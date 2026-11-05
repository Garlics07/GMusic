package com.gmusic.app.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import com.gmusic.app.ui.components.AddToPlaylistDialog
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDismissStop: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0L) {
        (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val accentColor by animateColorAsState(
        targetValue = state.adaptiveArtworkColor ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 500),
        label = "miniPlayerAccent"
    )

    val cardBgColor by animateColorAsState(
        targetValue = state.adaptiveArtworkColor?.let { raw ->
            Color(
                red = (raw.red * 0.36f + 0.08f).coerceIn(0f, 1f),
                green = (raw.green * 0.36f + 0.08f).coerceIn(0f, 1f),
                blue = (raw.blue * 0.36f + 0.08f).coerceIn(0f, 1f),
                alpha = 1f
            )
        } ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 500),
        label = "miniPlayerBg"
    )

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val containerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "player_container"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else modifier

    Surface(
        modifier = containerModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenFullPlayer() }
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        when {
                            dragY > 110f && abs(dragY) > abs(dragX) -> onDismissStop()
                            dragX < -120f && abs(dragX) > abs(dragY) -> onSkipNext()
                            dragX > 120f && abs(dragX) > abs(dragY) -> onSkipPrevious()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    }
                )
            },
        color = cardBgColor,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier
                            .size(48.dp)
                            .sharedElement(
                                state = rememberSharedContentState(key = "player_artwork_${track.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(RoundedCornerShape(6.dp))
                    }
                } else {
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                }

                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (track.isLiked) R.string.player_unlike else R.string.player_like
                        ),
                        tint = if (track.isLiked) accentColor else Color.White
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.player_now_playing),
                            tint = Color.White
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.22f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenPlayerSheet(
    state: PlayerUiState,
    playlists: List<PlaylistSummary>,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
    onCloseQueue: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onAddCurrentToPlaylist: (String) -> Unit,
    onCreatePlaylistAndAddCurrent: (String) -> Unit,
    onDownloadCurrentTrack: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val track = state.currentTrack ?: return
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val currentPosFraction = (state.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val animatedAccent by animateColorAsState(
        targetValue = if (state.adaptiveColorEnabled && state.adaptiveArtworkColor != null) {
            state.adaptiveArtworkColor
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 550),
        label = "fullPlayerAccent"
    )

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onSelectPlaylist = { plId, _ -> onAddCurrentToPlaylist(plId) },
            onCreateAndAdd = { title, _ -> onCreatePlaylistAndAddCurrent(title) },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (state.isQueueSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = onCloseQueue,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueueBottomSheetContent(
                queue = state.queue,
                currentIndex = state.currentQueueIndex,
                accentColor = animatedAccent,
                onPlayIndex = onPlayQueueIndex,
                onMoveItem = onMoveQueueItem,
                onRemoveItem = onRemoveQueueItem
            )
        }
    }

    if (state.isLyricsSheetOpen && state.lyrics != null) {
        ModalBottomSheet(
            onDismissRequest = onCloseLyrics,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SyncedLyricsSheetContent(
                lyrics = state.lyrics,
                currentPositionMs = state.currentPositionMs,
                accentColor = animatedAccent,
                onSeekTo = onSeekTo
            )
        }
    }

    val containerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "player_container"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else Modifier

    Box(
        modifier = containerModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.36f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { showAddToPlaylistDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Copertina HD con supporto swipe orizzontale (avanti/indietro) e verticale (chiudi)
            var artDragX by remember { mutableFloatStateOf(0f) }
            var artDragY by remember { mutableFloatStateOf(0f) }
            val artSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        state = rememberSharedContentState(key = "player_artwork_${track.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else Modifier

            AsyncImage(
                model = track.hdArtworkUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = artSharedModifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(track.id) {
                        detectDragGestures(
                            onDragStart = {
                                artDragX = 0f
                                artDragY = 0f
                            },
                            onDragEnd = {
                                when {
                                    artDragY > 140f && abs(artDragY) > abs(artDragX) -> onClose()
                                    artDragX < -130f && abs(artDragX) > abs(artDragY) -> onSkipNext()
                                    artDragX > 130f && abs(artDragX) > abs(artDragY) -> onSkipPrevious()
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                artDragX += amount.x
                                artDragY += amount.y
                            }
                        )
                    }
            )

            // Titolo, Artista, Download, Cuore
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onDownloadCurrentTrack != null) {
                    IconButton(onClick = onDownloadCurrentTrack) {
                        Icon(
                            imageVector = if (track.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = stringResource(R.string.player_download),
                            tint = if (track.isDownloaded) animatedAccent else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (track.isLiked) R.string.player_unlike else R.string.player_like
                        ),
                        tint = if (track.isLiked) animatedAccent else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Seekbar + tempi
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (sliderDragging) sliderValue else currentPosFraction,
                    onValueChange = {
                        sliderDragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (sliderValue * durationMs).toLong()
                        sliderDragging = false
                        onSeekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = animatedAccent,
                        activeTrackColor = animatedAccent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(state.currentPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDurationMs(state.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controlli principali
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (state.shuffleEnabled) {
                            animatedAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = animatedAccent,
                        contentColor = Color.Black
                    )
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = stringResource(R.string.player_repeat),
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                            animatedAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Barra azioni secondaria: Testo (nascosto se assente) e Coda di riproduzione
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.lyrics != null && state.lyrics.lines.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenLyrics)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = stringResource(R.string.player_lyrics),
                            tint = animatedAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.player_lyrics),
                            style = MaterialTheme.typography.titleMedium,
                            color = animatedAccent
                        )
                    }
                } else {
                    // Se il brano non ha testo: non mostrare nulla (nasconde l'entry point del testo)
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.player_queue),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueBottomSheetContent(
    queue: List<Track>,
    currentIndex: Int,
    accentColor: Color,
    onPlayIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.player_queue),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { idx, item ->
                val isCurrent = idx == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPlayIndex(idx) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (idx > 0) {
                        IconButton(onClick = { onMoveItem(idx, idx - 1) }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = stringResource(R.string.action_move_up),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (idx < queue.lastIndex) {
                        IconButton(onClick = { onMoveItem(idx, idx + 1) }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = stringResource(R.string.action_move_down),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (queue.size > 1) {
                        IconButton(onClick = { onRemoveItem(idx) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsSheetContent(
    lyrics: com.gmusic.app.data.lyrics.TrackLyrics,
    currentPositionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val activeLineIndex = remember(lyrics, currentPositionMs) {
        if (!lyrics.isSynced) 0
        else lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs + 250L }.coerceAtLeast(0)
    }

    LaunchedEffect(activeLineIndex) {
        if (lyrics.isSynced && activeLineIndex in lyrics.lines.indices) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.player_lyrics),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            itemsIndexed(lyrics.lines) { idx, line ->
                val isCurrent = lyrics.isSynced && idx == activeLineIndex
                Text(
                    text = line.text,
                    style = if (isCurrent) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = if (isCurrent) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (lyrics.isSynced) {
                                onSeekTo(line.timeMs)
                            }
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.ITALY, "%d:%02d", min, sec)
}
