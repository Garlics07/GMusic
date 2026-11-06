package com.gmusic.app.ui.home

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.HomeSection
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import com.gmusic.app.domain.model.toSongShelfItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class HomeChipFilter {
    ALL,
    MUSIC
}

data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val selectedFilter: HomeChipFilter = HomeChipFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPage: Int = 0,
    val canLoadMore: Boolean = true,
    val topDominantColor: Color? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val okHttpClient: OkHttpClient,
    private val offlineDownloadController: com.gmusic.app.data.download.OfflineDownloadController
) : ViewModel() {

    val recentTracks: StateFlow<List<Track>> = musicRepository.observeRecentHistory(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedTracks: StateFlow<List<Track>> = musicRepository.observeLikedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedArtists: StateFlow<List<ArtistSummary>> = musicRepository.observeSavedArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoggedIn: StateFlow<Boolean> = musicRepository.isLoggedIn

    val accountAvatarUrl: StateFlow<String?> = musicRepository.accountAvatarUrl

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadNextPage()
        observeTopRecentCoverPalette()
        observeRecentTracksForHomeFeed()
    }

    private fun observeRecentTracksForHomeFeed() {
        viewModelScope.launch {
            recentTracks.collectLatest { recent ->
                if (recent.isNotEmpty()) {
                    _uiState.update { current ->
                        val hasRecentShelf = current.sections.any { it.id == "shelf_recent_carousel" }
                        val updatedSections = if (hasRecentShelf) {
                            current.sections.map { section ->
                                if (section.id == "shelf_recent_carousel") {
                                    section.copy(
                                        items = recent.take(10).map { it.toSongShelfItem() },
                                        tracks = recent.take(10)
                                    )
                                } else section
                            }
                        } else if (current.sections.isNotEmpty()) {
                            val mutable = current.sections.toMutableList()
                            val insertIdx = if (mutable.isNotEmpty() && mutable[0].id == "shelf_made_for_you") 1 else 0
                            mutable.add(
                                insertIdx,
                                HomeSection(
                                    id = "shelf_recent_carousel",
                                    title = "Riproduzioni recenti",
                                    isSongShelf = true,
                                    items = recent.take(10).map { it.toSongShelfItem() },
                                    tracks = recent.take(10)
                                )
                            )
                            mutable
                        } else {
                            current.sections
                        }
                        current.copy(sections = updatedSections)
                    }
                }
            }
        }
    }

    private fun observeTopRecentCoverPalette() {
        viewModelScope.launch {
            combine(recentTracks, _uiState) { recents, state ->
                recents.firstOrNull()?.thumbnailUrl
                    ?: state.sections.firstOrNull()?.items?.firstOrNull()?.thumbnailUrl
            }
                .distinctUntilChanged()
                .collectLatest { coverUrl ->
                    if (coverUrl.isNullOrBlank()) {
                        _uiState.update { it.copy(topDominantColor = null) }
                    } else {
                        val extracted = extractDominantTintColor(coverUrl)
                        _uiState.update { it.copy(topDominantColor = extracted) }
                    }
                }
        }
    }

    private suspend fun extractDominantTintColor(imageUrl: String): Color? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(imageUrl).build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val palette = Palette.from(bitmap).generate()
                    val argb = palette.getDominantColor(
                        palette.getVibrantColor(0xFF1DB954.toInt())
                    )
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(argb, hsl)
                    hsl[1] = hsl[1].coerceAtLeast(0.4f)
                    hsl[2] = hsl[2].coerceIn(0.28f, 0.52f)
                    Color(ColorUtils.HSLToColor(hsl))
                }
            } catch (_: Exception) {
                null
            }
        }

    fun selectFilter(filter: HomeChipFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            musicRepository.syncRemoteLibraryIfLoggedIn()
            val freshSections = musicRepository.buildHomeFeedSections(page = 0)
            _uiState.update {
                it.copy(
                    sections = freshSections,
                    isRefreshing = false,
                    isLoading = false,
                    currentPage = 1,
                    canLoadMore = true
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || !current.canLoadMore) return
        val nextPage = current.currentPage
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val newSections = musicRepository.buildHomeFeedSections(page = nextPage)
            _uiState.update { old ->
                val merged = if (nextPage == 0) {
                    newSections
                } else {
                    (old.sections + newSections).distinctBy { it.id.ifEmpty { it.title } }
                }
                old.copy(
                    sections = merged,
                    isLoading = false,
                    currentPage = nextPage + 1,
                    canLoadMore = newSections.isNotEmpty()
                )
            }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleLike(track)
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun createPlaylistAndAdd(title: String, track: Track) {
        viewModelScope.launch {
            val id = musicRepository.createPlaylist(title)
            musicRepository.addTrackToPlaylist(id, track)
        }
    }

    fun downloadTrack(track: Track) {
        offlineDownloadController.downloadTrack(track)
    }

    fun removeDownload(track: Track) {
        offlineDownloadController.removeDownload(track.id)
    }
}
