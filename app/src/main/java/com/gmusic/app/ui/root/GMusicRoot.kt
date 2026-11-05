package com.gmusic.app.ui.root

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gmusic.app.R
import com.gmusic.app.core.designsystem.GMusicTheme
import com.gmusic.app.playback.service.PlaybackUiEvent
import com.gmusic.app.ui.album.AlbumScreen
import com.gmusic.app.ui.artist.ArtistScreen
import com.gmusic.app.ui.auth.WebLoginScreen
import com.gmusic.app.ui.explore.ExploreScreen
import com.gmusic.app.ui.home.HomeScreen
import com.gmusic.app.ui.library.DownloadedSongsScreen
import com.gmusic.app.ui.library.HistoryScreen
import com.gmusic.app.ui.library.LibraryScreen
import com.gmusic.app.ui.library.LikedSongsScreen
import com.gmusic.app.ui.library.PlaylistDetailScreen
import com.gmusic.app.ui.player.FullScreenPlayerSheet
import com.gmusic.app.ui.player.MiniPlayerBar
import com.gmusic.app.ui.player.PlayerViewModel
import com.gmusic.app.ui.search.SearchScreen
import com.gmusic.app.ui.settings.SettingsModalBottomSheet
import com.gmusic.app.ui.settings.SettingsScreen

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("library", R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GMusicRoot(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val localPlaylists by playerViewModel.localPlaylists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettingsModal by remember { mutableStateOf(false) }

    val navigateToAlbum: (String, String) -> Unit = { id, title ->
        navController.navigate("album/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }
    val navigateToArtist: (String, String) -> Unit = { id, name ->
        navController.navigate("artist/${Uri.encode(id)}?name=${Uri.encode(name)}")
    }

    LaunchedEffect(Unit) {
        playerViewModel.uiEvents.collect { event ->
            when (event) {
                is PlaybackUiEvent.StreamErrorSkipped -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.error_stream_resolved_skip)
                    )
                }
            }
        }
    }

    val seedColor = if (playerState.adaptiveColorEnabled) playerState.adaptiveArtworkColor else null

    GMusicTheme(seedPrimaryColor = seedColor) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = playerState.isFullScreenPlayerOpen && playerState.currentTrack != null,
                label = "playerContainerTransform"
            ) { isFullPlayer ->
                if (isFullPlayer) {
                    BackHandler {
                        playerViewModel.closeFullScreenPlayer()
                    }
                    FullScreenPlayerSheet(
                        state = playerState,
                        playlists = localPlaylists,
                        onClose = playerViewModel::closeFullScreenPlayer,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSeekTo = playerViewModel::seekTo,
                        onSkipNext = playerViewModel::skipNext,
                        onSkipPrevious = playerViewModel::skipPrevious,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeatMode,
                        onToggleLike = playerViewModel::toggleCurrentTrackLike,
                        onOpenQueue = { playerViewModel.setQueueSheetOpen(true) },
                        onCloseQueue = { playerViewModel.setQueueSheetOpen(false) },
                        onPlayQueueIndex = playerViewModel::playQueueIndex,
                        onMoveQueueItem = playerViewModel::moveQueueItem,
                        onRemoveQueueItem = playerViewModel::removeQueueItem,
                        onOpenLyrics = { playerViewModel.setLyricsSheetOpen(true) },
                        onCloseLyrics = { playerViewModel.setLyricsSheetOpen(false) },
                        onAddCurrentToPlaylist = playerViewModel::addCurrentTrackToPlaylist,
                        onCreatePlaylistAndAddCurrent = playerViewModel::createPlaylistAndAddCurrent,
                        onDownloadCurrentTrack = playerViewModel::downloadCurrentTrack,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (playerState.currentTrack != null) {
                                        MiniPlayerBar(
                                            state = playerState,
                                            onTogglePlayPause = playerViewModel::togglePlayPause,
                                            onToggleLike = playerViewModel::toggleCurrentTrackLike,
                                            onSkipNext = playerViewModel::skipNext,
                                            onSkipPrevious = playerViewModel::skipPrevious,
                                            onDismissStop = playerViewModel::stopAndDismiss,
                                            onOpenFullPlayer = playerViewModel::openFullScreenPlayer,
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                    }
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                    ) {
                                        TopLevelDestination.entries.forEach { destination ->
                                            val selected = currentRoute == destination.route
                                            NavigationBarItem(
                                                selected = selected,
                                                alwaysShowLabel = true,
                                                onClick = {
                                                    if (destination == TopLevelDestination.LIBRARY) {
                                                        // Se l'utente si trova su un sotto-schermo (artista, album, playlist, cronologia, preferiti), torna alla schermata iniziale di Libreria
                                                        if (currentRoute != TopLevelDestination.LIBRARY.route) {
                                                            val popped = navController.popBackStack(TopLevelDestination.LIBRARY.route, false)
                                                            if (popped) return@NavigationBarItem
                                                        }
                                                    }
                                                    navController.navigate(destination.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                                        contentDescription = stringResource(destination.labelRes)
                                                    )
                                                },
                                                label = {
                                                    Text(text = stringResource(destination.labelRes))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = TopLevelDestination.HOME.route,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                composable(TopLevelDestination.HOME.route) {
                                    HomeScreen(
                                        onOpenSettings = { showSettingsModal = true },
                                        onOpenAccount = { showSettingsModal = true },
                                        onOpenWebLogin = { navController.navigate("web_login") },
                                        onOpenLikedSongs = { navController.navigate("liked_songs") },
                                        onOpenPlaylist = { playlistId ->
                                            navController.navigate("playlist/${Uri.encode(playlistId)}")
                                        },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum
                                    )
                                }
                                composable(TopLevelDestination.SEARCH.route) {
                                    SearchScreen(
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable(TopLevelDestination.LIBRARY.route) {
                                    LibraryScreen(
                                        onOpenLikedSongs = { navController.navigate("liked_songs") },
                                        onOpenDownloadedSongs = { navController.navigate("downloaded_songs") },
                                        onOpenPlaylist = { playlistId ->
                                            navController.navigate("playlist/${Uri.encode(playlistId)}")
                                        },
                                        onOpenAlbum = navigateToAlbum,
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onOpenArtist = navigateToArtist,
                                        onOpenHistory = { navController.navigate("history") },
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable("history") {
                                    HistoryScreen(
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable("liked_songs") {
                                    LikedSongsScreen(
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable("downloaded_songs") {
                                    DownloadedSongsScreen(
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable(
                                    route = "playlist/{playlistId}",
                                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val plId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                                    PlaylistDetailScreen(
                                        playlistId = plId,
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        onNavigateToAlbum = navigateToAlbum,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable(
                                    route = "album/{albumId}?title={title}",
                                    arguments = listOf(
                                        navArgument("albumId") { type = NavType.StringType },
                                        navArgument("title") { type = NavType.StringType; defaultValue = "Album" }
                                    )
                                ) { backStackEntry ->
                                    val albId = backStackEntry.arguments?.getString("albumId") ?: return@composable
                                    val title = backStackEntry.arguments?.getString("title") ?: "Album"
                                    AlbumScreen(
                                        albumId = albId,
                                        titleHint = title,
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> playerViewModel.playTrack(track, queue) },
                                        onNavigateToArtist = navigateToArtist,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable(
                                    route = "artist/{artistId}?name={name}",
                                    arguments = listOf(
                                        navArgument("artistId") { type = NavType.StringType },
                                        navArgument("name") { type = NavType.StringType; defaultValue = "Artista" }
                                    )
                                ) { backStackEntry ->
                                    val artId = backStackEntry.arguments?.getString("artistId") ?: return@composable
                                    val name = backStackEntry.arguments?.getString("name") ?: "Artista"
                                    ArtistScreen(
                                        artistId = artId,
                                        nameHint = name,
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue ->
                                            playerViewModel.playTrack(
                                                track = track,
                                                queueContext = queue,
                                                isArtistPlayback = true,
                                                artistName = name,
                                                artistId = artId
                                            )
                                        },
                                        onNavigateToAlbum = navigateToAlbum,
                                        onNavigateToArtist = navigateToArtist,
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                                composable("settings") {
                                    LaunchedEffect(Unit) {
                                        showSettingsModal = true
                                        navController.popBackStack()
                                    }
                                }
                                composable("web_login") {
                                    val settingsVm: com.gmusic.app.ui.settings.SettingsViewModel = hiltViewModel()
                                    WebLoginScreen(
                                        onCookiesExtracted = { cookies, visitor ->
                                            settingsVm.saveManualCookie(cookies, visitor)
                                            navController.popBackStack()
                                        },
                                        onBack = { navController.popBackStack() },
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                            }
                        }

                        if (showSettingsModal) {
                            SettingsModalBottomSheet(
                                onDismiss = { showSettingsModal = false },
                                onOpenWebLogin = {
                                    showSettingsModal = false
                                    navController.navigate("web_login")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
