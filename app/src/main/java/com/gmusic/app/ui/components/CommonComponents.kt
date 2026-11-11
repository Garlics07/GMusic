package com.gmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track

/**
 * Copertina automatica per le playlist locali: mosaico 2x2 delle prime 4 copertine dei brani.
 */
@Composable
fun PlaylistMosaicCover(
    thumbnailUrls: List<String>,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val cleanUrls = thumbnailUrls.filter { it.isNotBlank() }.take(4)
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            cleanUrls.isEmpty() -> {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
            cleanUrls.size < 4 -> {
                AsyncImage(
                    model = cleanUrls.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = cleanUrls[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        AsyncImage(
                            model = cleanUrls[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = cleanUrls[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        AsyncImage(
                            model = cleanUrls[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Riga di un brano con menu contestuale completo (Preferiti, Aggiungi a playlist, Vai all'artista, Vai all'album).
 */
@Composable
fun TrackRowWithMenu(
    track: Track,
    onPlay: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    onDownloadRequest: ((Track) -> Unit)? = null,
    onRemoveDownloadRequest: ((Track) -> Unit)? = null,
    onNavigateToArtist: ((String, String) -> Unit)? = null,
    onNavigateToAlbum: ((String, String) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    trailingExtraContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailingExtraContent?.invoke()

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (track.isLiked) R.string.player_unlike else R.string.player_like
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleLike(track)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_add_to_playlist)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylistRequest(track)
                    }
                )
                if (onDownloadRequest != null || onRemoveDownloadRequest != null) {
                    if (track.isDownloaded && onRemoveDownloadRequest != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove_download)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemoveDownloadRequest(track)
                            }
                        )
                    } else if (!track.isDownloaded && onDownloadRequest != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_download_track)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDownloadRequest(track)
                            }
                        )
                    }
                }
                if (onNavigateToArtist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_go_to_artist)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onNavigateToArtist(track.artistId ?: track.artistName, track.artistName)
                        }
                    )
                }
                if (onNavigateToAlbum != null && !track.albumId.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_go_to_album)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Album, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onNavigateToAlbum(track.albumId, track.albumTitle ?: track.title)
                        }
                    )
                }
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove_from_playlist)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_more_options),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromPlaylist()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Modal BottomSheet per le opzioni contestuali del brano (attivabile anche con long-press in Home).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    onDownloadRequest: ((Track) -> Unit)? = null,
    onRemoveDownloadRequest: ((Track) -> Unit)? = null,
    onNavigateToArtist: ((String, String) -> Unit)? = null,
    onNavigateToAlbum: ((String, String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header con copertina, titolo e artista
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Opzione: Riproduci
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_play_all)) },
                leadingContent = {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onPlay()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // Opzione: Like / Unlike
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (track.isLiked) R.string.player_unlike else R.string.player_like
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = if (track.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onToggleLike(track)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // Opzione: Aggiungi a playlist
            ListItem(
                headlineContent = { Text(stringResource(R.string.player_add_to_playlist)) },
                leadingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddToPlaylistRequest(track)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // Opzione: Scarica brano / Elimina download
            if (track.isDownloaded && onRemoveDownloadRequest != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.menu_remove_download),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_more_options),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRemoveDownloadRequest(track)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            } else if (!track.isDownloaded && onDownloadRequest != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_download_track)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onDownloadRequest(track)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // Opzione: Vai all'artista
            if (onNavigateToArtist != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_go_to_artist)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onNavigateToArtist(track.artistId ?: track.artistName, track.artistName)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // Opzione: Vai all'album
            if (onNavigateToAlbum != null && !track.albumId.isNullOrBlank()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_go_to_album)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Filled.Album, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onNavigateToAlbum(track.albumId, track.albumTitle ?: track.title)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistSummary>,
    onSelectPlaylist: (String, Track) -> Unit,
    onCreateAndAdd: (String, Track) -> Unit,
    onDismiss: () -> Unit
) {
    var creatingNew by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_add_to_playlist)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (creatingNew || playlists.isEmpty()) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.library_playlist_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = { creatingNew = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.library_create_playlist))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSelectPlaylist(pl.id, track)
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistMosaicCover(thumbnailUrls = pl.thumbnailUrls, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = pl.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.library_track_count, pl.trackCount),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew || playlists.isEmpty()) {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onCreateAndAdd(newTitle.trim(), track)
                            onDismiss()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
