package com.gmusic.app.playback.service

import android.app.PendingIntent
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.gmusic.app.MainActivity
import com.gmusic.app.R
import com.gmusic.app.data.local.SettingsDataStore
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.data.stream.StreamResolver
import com.gmusic.app.domain.model.HomeItemType
import com.gmusic.app.domain.model.HomeShelfItem
import com.gmusic.app.domain.model.Track
import com.gmusic.app.playback.crossfade.CrossfadeController
import com.gmusic.app.playback.equalizer.GMusicEqualizerManager
import com.gmusic.app.playback.queue.TrackMediaItemMapper
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_BROWSABLE_HINT
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_GRID_ITEM_HINT_VALUE
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_GROUP_TITLE_HINT
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_PLAYABLE_HINT
import com.gmusic.app.playback.queue.TrackMediaItemMapper.CONTENT_STYLE_SUPPORTED
import com.gmusic.app.widget.GMusicGlanceWidget
import com.gmusic.app.widget.WidgetCommandActionCallback
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class GMusicMediaLibraryService : MediaLibraryService() {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var simpleCache: SimpleCache
    @Inject lateinit var streamResolver: StreamResolver
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var equalizerManager: GMusicEqualizerManager
    @Inject lateinit var playbackEventBus: PlaybackEventBus

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var crossfadeController: CrossfadeController? = null
    private var currentLikeWatchJob: Job? = null
    private var preCacheJob: Job? = null

    @Volatile private var currentCrossfadeSeconds: Int = 0
    @Volatile private var isFetchingAutoplay: Boolean = false
    @Volatile private var activeArtistPlaybackName: String? = null
    @Volatile private var activeArtistPlaybackId: String? = null
    @Volatile private var lastSearchResults: List<MediaItem> = emptyList()
    @Volatile private var lastBrowsedTracks: List<Track> = emptyList()

    private val retriedTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val artworkBytesCache = ConcurrentHashMap<String, ByteArray>()
    private val knownTracks = ConcurrentHashMap<String, Track>()
    private val containerTracksCache = ConcurrentHashMap<String, List<Track>>()
    private val artistNameHints = ConcurrentHashMap<String, String>()
    private val albumTitleHints = ConcurrentHashMap<String, String>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val widgetCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra(WidgetCommandActionCallback.EXTRA_WIDGET_COMMAND) ?: return
            when (cmd) {
                WidgetCommandActionCallback.CMD_TOGGLE -> {
                    if (player.isPlaying) player.pause() else player.play()
                }
                WidgetCommandActionCallback.CMD_NEXT -> {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
                WidgetCommandActionCallback.CMD_PREV -> {
                    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L)
                }
                WidgetCommandActionCallback.CMD_LIKE -> {
                    toggleCurrentTrackLikeFromSession()
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        player = buildExoPlayer()
        setupPlayerListeners()

        serviceScope.launch {
            restoreSavedPlaybackStateIfNeeded()
        }

        serviceScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                currentCrossfadeSeconds = settings.crossfadeSeconds
                equalizerManager.applySettings(settings)
            }
        }
        if (player.audioSessionId > 0) {
            equalizerManager.onAudioSessionIdChanged(player.audioSessionId)
        }
        crossfadeController = CrossfadeController(
            player = player,
            mainScope = serviceScope,
            crossfadeSecondsProvider = { currentCrossfadeSeconds }
        ).also { it.start() }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        ContextCompat.registerReceiver(
            this,
            widgetCommandReceiver,
            IntentFilter(WidgetCommandActionCallback.ACTION_WIDGET_MEDIA_COMMAND),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private suspend fun restoreSavedPlaybackStateIfNeeded() {
        if (player.mediaItemCount > 0) return
        val saved = withContext(Dispatchers.IO) {
            settingsDataStore.settingsFlow.first()
        }
        if (saved.lastQueueJson.isBlank()) return
        val restoredTracks = try {
            json.decodeFromString<List<Track>>(saved.lastQueueJson)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile decodificare la coda salvata: ${e.message}")
            emptyList()
        }
        if (restoredTracks.isNotEmpty() && player.mediaItemCount == 0) {
            restoredTracks.forEach { knownTracks[it.id] = it }
            val mediaItems = restoredTracks.map { track ->
                val art = withContext(Dispatchers.IO) { fetchOrGetCachedArtwork(track.id, track.thumbnailUrl) }
                TrackMediaItemMapper.toMediaItem(track, art)
            }
            val safeIndex = saved.lastQueueIndex.coerceIn(0, mediaItems.lastIndex)
            val safePos = saved.lastPositionMs.coerceAtLeast(0L)
            player.setMediaItems(mediaItems, safeIndex, safePos)
            player.playWhenReady = false
            player.prepare()
            Log.i(TAG, "Stato player ripristinato: ${restoredTracks.size} brani, indice=$safeIndex, pos=${safePos}ms")
        }
    }

    private fun persistCurrentPlaybackState() {
        val count = player.mediaItemCount
        if (count == 0) return
        val tracks = (0 until count).map { idx ->
            TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(idx))
        }
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            try {
                val encoded = json.encodeToString(tracks)
                settingsDataStore.savePlaybackState(encoded, currentIndex, currentPos)
            } catch (e: Exception) {
                Log.w(TAG, "Errore salvataggio stato player: ${e.message}")
            }
        }
    }


    private fun buildExoPlayer(): ExoPlayer {
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                )
            )

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(okHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory) { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == TrackMediaItemMapper.SCHEME_GMUSIC && uri.host == TrackMediaItemMapper.HOST_TRACK) {
                val videoId = uri.lastPathSegment
                    ?: throw IOException("VideoId mancante nell'URI $uri")
                val resolvedUrl = runBlocking(Dispatchers.IO) {
                    streamResolver.resolveStreamUrl(videoId, forceRefresh = false)
                }
                val ua = when {
                    resolvedUrl.contains("c=IOS", ignoreCase = true) ->
                        "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                    resolvedUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                        "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    resolvedUrl.contains("c=ANDROID", ignoreCase = true) ->
                        "com.google.android.youtube/19.45.38 (Linux; U; Android 14) gzip"
                    else ->
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
                }
                dataSpec.buildUpon()
                    .setUri(Uri.parse(resolvedUrl))
                    .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                    .setKey(videoId)
                    .build()
            } else {
                dataSpec
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerManager.onAudioSessionIdChanged(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncWidgetAndCustomLayout()
                persistCurrentPlaybackState()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                persistCurrentPlaybackState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) {
                    syncWidgetAndCustomLayout()
                    return
                }
                val currentTrack = TrackMediaItemMapper.fromMediaItem(mediaItem)
                knownTracks[currentTrack.id] = currentTrack
                retriedTrackIds.remove(currentTrack.id)
                Log.i(TAG, "In riproduzione: ${currentTrack.title} - ${currentTrack.artistName} (${currentTrack.id})")

                // Aggiorna il contesto artista attivo se presente o deducibile dalla coda
                val artistCtx = mediaItem.mediaMetadata.extras?.getString(TrackMediaItemMapper.EXTRA_ARTIST_CONTEXT_NAME)
                val artistCtxId = mediaItem.mediaMetadata.extras?.getString(TrackMediaItemMapper.EXTRA_ARTIST_CONTEXT_ID)
                if (!artistCtx.isNullOrBlank()) {
                    activeArtistPlaybackName = artistCtx
                    activeArtistPlaybackId = artistCtxId
                } else if (player.mediaItemCount >= 3) {
                    val dominantArtist = currentTrack.artistName
                    val matchCount = (0 until player.mediaItemCount.coerceAtMost(8))
                        .count { player.getMediaItemAt(it).mediaMetadata.artist?.toString()?.equals(dominantArtist, ignoreCase = true) == true }
                    if (matchCount >= 3) {
                        activeArtistPlaybackName = dominantArtist
                        activeArtistPlaybackId = currentTrack.artistId
                    }
                }

                persistCurrentPlaybackState()

                serviceScope.launch {
                    musicRepository.recordTrackPlayed(currentTrack)
                }

                observeCurrentTrackLike(currentTrack.id)

                // Pre-caching proattivo di 3 brani in totale (brano attuale + i 2 successivi)
                // per prevenire silenzi o interruzioni in galleria / assenza temporanea di segnale
                preCacheJob?.cancel()
                preCacheJob = serviceScope.launch(Dispatchers.IO) {
                    preCacheNextTracks()
                }

                val remainingInQueue = (player.mediaItemCount - 1 - player.currentMediaItemIndex).coerceAtLeast(0)
                if (remainingInQueue <= 3 && !isFetchingAutoplay) {
                    isFetchingAutoplay = true
                    val existingIds = (0 until player.mediaItemCount)
                        .map { player.getMediaItemAt(it).mediaId }
                        .toSet()
                    val artistCtxName = activeArtistPlaybackName
                    val artistCtxId = activeArtistPlaybackId
                    serviceScope.launch {
                        try {
                            val additions = if (!artistCtxName.isNullOrBlank()) {
                                musicRepository.fetchArtistAutoplayAddition(
                                    artistId = artistCtxId ?: "",
                                    artistName = artistCtxName,
                                    existingQueueIds = existingIds
                                )
                            } else {
                                musicRepository.fetchAutoplayRadioAddition(currentTrack, existingIds)
                            }
                            if (additions.isNotEmpty()) {
                                additions.forEach { knownTracks[it.id] = it }
                                val mediaItems = additions.map {
                                    TrackMediaItemMapper.toMediaItem(
                                        track = it,
                                        artistContextName = artistCtxName,
                                        artistContextId = artistCtxId
                                    )
                                }
                                player.addMediaItems(mediaItems)
                                persistCurrentPlaybackState()
                                Log.i(TAG, "Autoplay infinito: aggiunti ${mediaItems.size} brani alla coda (artista: $artistCtxName)")
                            }
                        } finally {
                            isFetchingAutoplay = false
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentItem = player.currentMediaItem ?: return
                val videoId = currentItem.mediaId
                val title = currentItem.mediaMetadata.title?.toString() ?: videoId
                Log.e(TAG, "Errore ExoPlayer per $title ($videoId): ${error.errorCodeName}", error)

                if (retriedTrackIds.add(videoId)) {
                    Log.w(TAG, "Tentativo di ri-risoluzione e retry automatico per $videoId")
                    val currentPos = player.currentPosition.coerceAtLeast(0L)
                    serviceScope.launch {
                        val refreshed = withContext(Dispatchers.IO) {
                            try {
                                streamResolver.resolveStreamUrl(videoId, forceRefresh = true)
                                true
                            } catch (e: Exception) {
                                Log.e(TAG, "Ri-risoluzione fallita per $videoId: ${e.message}")
                                false
                            }
                        }
                        if (refreshed) {
                            player.seekTo(player.currentMediaItemIndex, currentPos)
                            player.prepare()
                            player.play()
                        } else {
                            skipAfterFailure(title)
                        }
                    }
                } else {
                    retriedTrackIds.remove(videoId)
                    skipAfterFailure(title)
                }
            }
        })
    }

    private fun observeCurrentTrackLike(trackId: String) {
        currentLikeWatchJob?.cancel()
        currentLikeWatchJob = serviceScope.launch {
            musicRepository.observeTrackById(trackId).collectLatest { dbTrack ->
                val isLiked = dbTrack?.isLiked ?: false
                updateLikeCustomButton(isLiked)
                val currentItem = player.currentMediaItem
                val track = currentItem?.let { TrackMediaItemMapper.fromMediaItem(it).copy(isLiked = isLiked) }
                GMusicGlanceWidget.updateAllWidgets(
                    context = this@GMusicMediaLibraryService,
                    track = track,
                    isPlaying = player.isPlaying
                )
            }
        }
    }


    private fun syncWidgetAndCustomLayout() {
        val currentItem = player.currentMediaItem
        val baseTrack = currentItem?.let { TrackMediaItemMapper.fromMediaItem(it) }
        serviceScope.launch {
            val isLiked = if (baseTrack != null) {
                withContext(Dispatchers.IO) {
                    musicRepository.observeTrackById(baseTrack.id).first()?.isLiked ?: false
                }
            } else false
            updateLikeCustomButton(isLiked)
            GMusicGlanceWidget.updateAllWidgets(
                context = this@GMusicMediaLibraryService,
                track = baseTrack?.copy(isLiked = isLiked),
                isPlaying = player.isPlaying
            )
        }
    }

    private fun updateLikeCustomButton(isLiked: Boolean) {
        val likeButton = CommandButton.Builder(
            if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(getString(if (isLiked) R.string.player_unlike else R.string.player_like))
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
            .build()
        mediaLibrarySession?.setCustomLayout(ImmutableList.of(likeButton))
    }

    private fun toggleCurrentTrackLikeFromSession() {
        val currentItem = player.currentMediaItem ?: return
        val track = TrackMediaItemMapper.fromMediaItem(currentItem)
        serviceScope.launch {
            val newLiked = musicRepository.toggleLike(track)
            updateLikeCustomButton(newLiked)
            GMusicGlanceWidget.updateAllWidgets(
                context = this@GMusicMediaLibraryService,
                track = track.copy(isLiked = newLiked),
                isPlaying = player.isPlaying
            )
        }
    }

    private fun skipAfterFailure(failedTitle: String) {
        playbackEventBus.tryEmit(PlaybackUiEvent.StreamErrorSkipped(failedTitle))
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.stop()
        }
    }

    /**
     * Converte i brani in MediaItem istantaneamente senza bloccare il thread con download di copertine via rete.
     * Android Auto scaricherà l'URI in background. Usato per ricerche e risposte veloci.
     */
    private fun toMediaItemsFast(
        tracks: List<Track>,
        groupTitle: String? = null
    ): List<MediaItem> {
        val capped = tracks.take(MAX_PAGE_SIZE)
        capped.forEach { knownTracks[it.id] = it }
        if (capped.isNotEmpty()) {
            lastBrowsedTracks = capped
        }
        return capped.map { track ->
            val cachedBytes = artworkBytesCache[track.id]
            TrackMediaItemMapper.toMediaItem(track, cachedBytes, groupTitle)
        }
    }

    /**
     * Scarica e comprime in JPEG 200x200 le copertine per Android Auto.
     */
    private suspend fun toMediaItemsWithArtwork(
        tracks: List<Track>,
        groupTitle: String? = null,
        limit: Int = MAX_PAGE_SIZE,
        artistContextName: String? = null,
        artistContextId: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val capped = tracks.take(limit)
        capped.forEach { knownTracks[it.id] = it }
        if (capped.isNotEmpty()) {
            lastBrowsedTracks = capped
        }
        coroutineScope {
            capped.map { track ->
                async {
                    val bytes = fetchOrGetCachedArtwork(track.id, track.thumbnailUrl)
                    TrackMediaItemMapper.toMediaItem(
                        track = track,
                        artworkBytes = bytes,
                        groupTitle = groupTitle,
                        artistContextName = artistContextName,
                        artistContextId = artistContextId
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * Prepara l'elenco di un contenitore (playlist, album, mix, preferiti, download, artista)
     * iniziando con l'elemento "Casuale" seguito dai brani (toccare un brano avvia già la riproduzione).
     */
    private suspend fun buildContainerChildrenWithPlayAndShuffle(
        containerKey: String,
        tracks: List<Track>
    ): List<MediaItem> {
        if (tracks.isEmpty()) return emptyList()
        containerTracksCache[containerKey] = tracks
        lastBrowsedTracks = tracks
        tracks.forEach { knownTracks[it.id] = it }

        val shuffleItem = actionPlayableItem(
            mediaId = "$PREFIX_ACTION_SHUFFLE$containerKey",
            title = getString(R.string.action_shuffle_play),
            subtitle = null,
            iconRes = R.drawable.ic_aa_shuffle
        )
        val trackItems = toMediaItemsWithArtwork(tracks)
        return listOf(shuffleItem) + trackItems
    }

    private suspend fun resolveFullMediaItem(item: MediaItem): MediaItem {
        val metaTitle = item.mediaMetadata.title?.toString()
        val metaArtist = item.mediaMetadata.artist?.toString()
        val hasValidMetadata = !metaTitle.isNullOrBlank() &&
            metaTitle != "Brano sconosciuto" &&
            !metaArtist.isNullOrBlank() &&
            metaArtist != "Artista sconosciuto" &&
            metaArtist != "Artista"
        val track: Track = if (hasValidMetadata) {
            TrackMediaItemMapper.fromMediaItem(item).also { knownTracks[it.id] = it }
        } else {
            val cached = knownTracks[item.mediaId]?.takeIf {
                it.title != "Brano sconosciuto" &&
                    it.artistName != "Artista sconosciuto" &&
                    it.artistName != "Artista"
            }
            cached
                ?: withContext(Dispatchers.IO) { musicRepository.resolveTrackById(item.mediaId) }
                    ?.also { knownTracks[it.id] = it }
                ?: TrackMediaItemMapper.fromMediaItem(item)
        }
        val artworkBytes = artworkBytesCache[track.id]
            ?: withContext(Dispatchers.IO) { fetchOrGetCachedArtwork(track.id, track.thumbnailUrl) }
        return TrackMediaItemMapper.toMediaItem(track, artworkBytes)
    }

    private fun fetchOrGetCachedArtwork(
        cacheKey: String,
        rawUrl: String,
        circular: Boolean = false
    ): ByteArray? {
        val fullKey = if (circular) "${cacheKey}_circle" else cacheKey
        artworkBytesCache[fullKey]?.let { return it }
        val url = rawUrl.ifBlank { "https://i.ytimg.com/vi/$cacheKey/hqdefault.jpg" }
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val rawBytes = response.body?.bytes() ?: return null
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
                val targetSize = 220
                val scaled = Bitmap.createScaledBitmap(decoded, targetSize, targetSize, true)
                val finalBitmap = if (circular) cropCircularBitmap(scaled) else scaled
                val out = ByteArrayOutputStream()
                val format = if (circular) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                finalBitmap.compress(format, 82, out)
                val compressed = out.toByteArray()
                if (artworkBytesCache.size > 200) {
                    artworkBytesCache.clear()
                }
                artworkBytesCache[fullKey] = compressed
                compressed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cropCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }

    private fun generateMoodTileBytes(title: String, colorHex: Long): ByteArray {
        val cacheKey = "mood_tile_$title"
        artworkBytesCache[cacheKey]?.let { return it }
        val size = 200
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(colorHex.toInt())
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isFakeBoldText = true
        }
        canvas.drawText(title.take(14), 16f, 110f, textPaint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val bytes = out.toByteArray()
        artworkBytesCache[cacheKey] = bytes
        return bytes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ||
                intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
            val query = intent.getStringExtra(SearchManager.QUERY)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                ?: intent.getStringExtra("android.intent.extra.query")
                ?: ""
            if (query.isNotBlank()) {
                handleVoicePlayQuery(query.trim())
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleVoicePlayQuery(query: String) {
        serviceScope.launch {
            Log.i(TAG, "Avvio riproduzione vocale da Intent per query: $query")
            val tracks = withContext(Dispatchers.IO) {
                val songs = musicRepository.searchSongs(query)
                if (songs.isNotEmpty()) songs else {
                    val full = musicRepository.searchAllCategories(query)
                    if (full.artists.isNotEmpty()) {
                        val art = full.artists.first()
                        musicRepository.fetchArtistDetail(art.id, art.name).popularTracks.ifEmpty { full.songs }
                    } else full.songs
                }
            }
            if (tracks.isNotEmpty()) {
                val queue = toMediaItemsFast(tracks)
                player.setMediaItems(queue, 0, 0L)
                player.prepare()
                player.play()
            }
        }
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCurrentPlaybackState()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistCurrentPlaybackState()
        try {
            unregisterReceiver(widgetCommandReceiver)
        } catch (_: Exception) {
        }
        crossfadeController?.release()
        equalizerManager.releaseEffects()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                defaultResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                toggleCurrentTrackLikeFromSession()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            }
            val rootParams = LibraryParams.Builder()
                .setExtras(rootExtras)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId(NODE_ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setArtworkUri(resourceUri(R.drawable.ic_aa_logo))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(rootExtras)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val allItems: List<MediaItem> = when {
                // 1. Root = 3 tab stile Spotify ("Home", "Cerca", "La tua libreria")
                parentId == NODE_ROOT -> listOf(
                    rootTabFolder(
                        id = NODE_TAB_HOME,
                        title = getString(R.string.auto_root_home),
                        iconRes = R.drawable.ic_aa_home,
                        browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                        playableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                    ),
                    rootTabFolder(
                        id = NODE_TAB_SEARCH,
                        title = getString(R.string.auto_root_search),
                        iconRes = R.drawable.ic_aa_search,
                        browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                        playableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                    ),
                    rootTabFolder(
                        id = NODE_TAB_LIBRARY,
                        title = getString(R.string.auto_root_library),
                        iconRes = R.drawable.ic_aa_library,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        playableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                    )
                )

                // 2. Tab HOME: sezioni raggruppate tramite CONTENT_STYLE_GROUP_TITLE_HINT
                // nello stesso ordine degli scaffali del telefono ("Riproduzioni recenti", "Fatto per te", "Consigliati per te", "I tuoi artisti preferiti", "Nuove uscite per te", mood)
                parentId == NODE_TAB_HOME -> buildAndroidAutoHomeTabItems()

                // 3. Tab LIBRERIA: "Canzoni preferite" (fissata per prima con icona a cuore), Playlist, Album, Artisti, Download
                parentId == NODE_TAB_LIBRARY -> listOf(
                    browsableContainerItem(
                        id = NODE_LIB_LIKED,
                        title = getString(R.string.auto_root_liked),
                        subtitle = null,
                        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        childrenPlayableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        iconRes = R.drawable.ic_aa_heart
                    ),
                    browsableContainerItem(
                        id = NODE_LIB_PLAYLISTS,
                        title = getString(R.string.auto_root_playlists),
                        subtitle = null,
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        childrenBrowsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                        iconRes = R.drawable.ic_aa_library
                    ),
                    browsableContainerItem(
                        id = NODE_LIB_ALBUMS,
                        title = getString(R.string.auto_lib_albums),
                        subtitle = null,
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        childrenBrowsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                        iconRes = R.drawable.ic_aa_album
                    ),
                    browsableContainerItem(
                        id = NODE_LIB_ARTISTS,
                        title = getString(R.string.auto_lib_artists),
                        subtitle = null,
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        childrenPlayableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                        iconRes = R.drawable.ic_aa_artist
                    ),
                    browsableContainerItem(
                        id = NODE_LIB_DOWNLOADS,
                        title = getString(R.string.auto_lib_downloads),
                        subtitle = null,
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                        browsableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        childrenPlayableHint = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                        iconRes = R.drawable.ic_aa_download
                    )
                )

                // 4. Tab CERCA: categorie rapide raggruppate (Generi/Mood, Nuove uscite, Classifiche Italia)
                parentId == NODE_TAB_SEARCH -> buildAndroidAutoSearchTabItems()

                // Sotto-nodi di La tua libreria
                parentId == NODE_LIB_LIKED -> {
                    val liked = withContext(Dispatchers.IO) {
                        musicRepository.observeLikedTracks().first().take(MAX_PAGE_SIZE)
                    }
                    buildContainerChildrenWithPlayAndShuffle(NODE_LIB_LIKED, liked)
                }

                parentId == NODE_LIB_DOWNLOADS -> {
                    val downloaded = withContext(Dispatchers.IO) {
                        musicRepository.observeDownloadedTracks().first().take(MAX_PAGE_SIZE)
                    }
                    buildContainerChildrenWithPlayAndShuffle(NODE_LIB_DOWNLOADS, downloaded)
                }

                parentId == NODE_LIB_PLAYLISTS -> {
                    val isLoggedIn = musicRepository.isLoggedIn.value
                    val remotePlaylists = withContext(Dispatchers.IO) {
                        if (musicRepository.remotePlaylists.value.isEmpty() && isLoggedIn) {
                            musicRepository.syncRemoteLibraryIfLoggedIn()
                        }
                        musicRepository.remotePlaylists.value
                    }
                    if (isLoggedIn) {
                        coroutineScope {
                            remotePlaylists.map { pl ->
                                async(Dispatchers.IO) {
                                    albumTitleHints[pl.id] = pl.title
                                    val coverUrl = pl.thumbnailUrls.firstOrNull().orEmpty()
                                    val art = if (coverUrl.isNotBlank()) fetchOrGetCachedArtwork(pl.id, coverUrl) else null
                                    browsableContainerItem(
                                        id = "$PREFIX_REMOTE_PLAYLIST${pl.id}",
                                        title = pl.title,
                                        subtitle = null,
                                        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                        browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                                        artworkUri = coverUrl,
                                        artworkBytes = art,
                                        groupTitle = getString(R.string.library_yt_playlists)
                                    )
                                }
                            }.awaitAll()
                        }
                    } else {
                        val localPlaylists = withContext(Dispatchers.IO) {
                            musicRepository.observeLocalPlaylists().first()
                        }
                        coroutineScope {
                            localPlaylists.map { pl ->
                                async(Dispatchers.IO) {
                                    val coverUrl = pl.thumbnailUrls.firstOrNull().orEmpty()
                                    val art = if (coverUrl.isNotBlank()) fetchOrGetCachedArtwork(pl.id, coverUrl) else null
                                    browsableContainerItem(
                                        id = "$PREFIX_PLAYLIST${pl.id}",
                                        title = pl.title,
                                        subtitle = null,
                                        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                        browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                                        artworkUri = coverUrl,
                                        artworkBytes = art,
                                        groupTitle = getString(R.string.library_playlists)
                                    )
                                }
                            }.awaitAll()
                        }
                    }
                }

                parentId == NODE_LIB_ALBUMS -> {
                    val savedAlbums = withContext(Dispatchers.IO) {
                        musicRepository.observeSavedAlbums().first()
                    }
                    val albums = if (savedAlbums.isNotEmpty()) {
                        savedAlbums
                    } else {
                        withContext(Dispatchers.IO) {
                            musicRepository.loadExploreItalyData().second.take(12)
                        }
                    }
                    coroutineScope {
                        albums.map { alb ->
                            async(Dispatchers.IO) {
                                albumTitleHints[alb.id] = alb.title
                                val art = fetchOrGetCachedArtwork(alb.id, alb.hdArtworkUrl)
                                browsableContainerItem(
                                    id = "$PREFIX_ALBUM${alb.id}",
                                    title = alb.title,
                                    subtitle = null,
                                    mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                                    artworkUri = alb.hdArtworkUrl,
                                    artworkBytes = art
                                )
                            }
                        }.awaitAll()
                    }
                }

                parentId == NODE_LIB_ARTISTS -> {
                    val savedArtists = withContext(Dispatchers.IO) {
                        musicRepository.observeSavedArtists().first()
                    }
                    coroutineScope {
                        savedArtists.take(24).map { artist ->
                            async(Dispatchers.IO) {
                                artistNameHints[artist.id] = artist.name
                                val art = fetchOrGetCachedArtwork(artist.id, artist.thumbnailUrl, circular = true)
                                playableArtistItem(
                                    id = "$PREFIX_ARTIST${artist.id}",
                                    title = artist.name,
                                    artworkUri = artist.thumbnailUrl,
                                    artworkBytes = art
                                )
                            }
                        }.awaitAll()
                    }
                }

                // Apertura di un contenitore specifico (con "Riproduci" e "Casuale" in testa)
                parentId.startsWith(PREFIX_PLAYLIST) -> {
                    val plId = parentId.removePrefix(PREFIX_PLAYLIST)
                    val tracks = withContext(Dispatchers.IO) {
                        musicRepository.observePlaylistDetail(plId).first().second
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                parentId.startsWith(PREFIX_REMOTE_PLAYLIST) -> {
                    val plId = parentId.removePrefix(PREFIX_REMOTE_PLAYLIST)
                    val titleHint = albumTitleHints[plId] ?: "Playlist"
                    val tracks = withContext(Dispatchers.IO) {
                        musicRepository.fetchAlbumOrPlaylistDetail(plId, titleHint).tracks
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                parentId.startsWith(PREFIX_ALBUM) -> {
                    val albId = parentId.removePrefix(PREFIX_ALBUM)
                    val titleHint = albumTitleHints[albId] ?: "Album"
                    val tracks = withContext(Dispatchers.IO) {
                        musicRepository.fetchAlbumOrPlaylistDetail(albId, titleHint).tracks
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                parentId.startsWith(PREFIX_ARTIST) -> {
                    val artId = parentId.removePrefix(PREFIX_ARTIST)
                    val nameHint = artistNameHints[artId] ?: artId
                    val tracks = withContext(Dispatchers.IO) {
                        musicRepository.fetchArtistDiscography(artId, nameHint).ifEmpty {
                            musicRepository.fetchArtistDetail(artId, nameHint).popularTracks
                        }
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                parentId.startsWith(PREFIX_MIX) -> {
                    val cached = containerTracksCache[parentId].orEmpty()
                    val tracks = if (cached.isNotEmpty()) {
                        cached
                    } else {
                        withContext(Dispatchers.IO) {
                            musicRepository.searchSongs("Mix Italia")
                        }
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                parentId.startsWith(PREFIX_MOOD) -> {
                    val query = parentId.removePrefix(PREFIX_MOOD)
                    val tracks = withContext(Dispatchers.IO) {
                        musicRepository.searchSongs(query)
                    }
                    buildContainerChildrenWithPlayAndShuffle(parentId, tracks)
                }

                else -> emptyList()
            }

            val fromIndex = (page * safePageSize).coerceAtMost(allItems.size)
            val toIndex = (fromIndex + safePageSize).coerceAtMost(allItems.size)
            val paged = if (pageSize <= 0 || pageSize == Int.MAX_VALUE) {
                allItems.take(MAX_PAGE_SIZE)
            } else {
                allItems.subList(fromIndex, toIndex)
            }
            LibraryResult.ofItemList(paged, params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val effectiveQuery = query.ifBlank {
                params?.extras?.getString(SearchManager.QUERY)
                    ?: params?.extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
                    ?: params?.extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                    ?: params?.extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                    ?: params?.extras?.getString("android.intent.extra.query")
                    ?: ""
            }.trim()

            Log.i(TAG, "Android Auto onSearch: query='$query', effective='$effectiveQuery', extras=${params?.extras}")
            val groupedItems = if (effectiveQuery.isNotBlank()) {
                executeGroupedAutoSearch(effectiveQuery)
            } else {
                emptyList()
            }
            lastSearchResults = groupedItems
            session.notifySearchResultChanged(browser, query, groupedItems.size, params)

            val isVoicePlay = params?.extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS) != null ||
                params?.extras?.getBoolean("android.intent.extra.play", false) == true

            if (isVoicePlay && groupedItems.isNotEmpty()) {
                val playableSongs = groupedItems.filter { it.mediaMetadata.isPlayable == true }
                if (playableSongs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        player.setMediaItems(playableSongs, 0, 0L)
                        player.prepare()
                        player.play()
                    }
                }
            }
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val results = if (lastSearchResults.isNotEmpty()) {
                lastSearchResults
            } else {
                val effectiveQuery = query.ifBlank {
                    params?.extras?.getString(SearchManager.QUERY)
                        ?: params?.extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
                        ?: params?.extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                        ?: ""
                }.trim()
                if (effectiveQuery.isNotBlank()) {
                    executeGroupedAutoSearch(effectiveQuery).also { lastSearchResults = it }
                } else {
                    emptyList()
                }
            }
            val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val fromIndex = (page * safePageSize).coerceAtMost(results.size)
            val toIndex = (fromIndex + safePageSize).coerceAtMost(results.size)
            val paged = if (pageSize <= 0 || pageSize == Int.MAX_VALUE) {
                results.take(MAX_PAGE_SIZE)
            } else {
                results.subList(fromIndex, toIndex)
            }
            LibraryResult.ofItemList(paged, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            if (mediaId.startsWith(PREFIX_ARTIST)) {
                val artId = mediaId.removePrefix(PREFIX_ARTIST)
                val name = artistNameHints[artId] ?: artId
                val item = playableArtistItem(mediaId, name)
                return@future LibraryResult.ofItem(item, null)
            }
            val dummy = MediaItem.Builder().setMediaId(mediaId).build()
            val resolved = resolveFullMediaItem(dummy)
            LibraryResult.ofItem(resolved, null)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val firstItem = mediaItems.firstOrNull()

            // Controllo se è una richiesta di ricerca vocale da Google Assistant o Android Auto
            val voiceQuery = firstItem?.requestMetadata?.searchQuery?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(SearchManager.QUERY)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString("android.intent.extra.query")?.takeIf { it.isNotBlank() }

            if (voiceQuery != null) {
                Log.i(TAG, "Ricevuta richiesta di riproduzione vocale su onSetMediaItems: '$voiceQuery'")
                val searchResults = withContext(Dispatchers.IO) {
                    val directSongs = musicRepository.searchSongs(voiceQuery)
                    if (directSongs.isNotEmpty()) {
                        directSongs
                    } else {
                        val full = musicRepository.searchAllCategories(voiceQuery)
                        if (full.artists.isNotEmpty()) {
                            val art = full.artists.first()
                            val detail = musicRepository.fetchArtistDetail(art.id, art.name)
                            detail.popularTracks.ifEmpty { full.songs }
                        } else {
                            full.songs
                        }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    val fullQueue = toMediaItemsFast(searchResults)
                    return@future MediaSession.MediaItemsWithStartPosition(fullQueue, 0, 0L)
                }
            }

            if (mediaItems.size == 1) {
                val clickedId = mediaItems[0].mediaId

                // Gestione avvio diretto Discografia Artista in modalità casuale da Android Auto
                if (clickedId.startsWith(PREFIX_ARTIST)) {
                    val artId = clickedId.removePrefix(PREFIX_ARTIST)
                    val nameHint = artistNameHints[artId] ?: artId
                    val discography = withContext(Dispatchers.IO) {
                        val tracks = musicRepository.fetchArtistDiscography(artId, nameHint)
                        if (tracks.isNotEmpty()) tracks else {
                            musicRepository.fetchArtistDetail(artId, nameHint).popularTracks
                        }
                    }
                    if (discography.isNotEmpty()) {
                        activeArtistPlaybackName = nameHint
                        activeArtistPlaybackId = artId
                        player.shuffleModeEnabled = true
                        val shuffled = discography.shuffled()
                        val fullQueue = toMediaItemsWithArtwork(
                            tracks = shuffled,
                            limit = 100,
                            artistContextName = nameHint,
                            artistContextId = artId
                        )
                        return@future MediaSession.MediaItemsWithStartPosition(fullQueue, 0, 0L)
                    }
                }

                // Gestione pulsanti "Riproduci" e "Casuale" all'inizio di ogni contenitore Android Auto
                if (clickedId.startsWith(PREFIX_ACTION_PLAY_ALL)) {
                    val containerKey = clickedId.removePrefix(PREFIX_ACTION_PLAY_ALL)
                    val isArtist = containerKey.startsWith(PREFIX_ARTIST)
                    val artId = if (isArtist) containerKey.removePrefix(PREFIX_ARTIST) else null
                    val nameHint = artId?.let { artistNameHints[it] ?: it }
                    val tracks = containerTracksCache[containerKey] ?: lastBrowsedTracks
                    if (tracks.isNotEmpty()) {
                        if (isArtist && nameHint != null) {
                            activeArtistPlaybackName = nameHint
                            activeArtistPlaybackId = artId
                        }
                        player.shuffleModeEnabled = false
                        val fullQueue = toMediaItemsWithArtwork(
                            tracks = tracks,
                            limit = 100,
                            artistContextName = nameHint,
                            artistContextId = artId
                        )
                        return@future MediaSession.MediaItemsWithStartPosition(fullQueue, 0, 0L)
                    }
                }
                if (clickedId.startsWith(PREFIX_ACTION_SHUFFLE)) {
                    val containerKey = clickedId.removePrefix(PREFIX_ACTION_SHUFFLE)
                    val isArtist = containerKey.startsWith(PREFIX_ARTIST)
                    val artId = if (isArtist) containerKey.removePrefix(PREFIX_ARTIST) else null
                    val nameHint = artId?.let { artistNameHints[it] ?: it }
                    val tracks = (containerTracksCache[containerKey] ?: lastBrowsedTracks).shuffled()
                    if (tracks.isNotEmpty()) {
                        if (isArtist && nameHint != null) {
                            activeArtistPlaybackName = nameHint
                            activeArtistPlaybackId = artId
                        }
                        player.shuffleModeEnabled = true
                        val fullQueue = toMediaItemsWithArtwork(
                            tracks = tracks,
                            limit = 100,
                            artistContextName = nameHint,
                            artistContextId = artId
                        )
                        return@future MediaSession.MediaItemsWithStartPosition(fullQueue, 0, 0L)
                    }
                }

                val contextTracks = lastBrowsedTracks
                val contextIndex = contextTracks.indexOfFirst { it.id == clickedId }
                if (contextIndex >= 0 && contextTracks.size > 1) {
                    val fullQueue = toMediaItemsWithArtwork(contextTracks)
                    val safePos = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs.coerceAtLeast(0L)
                    return@future MediaSession.MediaItemsWithStartPosition(fullQueue, contextIndex, safePos)
                }
            }
            val firstArtistContext = mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString(TrackMediaItemMapper.EXTRA_ARTIST_CONTEXT_NAME)
            val firstArtistIdContext = mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString(TrackMediaItemMapper.EXTRA_ARTIST_CONTEXT_ID)
            if (!firstArtistContext.isNullOrBlank()) {
                activeArtistPlaybackName = firstArtistContext
                activeArtistPlaybackId = firstArtistIdContext
            } else if (mediaItems.size >= 3) {
                val firstArtist = mediaItems.firstOrNull()?.mediaMetadata?.artist?.toString()
                if (!firstArtist.isNullOrBlank()) {
                    val matchCount = mediaItems.take(8).count {
                        it.mediaMetadata.artist?.toString()?.equals(firstArtist, ignoreCase = true) == true
                    }
                    if (matchCount >= 3) {
                        activeArtistPlaybackName = firstArtist
                        activeArtistPlaybackId = null
                    } else {
                        activeArtistPlaybackName = null
                        activeArtistPlaybackId = null
                    }
                }
            } else {
                activeArtistPlaybackName = null
                activeArtistPlaybackId = null
            }

            val resolvedItems = coroutineScope {
                mediaItems.map { item ->
                    async { resolveFullMediaItem(item) }
                }.awaitAll()
            }
            val safeIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (resolvedItems.size - 1).coerceAtLeast(0))
            val safePos = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs.coerceAtLeast(0L)
            MediaSession.MediaItemsWithStartPosition(resolvedItems, safeIndex, safePos)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            val firstItem = mediaItems.firstOrNull()
            val voiceQuery = firstItem?.requestMetadata?.searchQuery?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(SearchManager.QUERY)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.takeIf { it.isNotBlank() }
                ?: firstItem?.requestMetadata?.extras?.getString("android.intent.extra.query")?.takeIf { it.isNotBlank() }

            if (voiceQuery != null) {
                val searchResults = withContext(Dispatchers.IO) {
                    musicRepository.searchSongs(voiceQuery)
                }
                if (searchResults.isNotEmpty()) {
                    return@future toMediaItemsFast(searchResults).toMutableList()
                }
            }

            coroutineScope {
                mediaItems.map { item ->
                    async { resolveFullMediaItem(item) }
                }.awaitAll().toMutableList()
            }
        }
    }


    /**
     * Costruisce gli elementi del tab Home di Android Auto raggruppati con CONTENT_STYLE_GROUP_TITLE_HINT
     * nello stesso ordine degli scaffali del telefono ("Riproduzioni recenti", "Fatto per te", "Consigliati per te",
     * "I tuoi artisti preferiti", "Nuove uscite per te", e scaffali tematici).
     */
    private suspend fun buildAndroidAutoHomeTabItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        var recentTracks = musicRepository.observeRecentHistory(10).first()
        if (recentTracks.isEmpty() && musicRepository.isLoggedIn.value) {
            musicRepository.syncRemoteLibraryIfLoggedIn()
            recentTracks = musicRepository.observeRecentHistory(10).first()
        }
        val activePlaylists = musicRepository.observeActivePlaylists().first()
        val savedArtists = musicRepository.observeSavedArtists().first()

        // 1. Griglia 3x2 rapida: Slot 1 "Canzoni preferite", Slot 2-6 mix randomico (Playlist, Artisti, Recenti)
        val quickGridItems = mutableListOf<MediaItem>()
        val likedItem = browsableContainerItem(
            id = NODE_LIB_LIKED,
            title = getString(R.string.auto_root_liked),
            subtitle = getString(R.string.library_liked_songs),
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
            browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
            iconRes = R.drawable.ic_aa_heart
        )
        quickGridItems.add(likedItem)

        val candidates = mutableListOf<suspend () -> MediaItem>()
        activePlaylists.take(6).forEach { pl ->
            candidates.add {
                val art = fetchOrGetCachedArtwork(pl.id, pl.thumbnailUrls.firstOrNull().orEmpty())
                browsableContainerItem(
                    id = if (pl.id.startsWith("local_pl_")) "$PREFIX_PLAYLIST${pl.id}" else "$PREFIX_REMOTE_PLAYLIST${pl.id}",
                    title = pl.title,
                    subtitle = "Playlist • ${pl.author}",
                    mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkBytes = art
                )
            }
        }
        savedArtists.take(6).forEach { artSummary ->
            candidates.add {
                artistNameHints[artSummary.id] = artSummary.name
                val art = fetchOrGetCachedArtwork(artSummary.id, artSummary.thumbnailUrl)
                playableArtistItem(
                    id = "$PREFIX_ARTIST${artSummary.id}",
                    title = artSummary.name,
                    artworkUri = artSummary.thumbnailUrl,
                    artworkBytes = art
                )
            }
        }
        recentTracks.take(6).forEach { track ->
            candidates.add {
                knownTracks[track.id] = track
                val art = fetchOrGetCachedArtwork(track.id, track.thumbnailUrl)
                TrackMediaItemMapper.toMediaItem(track, art, null)
            }
        }

        val shuffledCandidates = candidates.shuffled(java.util.Random(System.currentTimeMillis() / 60000L))
        for (cand in shuffledCandidates) {
            if (quickGridItems.size >= 6) break
            quickGridItems.add(cand())
        }
        result.addAll(quickGridItems)

        val sections = try {
            musicRepository.buildHomeFeedSections(page = 0)
        } catch (_: Exception) {
            emptyList()
        }

        // 2. Scaffali Home:
        // - "I tuoi mix" (6 mix)
        // - "Riproduzioni recenti" (ultimi 5 brani su Android Auto)
        // - "Consigliati per te"
        // - "I tuoi artisti preferiti"
        // - "Le tue playlist"
        // - "Nuove uscite"
        for (section in sections) {
            val groupTitle = section.title
            if (section.id == "shelf_recent_carousel") {
                if (recentTracks.isNotEmpty()) {
                    result.addAll(toMediaItemsWithArtwork(recentTracks.take(5), groupTitle = groupTitle))
                }
                continue
            }

            val shelfItems = section.items.take(8)
            if (shelfItems.isNotEmpty()) {
                val mapped = coroutineScope {
                    shelfItems.map { shelfItem ->
                        async {
                            mapShelfItemToAutoMediaItem(shelfItem, groupTitle)
                        }
                    }.awaitAll()
                }
                result.addAll(mapped)
            } else if (section.tracks.isNotEmpty()) {
                result.addAll(toMediaItemsWithArtwork(section.tracks.take(8), groupTitle = groupTitle))
            }
        }

        if (result.isEmpty()) {
            val fallbackTracks = musicRepository.searchSongs("Top 50 Italia").take(15)
            result.addAll(toMediaItemsWithArtwork(fallbackTracks, groupTitle = getString(R.string.home_recommended)))
        }
        result.take(MAX_PAGE_SIZE)
    }

    private suspend fun mapShelfItemToAutoMediaItem(
        item: HomeShelfItem,
        groupTitle: String
    ): MediaItem {
        return when (item.type) {
            HomeItemType.SONG -> {
                val track = item.track ?: Track(
                    id = item.id,
                    title = item.title,
                    artistName = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl
                )
                knownTracks[track.id] = track
                val art = fetchOrGetCachedArtwork(track.id, track.thumbnailUrl)
                TrackMediaItemMapper.toMediaItem(track, art, groupTitle)
            }
            HomeItemType.MIX -> {
                val mixKey = "$PREFIX_MIX${item.id}"
                if (item.tracks.isNotEmpty()) {
                    containerTracksCache[mixKey] = item.tracks
                    item.tracks.forEach { knownTracks[it.id] = it }
                }
                val art = fetchOrGetCachedArtwork(item.id, item.hdArtworkUrl)
                browsableContainerItem(
                    id = mixKey,
                    title = item.title,
                    subtitle = item.subtitle,
                    mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkUri = item.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = groupTitle
                )
            }
            HomeItemType.ALBUM -> {
                albumTitleHints[item.id] = item.title
                val art = fetchOrGetCachedArtwork(item.id, item.hdArtworkUrl)
                browsableContainerItem(
                    id = "$PREFIX_ALBUM${item.id}",
                    title = item.title,
                    subtitle = item.subtitle,
                    mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkUri = item.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = groupTitle
                )
            }
            HomeItemType.PLAYLIST -> {
                val isLocal = item.id.startsWith("local_pl_")
                val nodeId = if (isLocal) "$PREFIX_PLAYLIST${item.id}" else "$PREFIX_REMOTE_PLAYLIST${item.id}"
                albumTitleHints[item.id] = item.title
                val art = fetchOrGetCachedArtwork(item.id, item.hdArtworkUrl)
                browsableContainerItem(
                    id = nodeId,
                    title = item.title,
                    subtitle = item.subtitle,
                    mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkUri = item.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = groupTitle
                )
            }
            HomeItemType.ARTIST -> {
                artistNameHints[item.id] = item.title
                val art = fetchOrGetCachedArtwork(item.id, item.hdArtworkUrl, circular = true)
                playableArtistItem(
                    id = "$PREFIX_ARTIST${item.id}",
                    title = item.title,
                    artworkUri = item.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = groupTitle
                )
            }
        }
    }


    private suspend fun buildAndroidAutoSearchTabItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val (charts, _, moods) = musicRepository.loadExploreItalyData()
        val items = mutableListOf<MediaItem>()

        // 6 generi su Android Auto
        val moodGroup = getString(R.string.explore_moods_genres)
        moods.take(6).forEach { mood ->
            val tileBytes = generateMoodTileBytes(mood.title, mood.colorHex)
            items.add(
                browsableContainerItem(
                    id = "$PREFIX_MOOD${mood.query}",
                    title = mood.title,
                    subtitle = moodGroup,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkBytes = tileBytes,
                    groupTitle = moodGroup
                )
            )
        }

        // Tendenze (recuperate da YouTube Music)
        val trendingGroup = getString(R.string.search_trending)
        items.addAll(toMediaItemsWithArtwork(charts.take(20), groupTitle = trendingGroup))
        items.take(MAX_PAGE_SIZE)
    }

    /**
     * Esegue la ricerca su tutte le categorie e raggruppa i risultati sotto le intestazioni
     * "Brani", "Artisti", "Album", "Playlist" tramite CONTENT_STYLE_GROUP_TITLE_HINT.
     */
    private suspend fun executeGroupedAutoSearch(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val full = musicRepository.searchAllCategories(query, "all")
        val results = mutableListOf<MediaItem>()

        if (full.songs.isNotEmpty()) {
            val songsHeader = getString(R.string.filter_songs)
            results.addAll(toMediaItemsFast(full.songs.take(15), groupTitle = songsHeader))
        }
        if (full.artists.isNotEmpty()) {
            val artistsHeader = getString(R.string.filter_artists)
            val artistItems = full.artists.take(8).map { artist ->
                artistNameHints[artist.id] = artist.name
                val art = artworkBytesCache[artist.id]
                playableArtistItem(
                    id = "$PREFIX_ARTIST${artist.id}",
                    title = artist.name,
                    artworkUri = artist.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = artistsHeader
                )
            }
            results.addAll(artistItems)
        }
        if (full.albums.isNotEmpty()) {
            val albumsHeader = getString(R.string.filter_albums)
            val albumItems = full.albums.take(8).map { album ->
                albumTitleHints[album.id] = album.title
                val art = artworkBytesCache[album.id]
                browsableContainerItem(
                    id = "$PREFIX_ALBUM${album.id}",
                    title = album.title,
                    subtitle = album.artistName,
                    mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkUri = album.hdArtworkUrl,
                    artworkBytes = art,
                    groupTitle = albumsHeader
                )
            }
            results.addAll(albumItems)
        }
        if (full.playlists.isNotEmpty()) {
            val playlistsHeader = getString(R.string.filter_playlists)
            val plItems = full.playlists.take(8).map { pl ->
                albumTitleHints[pl.id] = pl.title
                val cover = pl.thumbnailUrls.firstOrNull().orEmpty()
                val art = artworkBytesCache[pl.id]
                browsableContainerItem(
                    id = "$PREFIX_REMOTE_PLAYLIST${pl.id}",
                    title = pl.title,
                    subtitle = pl.author ?: "Playlist",
                    mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    browsableHint = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    artworkUri = cover,
                    artworkBytes = art,
                    groupTitle = playlistsHeader
                )
            }
            results.addAll(plItems)
        }
        results.take(MAX_PAGE_SIZE)
    }

    private fun rootTabFolder(
        id: String,
        title: String,
        @DrawableRes iconRes: Int,
        browsableHint: Int,
        playableHint: Int
    ): MediaItem {
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setArtworkUri(resourceUri(iconRes))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun browsableContainerItem(
        id: String,
        title: String,
        subtitle: String? = null,
        mediaType: Int,
        browsableHint: Int = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
        childrenBrowsableHint: Int = CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
        childrenPlayableHint: Int = CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
        artworkUri: String? = null,
        artworkBytes: ByteArray? = null,
        groupTitle: String? = null,
        @DrawableRes iconRes: Int? = null
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_BROWSABLE_HINT, childrenBrowsableHint)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, childrenPlayableHint)
            if (!groupTitle.isNullOrBlank()) {
                putString(CONTENT_STYLE_GROUP_TITLE_HINT, groupTitle)
            }
        }
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .setExtras(extras)

        if (!artworkUri.isNullOrBlank()) {
            metaBuilder.setArtworkUri(Uri.parse(artworkUri))
        } else if (iconRes != null) {
            metaBuilder.setArtworkUri(resourceUri(iconRes))
        }
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metaBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun actionPlayableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        @DrawableRes iconRes: Int
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        }
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtworkUri(resourceUri(iconRes))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
        if (!subtitle.isNullOrBlank()) {
            metaBuilder.setSubtitle(subtitle).setArtist(subtitle)
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.parse("${TrackMediaItemMapper.SCHEME_GMUSIC}://action/$mediaId"))
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun playableArtistItem(
        id: String,
        title: String,
        artworkUri: String? = null,
        artworkBytes: ByteArray? = null,
        groupTitle: String? = null
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            if (!groupTitle.isNullOrBlank()) {
                putString(CONTENT_STYLE_GROUP_TITLE_HINT, groupTitle)
            }
        }
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setExtras(extras)

        if (!artworkUri.isNullOrBlank()) {
            metaBuilder.setArtworkUri(Uri.parse(artworkUri))
        }
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metaBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse("${TrackMediaItemMapper.SCHEME_GMUSIC}://artist/$id"))
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun resourceUri(@DrawableRes resId: Int): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(packageName)
            .appendPath(resId.toString())
            .build()

    private suspend fun preCacheNextTracks() = withContext(Dispatchers.IO) {
        val currentIndex = withContext(Dispatchers.Main) { player.currentMediaItemIndex }
        val totalCount = withContext(Dispatchers.Main) { player.mediaItemCount }
        if (currentIndex == C.INDEX_UNSET || totalCount <= 1) return@withContext

        val targetIndices = listOf(currentIndex + 1, currentIndex + 2)
            .filter { it in 0 until totalCount }

        for (idx in targetIndices) {
            if (!coroutineContext.isActive) break
            val mediaId = withContext(Dispatchers.Main) {
                if (idx < player.mediaItemCount) player.getMediaItemAt(idx).mediaId else null
            } ?: continue

            try {
                // 1. Risolvi preventivamente l'URL audio salvandolo in cache Room e memoria
                val resolvedUrl = streamResolver.resolveStreamUrl(mediaId, forceRefresh = false)
                if (resolvedUrl.isBlank() || !coroutineContext.isActive) continue

                // 2. Pre-scarica l'audio nel SimpleCache in modo che sia disponibile in galleria/offline
                val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
                val cacheDataSource = CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(okHttpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()

                val ua = when {
                    resolvedUrl.contains("c=IOS", ignoreCase = true) ->
                        "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                    resolvedUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                        "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    resolvedUrl.contains("c=ANDROID", ignoreCase = true) ->
                        "com.google.android.youtube/19.45.38 (Linux; U; Android 14) gzip"
                    else ->
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
                }

                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(resolvedUrl))
                    .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                    .setKey(mediaId)
                    .setLength(8L * 1024L * 1024L)
                    .build()

                val writer = CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    ByteArray(64 * 1024),
                    null
                )
                Log.d(TAG, "Pre-caching audio avviato per [idx=$idx, id=$mediaId]")
                writer.cache()
                Log.i(TAG, "Pre-caching audio completato con successo per $mediaId")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Pre-caching parziale o fallito per $mediaId: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "GMusicAudio"
        private const val MAX_PAGE_SIZE = 50
        const val ACTION_TOGGLE_LIKE = "com.gmusic.app.ACTION_TOGGLE_LIKE"
        const val NODE_ROOT = "root"
        const val NODE_TAB_HOME = "tab_home"
        const val NODE_TAB_SEARCH = "tab_search"
        const val NODE_TAB_LIBRARY = "tab_library"

        const val NODE_LIB_LIKED = "lib_liked"
        const val NODE_LIB_PLAYLISTS = "lib_playlists"
        const val NODE_LIB_ALBUMS = "lib_albums"
        const val NODE_LIB_ARTISTS = "lib_artists"
        const val NODE_LIB_DOWNLOADS = "lib_downloads"

        const val PREFIX_PLAYLIST = "playlist_"
        const val PREFIX_REMOTE_PLAYLIST = "remote_playlist_"
        const val PREFIX_ALBUM = "album_"
        const val PREFIX_ARTIST = "artist_"
        const val PREFIX_MIX = "mix_"
        const val PREFIX_MOOD = "mood_"
        const val PREFIX_ACTION_PLAY_ALL = "action_play_all_"
        const val PREFIX_ACTION_SHUFFLE = "action_shuffle_"
    }
}
