package com.gmusic.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.MoodGenreChip
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchFilter(val key: String) {
    ALL("all"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists")
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val isLoading: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val songResults: List<Track> = emptyList(),
    val artistResults: List<ArtistSummary> = emptyList(),
    val albumResults: List<AlbumSummary> = emptyList(),
    val playlistResults: List<PlaylistSummary> = emptyList(),
    val exploreCharts: List<Track> = emptyList(),
    val exploreNewReleases: List<AlbumSummary> = emptyList(),
    val exploreMoods: List<MoodGenreChip> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val offlineDownloadController: com.gmusic.app.data.download.OfflineDownloadController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = musicRepository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localPlaylists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    init {
        loadExploreCategories()
    }

    private fun loadExploreCategories() {
        viewModelScope.launch {
            try {
                val (charts, newReleases, moods) = musicRepository.loadExploreItalyData()
                _uiState.update {
                    it.copy(
                        exploreCharts = charts,
                        exploreNewReleases = newReleases,
                        exploreMoods = moods
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        if (newQuery.trim().length < 2) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    suggestions = emptyList(),
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    playlistResults = emptyList()
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280L)
            val clean = newQuery.trim()
            val liveSuggestions = musicRepository.getSearchSuggestions(clean)
            _uiState.update { it.copy(suggestions = liveSuggestions) }
            executeSearch(clean, _uiState.value.filter, saveHistory = false)
        }
    }

    fun onFilterChange(newFilter: SearchFilter) {
        if (_uiState.value.filter == newFilter) return
        _uiState.update { it.copy(filter = newFilter) }
        val clean = _uiState.value.query.trim()
        if (clean.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(clean, newFilter, saveHistory = false)
            }
        }
    }

    fun onSubmitSearch(query: String = _uiState.value.query) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        searchJob?.cancel()
        _uiState.update { it.copy(query = clean, suggestions = emptyList()) }
        searchJob = viewModelScope.launch {
            executeSearch(clean, _uiState.value.filter, saveHistory = true)
        }
    }

    private suspend fun executeSearch(cleanQuery: String, filter: SearchFilter, saveHistory: Boolean) {
        _uiState.update { it.copy(isLoading = true) }
        if (saveHistory) {
            musicRepository.saveSearchQuery(cleanQuery)
        }
        val result = musicRepository.searchAllCategories(cleanQuery, filter.key)
        _uiState.update {
            it.copy(
                isLoading = false,
                songResults = result.songs,
                artistResults = result.artists,
                albumResults = result.albums,
                playlistResults = result.playlists
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            musicRepository.clearSearchHistory()
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
