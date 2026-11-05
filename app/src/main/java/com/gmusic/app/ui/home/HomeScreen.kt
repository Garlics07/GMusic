package com.gmusic.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.HomeItemType
import com.gmusic.app.domain.model.HomeSection
import com.gmusic.app.domain.model.HomeShelfItem
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track

private sealed interface QuickAccessGridEntry {
    data object LikedSongs : QuickAccessGridEntry
    data class RecentTrack(val track: Track) : QuickAccessGridEntry
    data class PlaylistEntry(val playlist: PlaylistSummary) : QuickAccessGridEntry
    data class ArtistEntry(val artist: ArtistSummary) : QuickAccessGridEntry
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit = onOpenSettings,
    onOpenWebLogin: () -> Unit = {},
    onOpenLikedSongs: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val savedArtists by viewModel.savedArtists.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val accountAvatarUrl by viewModel.accountAvatarUrl.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val visibleSections = remember(state.sections, state.selectedFilter) {
        when (state.selectedFilter) {
            HomeChipFilter.ALL -> state.sections
            HomeChipFilter.MUSIC -> state.sections.filter { it.isSongShelf }
        }
    }

    // Infinite loading quando gli ultimi 3 scaffali diventano visibili
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 2 && lastVisibleIndex >= (totalItems - 3)
        }
    }

    LaunchedEffect(shouldLoadMore, state.isLoading, state.isRefreshing) {
        if (shouldLoadMore && !state.isLoading && !state.isRefreshing) {
            viewModel.loadNextPage()
        }
    }

    val defaultTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    val targetTint = if (recentTracks.isNotEmpty() && state.topDominantColor != null) {
        state.topDominantColor!!.copy(alpha = 0.52f)
    } else {
        defaultTint
    }
    val animatedTopTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 650),
        label = "homeTopTint"
    )
    val backgroundColor = MaterialTheme.colorScheme.background

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Sezione superiore con gradiente dinamico di 280dp + Top Bar (Avatar 32dp + Chips) + Griglia 2x3
            item(key = "home_top_header_and_grid") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(animatedTopTint, backgroundColor),
                                startY = 0f,
                                endY = 780f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top bar: in alto a sinistra SOLO l'icona account Google, e accanto il saluto
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLoggedIn) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(onClick = {
                                        if (!isLoggedIn) {
                                            onOpenWebLogin()
                                        } else {
                                            onOpenAccount()
                                        }
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!accountAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = accountAvatarUrl,
                                        contentDescription = stringResource(R.string.settings_account),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = stringResource(R.string.settings_account),
                                        tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                            val greeting = when (hour) {
                                in 5..12 -> stringResource(R.string.greeting_morning)
                                in 13..17 -> stringResource(R.string.greeting_afternoon)
                                else -> stringResource(R.string.greeting_evening)
                            }
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // 2. Griglia 3x2: Slot 1 Canzoni preferite, Slot 2-6 mix randomico tra playlist, artisti e brani recenti
                        val gridEntries = remember(recentTracks, likedTracks, playlists, savedArtists) {
                            buildList {
                                add(QuickAccessGridEntry.LikedSongs)
                                val pool = mutableListOf<QuickAccessGridEntry>()
                                playlists.take(6).forEach { pool.add(QuickAccessGridEntry.PlaylistEntry(it)) }
                                savedArtists.take(6).forEach { pool.add(QuickAccessGridEntry.ArtistEntry(it)) }
                                recentTracks.take(6).forEach { pool.add(QuickAccessGridEntry.RecentTrack(it)) }

                                val shuffled = pool.shuffled(java.util.Random(System.currentTimeMillis() / 60000L))
                                for (cand in shuffled) {
                                    if (size >= 6) break
                                    add(cand)
                                }
                                if (size < 6) {
                                    for (track in recentTracks) {
                                        if (size >= 6) break
                                        val entry = QuickAccessGridEntry.RecentTrack(track)
                                        if (!contains(entry)) add(entry)
                                    }
                                }
                            }
                        }

                        if (gridEntries.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val rows = gridEntries.chunked(2)
                                for (rowItems in rows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (entry in rowItems) {
                                            when (entry) {
                                                is QuickAccessGridEntry.LikedSongs -> {
                                                    LikedSongsGridCard(
                                                        onClick = onOpenLikedSongs,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                is QuickAccessGridEntry.RecentTrack -> {
                                                    RecentGridCard(
                                                        track = entry.track,
                                                        onClick = { onPlayTrack(entry.track, recentTracks) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                is QuickAccessGridEntry.PlaylistEntry -> {
                                                    PlaylistGridCard(
                                                        playlist = entry.playlist,
                                                        onClick = {
                                                            if (entry.playlist.id.startsWith("local_pl_")) {
                                                                onOpenPlaylist(entry.playlist.id)
                                                            } else {
                                                                onNavigateToAlbum(entry.playlist.id, entry.playlist.title)
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                is QuickAccessGridEntry.ArtistEntry -> {
                                                    ArtistGridCard(
                                                        artist = entry.artist,
                                                        onClick = { onNavigateToArtist(entry.artist.id, entry.artist.name) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Scaffali (Shelves) con copertine da 150dp, spaziatura 24dp
            items(
                items = visibleSections,
                key = { section -> section.id.ifEmpty { section.title } }
            ) { section ->
                HomeShelfSection(
                    section = section,
                    onItemClick = { item ->
                        when (item.type) {
                            HomeItemType.SONG -> {
                                val targetTrack = item.track ?: section.tracks.firstOrNull { it.id == item.id }
                                if (targetTrack != null) {
                                    val queue = section.tracks.ifEmpty { listOf(targetTrack) }
                                    onPlayTrack(targetTrack, queue)
                                }
                            }
                            HomeItemType.MIX -> {
                                val queue = item.tracks.ifEmpty { section.tracks }
                                val first = queue.firstOrNull()
                                if (first != null) {
                                    onPlayTrack(first, queue)
                                } else if (item.id.isNotBlank()) {
                                    onNavigateToAlbum(item.id, item.title)
                                }
                            }
                            HomeItemType.ALBUM -> {
                                onNavigateToAlbum(item.id, item.title)
                            }
                            HomeItemType.PLAYLIST -> {
                                if (item.id.startsWith("local_pl_")) {
                                    onOpenPlaylist(item.id)
                                } else {
                                    onNavigateToAlbum(item.id, item.title)
                                }
                            }
                            HomeItemType.ARTIST -> {
                                onNavigateToArtist(item.id, item.title)
                            }
                        }
                    }
                )
            }

            // Skeleton shimmer durante il caricamento iniziale o infinito
            if (state.isLoading) {
                items(count = 2, key = { "shimmer_shelf_$it" }) {
                    ShimmerShelfSkeleton()
                }
            }
        }
    }
}

@Composable
private fun SpotifyFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = textColor
        )
    }
}

@Composable
private fun RecentGridCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(6.dp)
    val coverShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)

    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cardShape
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(coverShape)
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun LikedSongsGridCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(6.dp)
    val coverShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)

    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cardShape
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(coverShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.library_liked_songs),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(6.dp)
    val coverShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
    val coverUrl = playlist.thumbnailUrls.firstOrNull().orEmpty()

    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cardShape
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(coverShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun ArtistGridCard(
    artist: ArtistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(6.dp)
    val coverShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)

    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cardShape
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (artist.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(coverShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeShelfSection(
    section: HomeSection,
    onItemClick: (HomeShelfItem) -> Unit
) {
    val shelfItems = remember(section) {
        if (section.items.isNotEmpty()) {
            section.items
        } else {
            section.tracks.map { track ->
                HomeShelfItem(
                    id = track.id,
                    title = track.title,
                    subtitle = track.artistName,
                    thumbnailUrl = track.hdArtworkUrl,
                    type = HomeItemType.SONG,
                    track = track,
                    tracks = section.tracks
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
            if (!section.subtitle.isNullOrBlank()) {
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = shelfItems,
                key = { item -> "${section.id}_${item.type}_${item.id}" }
            ) { item ->
                HomeShelfCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun HomeShelfCard(
    item: HomeShelfItem,
    onClick: () -> Unit
) {
    val isArtist = item.type == HomeItemType.ARTIST
    val coverShape = if (isArtist) CircleShape else RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    ) {
        AsyncImage(
            model = item.hdArtworkUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(150.dp)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShimmerShelfSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 200f)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(180.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(150.dp)) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}
