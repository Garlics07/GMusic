package com.gmusic.app.ui.player

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.gmusic.app.data.download.OfflineDownloadController
import com.gmusic.app.data.local.SettingsDataStore
import com.gmusic.app.data.lyrics.LyricsRepository
import com.gmusic.app.data.lyrics.TrackLyrics
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import com.gmusic.app.playback.queue.TrackMediaItemMapper
import com.gmusic.app.playback.service.GMusicMediaLibraryService
import com.gmusic.app.playback.service.PlaybackEventBus
import com.gmusic.app.playback.service.PlaybackUiEvent
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isFullScreenPlayerOpen: Boolean = false,
    val lyrics: TrackLyrics? = null,
    val isQueueSheetOpen: Boolean = false,
    val isLyricsSheetOpen: Boolean = false,
    val adaptiveArtworkColor: Color? = null,
    val adaptiveColorEnabled: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val offlineDownloadController: OfflineDownloadController,
    private val okHttpClient: OkHttpClient,
    playbackEventBus: PlaybackEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val uiEvents: SharedFlow<PlaybackUiEvent> = playbackEventBus.events

    val localPlaylists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDownloads: StateFlow<Map<String, Float>> = offlineDownloadController.downloadProgress

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var likeObserverJob: Job? = null
    private var lyricsAndPaletteJob: Job? = null
    private var lastProcessedTrackId: String? = null
    private var pendingPlayRequest: Pair<Track, List<Track>>? = null
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        restoreSavedStateFromSettings()
        connectToService()
        startProgressTicker()
        observeSettings()
    }

    private fun restoreSavedStateFromSettings() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                settingsDataStore.settingsFlow.first()
            }
            if (saved.lastQueueJson.isNotBlank() && _uiState.value.currentTrack == null) {
                val restoredQueue = try {
                    json.decodeFromString<List<Track>>(saved.lastQueueJson)
                } catch (_: Exception) {
                    emptyList()
                }
                if (restoredQueue.isNotEmpty()) {
                    val safeIdx = saved.lastQueueIndex.coerceIn(0, restoredQueue.lastIndex)
                    val track = restoredQueue[safeIdx]
                    _uiState.update { state ->
                        if (state.currentTrack == null) {
                            state.copy(
                                currentTrack = track,
                                queue = restoredQueue,
                                currentQueueIndex = safeIdx,
                                currentPositionMs = saved.lastPositionMs.coerceAtLeast(0L),
                                durationMs = (track.durationSeconds * 1000L).coerceAtLeast(0L)
                            )
                        } else state
                    }
                    observeTrackLikeState(track.id)
                    if (lastProcessedTrackId != track.id) {
                        lastProcessedTrackId = track.id
                        loadLyricsAndPalette(track)
                    }
                    val controller = mediaController
                    if (controller != null && controller.mediaItemCount == 0) {
                        val mediaItems = restoredQueue.map { TrackMediaItemMapper.toMediaItem(it) }
                        controller.setMediaItems(mediaItems, safeIdx, saved.lastPositionMs.coerceAtLeast(0L))
                        controller.playWhenReady = false
                        controller.prepare()
                    }
                }
            }
        }
    }

    private fun persistStateToSettings(queue: List<Track>, index: Int, positionMs: Long) {
        if (queue.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = json.encodeToString(queue)
                settingsDataStore.savePlaybackState(encoded, index.coerceAtLeast(0), positionMs.coerceAtLeast(0L))
            } catch (_: Exception) {
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                _uiState.update { it.copy(adaptiveColorEnabled = settings.adaptivePlayerColor) }
            }
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, GMusicMediaLibraryService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    attachControllerListener(controller)
                    syncStateFromController(controller)
                    pendingPlayRequest?.let { (track, queue) ->
                        pendingPlayRequest = null
                        playTrack(track, queue)
                    }
                } catch (_: Exception) {
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun attachControllerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncStateFromController(controller)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncStateFromController(controller)
            }
        })
    }

    private fun syncStateFromController(controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            val existingQueue = _uiState.value.queue
            if (existingQueue.isNotEmpty()) {
                val safeIdx = _uiState.value.currentQueueIndex.coerceIn(0, existingQueue.lastIndex)
                val mediaItems = existingQueue.map { TrackMediaItemMapper.toMediaItem(it) }
                controller.setMediaItems(mediaItems, safeIdx, _uiState.value.currentPositionMs)
                controller.playWhenReady = false
                controller.prepare()
                return
            }
        }
        val currentItem = controller.currentMediaItem
        val track = currentItem?.let { TrackMediaItemMapper.fromMediaItem(it) }
        val queueList = (0 until controller.mediaItemCount).map { idx ->
            TrackMediaItemMapper.fromMediaItem(controller.getMediaItemAt(idx))
        }
        val duration = controller.duration.takeIf { it > 0 }
            ?: ((track?.durationSeconds ?: 0L) * 1000L)

        _uiState.update { old ->
            old.copy(
                currentTrack = track?.copy(isLiked = old.currentTrack?.takeIf { it.id == track.id }?.isLiked ?: false),
                queue = queueList,
                currentQueueIndex = controller.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode
            )
        }

        if (track != null) {
            observeTrackLikeState(track.id)
            if (lastProcessedTrackId != track.id) {
                lastProcessedTrackId = track.id
                loadLyricsAndPalette(track)
            }
        } else {
            likeObserverJob?.cancel()
            lyricsAndPaletteJob?.cancel()
            lastProcessedTrackId = null
        }
    }

    private fun loadLyricsAndPalette(track: Track) {
        lyricsAndPaletteJob?.cancel()
        _uiState.update { it.copy(lyrics = null, isLyricsSheetOpen = false) }
        lyricsAndPaletteJob = viewModelScope.launch {
            launch {
                val extractedColor = extractAccessiblePaletteColor(track.thumbnailUrl)
                _uiState.update { it.copy(adaptiveArtworkColor = extractedColor) }
            }
            launch {
                val trackLyrics = lyricsRepository.getLyricsForTrack(track)
                _uiState.update { state ->
                    if (state.currentTrack?.id == track.id) {
                        state.copy(lyrics = trackLyrics)
                    } else state
                }
            }
        }
    }

    /**
     * Estrae il colore dominante/vibrante tramite AndroidX Palette e garantisce contrasto WCAG AA (>= 4.5:1)
     * rispetto al testo su sfondo scuro.
     */
    private suspend fun extractAccessiblePaletteColor(imageUrl: String): Color? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(imageUrl).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val palette = Palette.from(bitmap).generate()
                    val rawArgb = palette.getVibrantColor(
                        palette.getDominantColor(0xFF1DB954.toInt())
                    )
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(rawArgb, hsl)
                    // Mantieni luminosità compresa tra 0.52 e 0.72 per garantire contrasto WCAG AA sia su sfondo scuro che per testo nero su bottone primario
                    hsl[1] = hsl[1].coerceAtLeast(0.45f)
                    hsl[2] = hsl[2].coerceIn(0.52f, 0.72f)
                    Color(ColorUtils.HSLToColor(hsl))
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun observeTrackLikeState(trackId: String) {
        likeObserverJob?.cancel()
        likeObserverJob = viewModelScope.launch {
            val cur = _uiState.value.currentTrack
            if (cur == null ||
                cur.title == "Brano sconosciuto" ||
                cur.artistName == "Artista sconosciuto" ||
                cur.artistName == "Artista" ||
                cur.albumTitle.isNullOrBlank()
            ) {
                launch {
                    musicRepository.resolveTrackById(trackId)
                }
            }
            musicRepository.observeTrackById(trackId).collectLatest { dbTrack ->
                if (dbTrack != null) {
                    _uiState.update { state ->
                        if (state.currentTrack?.id == trackId) {
                            val updatedTrack = state.currentTrack.copy(
                                title = if (dbTrack.title != "Brano sconosciuto" && dbTrack.title.isNotBlank()) dbTrack.title else state.currentTrack.title,
                                artistName = if (dbTrack.artistName != "Artista sconosciuto" && dbTrack.artistName.isNotBlank()) dbTrack.artistName else state.currentTrack.artistName,
                                artistId = dbTrack.artistId ?: state.currentTrack.artistId,
                                albumTitle = dbTrack.albumTitle ?: state.currentTrack.albumTitle,
                                albumId = dbTrack.albumId ?: state.currentTrack.albumId,
                                thumbnailUrl = dbTrack.thumbnailUrl.ifBlank { state.currentTrack.thumbnailUrl },
                                isLiked = dbTrack.isLiked,
                                isDownloaded = dbTrack.isDownloaded
                            )
                            val updatedQueue = state.queue.map { q ->
                                if (q.id == trackId) updatedTrack else q
                            }
                            state.copy(
                                currentTrack = updatedTrack,
                                queue = updatedQueue
                            )
                        } else state
                    }
                }
            }
        }
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0 } ?: _uiState.value.durationMs
                    _uiState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur.coerceAtLeast(0L)
                        )
                    }
                    tickCount++
                    if (tickCount % 16 == 0) {
                        val state = _uiState.value
                        persistStateToSettings(state.queue, state.currentQueueIndex, pos)
                    }
                }
                delay(300L)
            }
        }
    }

    fun playFromSearchQuery(query: String) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                val direct = musicRepository.searchSongs(query)
                if (direct.isNotEmpty()) direct else {
                    val full = musicRepository.searchAllCategories(query)
                    if (full.artists.isNotEmpty()) {
                        val art = full.artists.first()
                        musicRepository.fetchArtistDetail(art.id, art.name).popularTracks.ifEmpty { full.songs }
                    } else full.songs
                }
            }
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
        }
    }

    fun playFromVideoId(videoId: String) {
        viewModelScope.launch {
            val initialTrack = withContext(Dispatchers.IO) {
                musicRepository.resolveTrackById(videoId)
            } ?: Track(
                id = videoId,
                title = "YouTube Music",
                artistName = "...",
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            )
            playTrack(initialTrack)
            _uiState.update { it.copy(isFullScreenPlayerOpen = true) }

            if (initialTrack.title == "YouTube Music" || initialTrack.artistName == "...") {
                val resolved = withContext(Dispatchers.IO) {
                    musicRepository.resolveTrackById(videoId)
                }
                if (resolved != null) {
                    _uiState.update { current ->
                        if (current.currentTrack?.id == videoId) {
                            current.copy(currentTrack = resolved)
                        } else current
                    }
                }
            }
        }
    }

    fun playFromPlaylistId(playlistId: String) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                musicRepository.fetchAlbumOrPlaylistDetail(playlistId, "Playlist")
            }
            if (detail.tracks.isNotEmpty()) {
                playTrack(
                    track = detail.tracks.first(),
                    queueContext = detail.tracks
                )
                _uiState.update { it.copy(isFullScreenPlayerOpen = true) }
            }
        }
    }

    fun playTrack(
        track: Track,
        queueContext: List<Track> = listOf(track),
        isArtistPlayback: Boolean = false,
        artistName: String? = null,
        artistId: String? = null
    ) {
        val items = queueContext.ifEmpty { listOf(track) }
        val startIndex = items.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        persistStateToSettings(items, startIndex, 0L)
        val controller = mediaController
        if (controller == null) {
            pendingPlayRequest = track to queueContext
            return
        }
        val mediaItems = items.map {
            TrackMediaItemMapper.toMediaItem(
                track = it,
                artistContextName = if (isArtistPlayback) (artistName ?: track.artistName) else null,
                artistContextId = if (isArtistPlayback) (artistId ?: track.artistId) else null
            )
        }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromIndex, toIndex)
            syncStateFromController(controller)
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount && controller.mediaItemCount > 1) {
            controller.removeMediaItem(index)
            syncStateFromController(controller)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(currentPositionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_500L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun stopAndDismiss() {
        _uiState.update {
            it.copy(
                currentTrack = null,
                queue = emptyList(),
                isPlaying = false,
                isFullScreenPlayerOpen = false,
                isQueueSheetOpen = false,
                isLyricsSheetOpen = false
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.savePlaybackState("", 0, 0L)
        }
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleCurrentTrackLike() {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            val liked = musicRepository.toggleLike(current)
            _uiState.update { state ->
                state.copy(currentTrack = state.currentTrack?.copy(isLiked = liked))
            }
        }
    }

    fun addCurrentTrackToPlaylist(playlistId: String) {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, current)
        }
    }

    fun createPlaylistAndAddCurrent(title: String) {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            val id = musicRepository.createPlaylist(title)
            musicRepository.addTrackToPlaylist(id, current)
        }
    }

    fun downloadCurrentTrack() {
        val current = _uiState.value.currentTrack ?: return
        offlineDownloadController.downloadTrack(current)
    }

    fun setQueueSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isQueueSheetOpen = open) }
    }

    fun setLyricsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isLyricsSheetOpen = open) }
    }

    fun openFullScreenPlayer() {
        if (_uiState.value.currentTrack != null) {
            _uiState.update { it.copy(isFullScreenPlayerOpen = true) }
        }
    }

    fun closeFullScreenPlayer() {
        _uiState.update {
            it.copy(
                isFullScreenPlayerOpen = false,
                isQueueSheetOpen = false,
                isLyricsSheetOpen = false
            )
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
