package com.gmusic.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadController: com.gmusic.app.data.download.OfflineDownloadController
) : ViewModel() {

    val likedTracks: StateFlow<List<Track>> = musicRepository.observeLikedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localPlaylists: StateFlow<List<PlaylistSummary>> = musicRepository.observeLocalPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val remotePlaylists: StateFlow<List<PlaylistSummary>> = musicRepository.remotePlaylists

    val isLoggedIn: StateFlow<Boolean> = musicRepository.isLoggedIn

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val savedAlbums: StateFlow<List<AlbumSummary>> = musicRepository.observeSavedAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedArtists: StateFlow<List<ArtistSummary>> = musicRepository.observeSavedArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadProgress: StateFlow<Map<String, Float>> = downloadController.downloadProgress

    val downloadedTracks: StateFlow<List<Track>> = combine(
        musicRepository.observeDownloadedTracks(),
        downloadController.activeDownloadingTracks
    ) { dbDownloaded, activeDownloading ->
        val activeList = activeDownloading.values.filter { active ->
            dbDownloaded.none { it.id == active.id }
        }
        activeList + dbDownloaded
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyTracks: StateFlow<List<Track>> = musicRepository.observeRecentHistory(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<Pair<PlaylistSummary?, List<Track>>>(null to emptyList())
    val selectedPlaylist: StateFlow<Pair<PlaylistSummary?, List<Track>>> = _selectedPlaylist.asStateFlow()

    init {
        syncRemoteLibrary()
    }

    fun clearHistory() {
        viewModelScope.launch {
            musicRepository.clearPlayHistory()
        }
    }

    fun syncRemoteLibrary() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                musicRepository.syncRemoteLibraryIfLoggedIn()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun observePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.observePlaylistDetail(playlistId).collectLatest { detail ->
                _selectedPlaylist.value = detail
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(title)
        }
    }

    fun renamePlaylist(playlistId: String, newTitle: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newTitle)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderPlaylistTracks(playlistId: String, orderedTracks: List<Track>) {
        viewModelScope.launch {
            musicRepository.reorderPlaylistTracks(playlistId, orderedTracks)
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
        downloadController.downloadTrack(track)
    }

    fun removeDownload(track: Track) {
        downloadController.removeDownload(track.id)
    }

    fun downloadTracks(tracks: List<Track>) {
        downloadController.downloadTracks(tracks)
    }
}
