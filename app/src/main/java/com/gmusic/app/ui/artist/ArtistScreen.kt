package com.gmusic.app.ui.artist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.ArtistDetail
import com.gmusic.app.domain.model.ArtistSummary
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
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val offlineDownloadController: com.gmusic.app.data.download.OfflineDownloadController
) : ViewModel() {

    private val _detail = MutableStateFlow<ArtistDetail?>(null)
    val detail: StateFlow<ArtistDetail?> = _detail.asStateFlow()

    private val _discography = MutableStateFlow<List<Track>>(emptyList())
    val discography: StateFlow<List<Track>> = _discography.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = musicRepository.observeActivePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadArtist(artistId: String, nameHint: String) {
        viewModelScope.launch {
            try {
                val det = musicRepository.fetchArtistDetail(artistId, nameHint)
                _detail.value = det
                val fullTracks = musicRepository.fetchArtistDiscography(artistId, det.name.ifBlank { nameHint })
                _discography.value = fullTracks
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch {
            musicRepository.observeIsArtistSaved(artistId).collectLatest { saved ->
                _isSaved.value = saved
            }
        }
    }

    fun toggleSaveArtist(artist: ArtistDetail) {
        viewModelScope.launch {
            musicRepository.toggleSaveArtist(
                artist = ArtistSummary(
                    id = artist.id,
                    name = artist.name,
                    subscribersText = null,
                    thumbnailUrl = artist.thumbnailUrl
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

    fun downloadTrack(track: Track) {
        offlineDownloadController.downloadTrack(track)
    }

    fun removeDownload(track: Track) {
        offlineDownloadController.removeDownload(track.id)
    }
}

@Composable
fun ArtistScreen(
    artistId: String,
    nameHint: String,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    LaunchedEffect(artistId, nameHint) {
        viewModel.loadArtist(artistId, nameHint)
    }

    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val discography by viewModel.discography.collectAsStateWithLifecycle()
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        val current = detail
        if (current == null) {
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
            // Header artista
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = current.hdArtworkUrl,
                        contentDescription = current.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(164.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            val songsToPlay = if (discography.isNotEmpty()) discography else current.popularTracks
            if (songsToPlay.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val shuffled = songsToPlay.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_shuffle_play))
                        }
                        val isDark = isSystemInDarkTheme()
                        OutlinedButton(
                            onClick = { viewModel.toggleSaveArtist(current) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, if (isDark) Color.White else MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else (if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (isSaved) R.string.artist_following else R.string.artist_follow
                                ),
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(
                            if (discography.size > current.popularTracks.size) R.string.artist_discography else R.string.artist_popular_songs
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(songsToPlay, key = { it.id }) { track ->
                    TrackRowWithMenu(
                        track = track,
                        onPlay = { onPlayTrack(track, songsToPlay) },
                        onToggleLike = viewModel::toggleLike,
                        onAddToPlaylistRequest = { trackForPlaylistDialog = it },
                        onDownloadRequest = viewModel::downloadTrack,
                        onRemoveDownloadRequest = viewModel::removeDownload,
                        onNavigateToAlbum = onNavigateToAlbum
                    )
                }
            }

            // Album
            if (current.albums.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.artist_albums),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(current.albums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onNavigateToAlbum(album.id, album.title) }
                            ) {
                                AsyncImage(
                                    model = album.hdArtworkUrl,
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Singoli
            if (current.singles.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.artist_singles),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(current.singles, key = { "single_${it.id}" }) { single ->
                            Column(
                                modifier = Modifier
                                    .width(132.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val match = current.popularTracks.firstOrNull { it.id == single.id }
                                            ?: Track(
                                                id = single.id,
                                                title = single.title,
                                                artistName = current.name,
                                                thumbnailUrl = single.thumbnailUrl
                                            )
                                        onPlayTrack(match, listOf(match))
                                    }
                            ) {
                                AsyncImage(
                                    model = single.hdArtworkUrl,
                                    contentDescription = single.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(132.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = single.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Artisti simili
            if (current.similarArtists.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.artist_similar),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(current.similarArtists, key = { "sim_${it.id}" }) { sim ->
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { onNavigateToArtist(sim.id, sim.name) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = sim.thumbnailUrl,
                                    contentDescription = sim.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = sim.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
