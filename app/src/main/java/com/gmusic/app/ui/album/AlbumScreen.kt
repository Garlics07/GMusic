package com.gmusic.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.AlbumDetail
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import com.gmusic.app.ui.components.AddToPlaylistDialog
import com.gmusic.app.ui.components.TrackRowWithMenu
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _detail = MutableStateFlow<AlbumDetail?>(null)
    val detail: StateFlow<AlbumDetail?> = _detail.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadAlbum(albumId: String, titleHint: String) {
        viewModelScope.launch {
            _detail.value = musicRepository.fetchAlbumOrPlaylistDetail(albumId, titleHint)
        }
        viewModelScope.launch {
            musicRepository.observeIsAlbumSaved(albumId).collectLatest { saved ->
                _isSaved.value = saved
            }
        }
    }

    fun toggleSaveCurrentAlbum() {
        val current = _detail.value ?: return
        viewModelScope.launch {
            musicRepository.toggleSaveAlbum(
                album = AlbumSummary(
                    id = current.id,
                    title = current.title,
                    artistName = current.artistName,
                    artistId = current.artistId,
                    year = current.year,
                    thumbnailUrl = current.thumbnailUrl
                ),
                currentlySaved = _isSaved.value
            )
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
}

@Composable
fun AlbumScreen(
    albumId: String,
    titleHint: String,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId, titleHint)
    }

    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }

    trackForPlaylistDialog?.let { selectedTrack ->
        AddToPlaylistDialog(
            track = selectedTrack,
            playlists = playlists,
            onSelectPlaylist = viewModel::addTrackToPlaylist,
            onCreateAndAdd = viewModel::createPlaylistAndAdd,
            onDismiss = { trackForPlaylistDialog = null }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = viewModel::toggleSaveCurrentAlbum) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isSaved) R.string.album_saved_library else R.string.album_save_library
                        ),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        val currentDetail = detail
        if (currentDetail == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = currentDetail.hdArtworkUrl,
                        contentDescription = currentDetail.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = currentDetail.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(currentDetail.artistName, currentDetail.year).joinToString(" • "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (currentDetail.tracks.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onPlayTrack(currentDetail.tracks.first(), currentDetail.tracks) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_play_all))
                        }
                        FilledTonalButton(
                            onClick = {
                                val shuffled = currentDetail.tracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_shuffle_play))
                        }
                    }
                }

                items(currentDetail.tracks, key = { it.id }) { track ->
                    TrackRowWithMenu(
                        track = track,
                        onPlay = { onPlayTrack(track, currentDetail.tracks) },
                        onToggleLike = viewModel::toggleLike,
                        onAddToPlaylistRequest = { trackForPlaylistDialog = it },
                        onNavigateToArtist = onNavigateToArtist
                    )
                }
            }
        }
    }
}
