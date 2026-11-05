package com.gmusic.app.data.stream

import android.util.Log
import com.gmusic.app.data.innertube.InnerTubeClient
import com.gmusic.app.data.innertube.ResolvedAudioFormat
import com.gmusic.app.data.local.GMusicDao
import com.gmusic.app.data.local.SettingsDataStore
import com.gmusic.app.data.local.StreamUrlCacheEntity
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

interface StreamResolver {
    suspend fun resolveStreamUrl(videoId: String, forceRefresh: Boolean = false): String
    suspend fun invalidateCache(videoId: String)
    suspend fun preResolve(videoId: String)
}

@Singleton
class ChainedStreamResolver @Inject constructor(
    private val newPipeDownloader: NewPipeOkHttpDownloader,
    private val innerTubeClient: InnerTubeClient,
    private val dao: GMusicDao,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : StreamResolver {

    private val memoryCache = ConcurrentHashMap<String, StreamUrlCacheEntity>()
    private val instanceFailureCounts = ConcurrentHashMap<String, Int>()
    private val pipedRoundRobin = AtomicInteger(0)
    private val invidiousRoundRobin = AtomicInteger(0)

    override suspend fun resolveStreamUrl(videoId: String, forceRefresh: Boolean): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            if (!forceRefresh) {
                memoryCache[videoId]?.takeIf { it.expiresAtMillis > now }?.let { cached ->
                    Log.d(TAG, "Cache HIT (memory) per $videoId [${cached.resolverSource}, itag=${cached.itag}]")
                    return@withContext cached.streamUrl
                }
                dao.getValidStreamCache(videoId, now)?.let { roomCached ->
                    memoryCache[videoId] = roomCached
                    Log.d(TAG, "Cache HIT (Room) per $videoId [${roomCached.resolverSource}, itag=${roomCached.itag}]")
                    return@withContext roomCached.streamUrl
                }
            } else {
                invalidateCache(videoId)
            }

            // 1. Primario: NewPipeExtractor v0.26.5 (include PoToken + deobfuscation JS aggiornato)
            resolveWithNewPipe(videoId)?.let { resolved ->
                if (verifyStreamPlayable(resolved.url)) {
                    saveToCache(videoId, resolved)
                    Log.i(TAG, "Risolto con NewPipeExtractor: $videoId (itag=${resolved.itag}, mime=${resolved.mimeType})")
                    return@withContext resolved.url
                } else {
                    Log.w(TAG, "NewPipeExtractor URL ha restituito HTTP 403 per $videoId, passo al fallback")
                }
            }

            // 2. Secondario: InnerTube ANDROID_VR / IOS / WEB_REMIX
            innerTubeClient.resolveAudioStream(videoId)?.let { resolved ->
                if (verifyStreamPlayable(resolved.url)) {
                    saveToCache(videoId, resolved)
                    Log.i(TAG, "Risolto con ${resolved.source}: $videoId (itag=${resolved.itag}, mime=${resolved.mimeType})")
                    return@withContext resolved.url
                } else {
                    Log.w(TAG, "${resolved.source} ha restituito HTTP non valido per $videoId, passo al resolver successivo")
                }
            }

            // 3. Fallback: istanze Piped / Invidious configurabili in Settings con rotazione e health-check
            resolveWithPipedOrInvidious(videoId)?.let { resolved ->
                saveToCache(videoId, resolved)
                Log.i(TAG, "Risolto con ${resolved.source}: $videoId (itag=${resolved.itag})")
                return@withContext resolved.url
            }

            throw IOException("Impossibile risolvere lo stream audio per videoId=$videoId su tutta la catena")
        }

    override suspend fun invalidateCache(videoId: String) = withContext(Dispatchers.IO) {
        memoryCache.remove(videoId)
        dao.invalidateStreamCache(videoId)
    }

    override suspend fun preResolve(videoId: String) {
        try {
            resolveStreamUrl(videoId, forceRefresh = false)
        } catch (e: Exception) {
            Log.w(TAG, "Pre-risoluzione fallita per $videoId: ${e.message}")
        }
    }

    private suspend fun saveToCache(videoId: String, format: ResolvedAudioFormat) {
        val entity = StreamUrlCacheEntity(
            videoId = videoId,
            streamUrl = format.url,
            itag = format.itag,
            mimeType = format.mimeType,
            bitrate = format.bitrate,
            expiresAtMillis = format.expiresAtMillis,
            resolverSource = format.source
        )
        memoryCache[videoId] = entity
        dao.upsertStreamCache(entity)
    }

    private fun resolveWithNewPipe(videoId: String): ResolvedAudioFormat? {
        return try {
            newPipeDownloader.ensureInitialized()
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
            val audioStreams: List<AudioStream> = streamInfo.audioStreams.orEmpty()
            if (audioStreams.isEmpty()) return null

            val best = audioStreams
                .filter { it.content.isNotBlank() }
                .sortedWith(
                    compareByDescending<AudioStream> { stream ->
                        InnerTubeClient.formatPriority(stream.itag, stream.format?.mimeType ?: "audio/webm")
                    }.thenByDescending { it.averageBitrate }
                )
                .firstOrNull()
                ?: return null

            val streamUrl = best.content
            val expireSec = Regex("[?&]expire=(\\d+)").find(streamUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val expiresAtMillis = if (expireSec != null) {
                (expireSec * 1000L) - 60_000L
            } else {
                System.currentTimeMillis() + FIVE_HOURS_MILLIS
            }

            ResolvedAudioFormat(
                url = streamUrl,
                itag = best.itag,
                mimeType = best.format?.mimeType ?: "audio/webm",
                bitrate = best.averageBitrate,
                expiresAtMillis = expiresAtMillis,
                source = "NewPipeExtractor"
            )
        } catch (e: Exception) {
            Log.w(TAG, "NewPipeExtractor fallback per $videoId: ${e.message}")
            null
        }
    }

    private suspend fun resolveWithPipedOrInvidious(videoId: String): ResolvedAudioFormat? {
        val settings = settingsDataStore.settingsFlow.first()
        val pipedList = settings.pipedInstances.sortedBy { instanceFailureCounts[it] ?: 0 }
        if (pipedList.isNotEmpty()) {
            val startIdx = pipedRoundRobin.getAndIncrement()
            for (i in pipedList.indices) {
                val baseUrl = pipedList[(startIdx + i) % pipedList.size]
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/streams/$videoId")
                        .header("User-Agent", "GMusic/1.0")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            markInstanceFailure(baseUrl)
                            return@use
                        }
                        val body = resp.body?.string() ?: return@use
                        val root = json.parseToJsonElement(body).jsonObject
                        val audioStreams = root["audioStreams"]?.jsonArray ?: return@use
                        val best = audioStreams.mapNotNull { el ->
                            val obj = el.jsonObject
                            val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            if (!mime.startsWith("audio/")) return@mapNotNull null
                            val streamUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val itag = obj["itag"]?.jsonPrimitive?.intOrNull ?: -1
                            val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                            ResolvedAudioFormat(
                                url = streamUrl,
                                itag = itag,
                                mimeType = mime,
                                bitrate = bitrate,
                                expiresAtMillis = System.currentTimeMillis() + FIVE_HOURS_MILLIS,
                                source = "Piped($baseUrl)"
                            )
                        }.sortedWith(
                            compareByDescending<ResolvedAudioFormat> {
                                InnerTubeClient.formatPriority(it.itag, it.mimeType)
                            }.thenByDescending { it.bitrate }
                        ).firstOrNull()

                        if (best != null) {
                            instanceFailureCounts[baseUrl] = 0
                            return best
                        }
                    }
                } catch (_: Exception) {
                    markInstanceFailure(baseUrl)
                }
            }
        }

        val invidiousList = settings.invidiousInstances.sortedBy { instanceFailureCounts[it] ?: 0 }
        if (invidiousList.isNotEmpty()) {
            val startIdx = invidiousRoundRobin.getAndIncrement()
            for (i in invidiousList.indices) {
                val baseUrl = invidiousList[(startIdx + i) % invidiousList.size]
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/api/v1/videos/$videoId")
                        .header("User-Agent", "GMusic/1.0")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            markInstanceFailure(baseUrl)
                            return@use
                        }
                        val body = resp.body?.string() ?: return@use
                        val root = json.parseToJsonElement(body).jsonObject
                        val adaptive = root["adaptiveFormats"]?.jsonArray ?: return@use
                        val best = adaptive.mapNotNull { el ->
                            val obj = el.jsonObject
                            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            if (!type.startsWith("audio/")) return@mapNotNull null
                            val streamUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val itag = obj["itag"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
                            val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            ResolvedAudioFormat(
                                url = streamUrl,
                                itag = itag,
                                mimeType = type.substringBefore(";"),
                                bitrate = bitrate,
                                expiresAtMillis = System.currentTimeMillis() + FIVE_HOURS_MILLIS,
                                source = "Invidious($baseUrl)"
                            )
                        }.sortedWith(
                            compareByDescending<ResolvedAudioFormat> {
                                InnerTubeClient.formatPriority(it.itag, it.mimeType)
                            }.thenByDescending { it.bitrate }
                        ).firstOrNull()

                        if (best != null) {
                            instanceFailureCounts[baseUrl] = 0
                            return best
                        }
                    }
                } catch (_: Exception) {
                    markInstanceFailure(baseUrl)
                }
            }
        }
        return null
    }

    private fun verifyStreamPlayable(streamUrl: String): Boolean {
        return try {
            val ua = when {
                streamUrl.contains("c=IOS", ignoreCase = true) ->
                    "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                streamUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                    "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                streamUrl.contains("c=ANDROID", ignoreCase = true) ->
                    "com.google.android.youtube/19.45.38 (Linux; U; Android 14) gzip"
                else ->
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
            }
            // Testiamo sia l'inizio che un offset a 128 KB perché YouTube spesso blocca con 403 dopo i primi 64 KB
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", ua)
                .header("Range", "bytes=131072-135167")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                resp.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun markInstanceFailure(baseUrl: String) {
        instanceFailureCounts.compute(baseUrl) { _, old -> (old ?: 0) + 1 }
    }

    companion object {
        private const val TAG = "GMusicAudio"
        private const val FIVE_HOURS_MILLIS = 5L * 60L * 60L * 1000L
    }
}
