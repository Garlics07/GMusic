package com.gmusic.app

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import com.gmusic.app.core.designsystem.DarkBackground
import com.gmusic.app.core.designsystem.GMusicTheme
import com.gmusic.app.domain.model.Track
import com.gmusic.app.ui.player.PlayerViewModel
import com.gmusic.app.ui.root.GMusicRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        handleIncomingIntent(intent)
        setContent {
            GMusicTheme {
                GMusicRoot(playerViewModel = playerViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ||
            intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = intent.getStringExtra(SearchManager.QUERY)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                ?: intent.getStringExtra("android.intent.extra.query")
                ?: ""
            if (query.isNotBlank()) {
                playerViewModel.playFromSearchQuery(query.trim())
            }
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                handleDeepLinkUri(uri)
                return
            }
        }

        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val urlMatch = Regex("https?://[^\\s]+").find(sharedText)?.value
                if (urlMatch != null) {
                    handleDeepLinkUri(android.net.Uri.parse(urlMatch))
                    return
                }
            }
        }

        val videoId = intent.getStringExtra("debug_play_id") ?: return
        val title = intent.getStringExtra("debug_play_title") ?: "Test Audio"
        val artist = intent.getStringExtra("debug_play_artist") ?: "Artista"
        val track = Track(
            id = videoId,
            title = title,
            artistName = artist,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
        playerViewModel.playTrack(track)
    }

    private fun handleDeepLinkUri(uri: android.net.Uri) {
        val host = uri.host?.lowercase() ?: return
        val path = uri.path ?: ""

        when {
            host == "youtu.be" -> {
                val videoId = uri.lastPathSegment?.trim()
                if (!videoId.isNullOrBlank()) {
                    playerViewModel.playFromVideoId(videoId)
                }
            }
            host.contains("youtube.com") -> {
                if (path.startsWith("/watch")) {
                    val videoId = uri.getQueryParameter("v")
                    if (!videoId.isNullOrBlank()) {
                        playerViewModel.playFromVideoId(videoId)
                    }
                } else if (path.startsWith("/playlist")) {
                    val playlistId = uri.getQueryParameter("list")
                    if (!playlistId.isNullOrBlank()) {
                        playerViewModel.playFromPlaylistId(playlistId)
                    }
                } else if (path.startsWith("/channel")) {
                    val channelId = uri.lastPathSegment
                    if (!channelId.isNullOrBlank()) {
                        playerViewModel.playFromSearchQuery(channelId)
                    }
                }
            }
        }
    }
}

