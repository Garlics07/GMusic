package com.gmusic.app.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gestisce il Crossfade (0..12s) con rampe di volume morbide sul Main thread
 * senza interrompere lo stato della MediaSession né Android Auto.
 */
class CrossfadeController(
    private val player: ExoPlayer,
    private val mainScope: CoroutineScope,
    private val crossfadeSecondsProvider: () -> Int
) {
    private var monitorJob: Job? = null
    private var fadeInJob: Job? = null
    private var isFadingOut = false

    fun start() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val cfSec = crossfadeSecondsProvider()
                if (cfSec <= 0) {
                    player.volume = 1f
                    isFadingOut = false
                    return
                }
                // Esegui rampa di fade-in sul nuovo brano
                isFadingOut = false
                fadeInJob?.cancel()
                fadeInJob = mainScope.launch {
                    val steps = 20
                    val stepDelay = (cfSec * 1000L / 2L) / steps
                    player.volume = 0.15f
                    for (i in 1..steps) {
                        val fraction = i.toFloat() / steps.toFloat()
                        player.volume = (0.15f + 0.85f * fraction).coerceIn(0f, 1f)
                        delay(stepDelay.coerceAtLeast(25L))
                    }
                    player.volume = 1f
                }
            }
        })

        monitorJob = mainScope.launch {
            while (isActive) {
                val cfSec = crossfadeSecondsProvider()
                if (cfSec > 0 && player.isPlaying && player.hasNextMediaItem()) {
                    val duration = player.duration
                    val pos = player.currentPosition
                    val windowMs = cfSec * 1000L
                    if (duration > windowMs * 2 && pos >= duration - windowMs) {
                        isFadingOut = true
                        val remaining = (duration - pos).coerceAtLeast(0L)
                        val ratio = (remaining.toFloat() / windowMs.toFloat()).coerceIn(0.12f, 1f)
                        player.volume = ratio
                    } else if (!isFadingOut && fadeInJob?.isActive != true) {
                        player.volume = 1f
                    }
                }
                delay(200L)
            }
        }
    }

    fun release() {
        monitorJob?.cancel()
        fadeInJob?.cancel()
    }
}
