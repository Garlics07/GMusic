package com.gmusic.app.data.lyrics

import com.gmusic.app.data.local.GMusicDao
import com.gmusic.app.data.local.LyricsCacheEntity
import com.gmusic.app.domain.model.Track
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class TrackLyrics(
    val trackId: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean
)

@Singleton
class LyricsRepository @Inject constructor(
    private val dao: GMusicDao,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    /**
     * Recupera il testo sincronizzato da LRCLIB con caching in Room (`lyrics_cache`).
     * Se il brano non ha testo, restituisce `null` (così la UI nasconde il pulsante Testo).
     */
    suspend fun getLyricsForTrack(track: Track): TrackLyrics? = withContext(Dispatchers.IO) {
        dao.getLyricsCache(track.id)?.let { cached ->
            if (!cached.hasLyrics) return@withContext null
            return@withContext parseCachedEntity(cached)
        }

        val cleanTitle = cleanTrackTitle(track.title)
        val cleanArtist = track.artistName.replace(Regex("(?i)\\s*-\\s*topic$"), "").trim()

        val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
        val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")

        // 1. Tentativo diretto /api/get
        val getUrl = buildString {
            append("https://lrclib.net/api/get?track_name=").append(encodedTitle)
            append("&artist_name=").append(encodedArtist)
            if (track.durationSeconds > 0L) {
                append("&duration=").append(track.durationSeconds)
            }
        }
        fetchFromLrclibGet(getUrl, track.id)?.let { entity ->
            dao.upsertLyricsCache(entity)
            return@withContext if (entity.hasLyrics) parseCachedEntity(entity) else null
        }

        // 2. Fallback /api/search?q=
        val searchQ = URLEncoder.encode("$cleanArtist $cleanTitle", "UTF-8")
        val searchUrl = "https://lrclib.net/api/search?q=$searchQ"
        val searchEntity = fetchFromLrclibSearch(searchUrl, track.id)
        if (searchEntity != null) {
            dao.upsertLyricsCache(searchEntity)
            return@withContext if (searchEntity.hasLyrics) parseCachedEntity(searchEntity) else null
        }

        val negativeEntity = LyricsCacheEntity(
            trackId = track.id,
            syncedLrc = null,
            plainLyrics = null,
            hasLyrics = false
        )
        dao.upsertLyricsCache(negativeEntity)
        null
    }

    private fun fetchFromLrclibGet(url: String, trackId: String): LyricsCacheEntity? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "GMusic/1.0 (https://github.com/gmusic)")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = json.parseToJsonElement(body).jsonObject
                val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (synced == null && plain == null) return null
                LyricsCacheEntity(
                    trackId = trackId,
                    syncedLrc = synced,
                    plainLyrics = plain,
                    hasLyrics = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromLrclibSearch(url: String, trackId: String): LyricsCacheEntity? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "GMusic/1.0 (https://github.com/gmusic)")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = json.parseToJsonElement(body).jsonArray
                if (arr.isEmpty()) return null

                // Preferisci il primo risultato con syncedLyrics
                val bestObj = arr.map { it.jsonObject }.firstOrNull {
                    !it["syncedLyrics"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
                } ?: arr.first().jsonObject

                val synced = bestObj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val plain = bestObj["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (synced == null && plain == null) return null
                LyricsCacheEntity(
                    trackId = trackId,
                    syncedLrc = synced,
                    plainLyrics = plain,
                    hasLyrics = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCachedEntity(entity: LyricsCacheEntity): TrackLyrics? {
        if (!entity.syncedLrc.isNullOrBlank()) {
            val syncedLines = parseSyncedLrc(entity.syncedLrc)
            if (syncedLines.isNotEmpty()) {
                return TrackLyrics(
                    trackId = entity.trackId,
                    lines = syncedLines,
                    isSynced = true
                )
            }
        }
        if (!entity.plainLyrics.isNullOrBlank()) {
            val plainLines = entity.plainLyrics.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { idx, line -> LyricLine(timeMs = idx * 4000L, text = line) }
            if (plainLines.isNotEmpty()) {
                return TrackLyrics(
                    trackId = entity.trackId,
                    lines = plainLines,
                    isSynced = false
                )
            }
        }
        return null
    }

    private fun parseSyncedLrc(lrc: String): List<LyricLine> {
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")
        val list = mutableListOf<LyricLine>()
        for (rawLine in lrc.lines()) {
            val match = regex.find(rawLine.trim()) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val millis = when (fracStr.length) {
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty()) {
                list.add(
                    LyricLine(
                        timeMs = min * 60_000L + sec * 1_000L + millis,
                        text = text
                    )
                )
            }
        }
        return list.sortedBy { it.timeMs }
    }

    private fun cleanTrackTitle(raw: String): String =
        raw.replace(Regex("(?i)\\((official|lyric|audio|video|remastered|hd|hq)[^)]*\\)"), "")
            .replace(Regex("(?i)\\[(official|lyric|audio|video|remastered|hd|hq)[^]]*]"), "")
            .trim()
}
