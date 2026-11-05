package com.gmusic.app.playback.equalizer

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.gmusic.app.data.local.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioEffectsSupport(
    val equalizerSupported: Boolean = true,
    val bassBoostSupported: Boolean = true,
    val virtualizerSupported: Boolean = true
)

/**
 * Gestore dell'Equalizzatore a 5 bande (60 Hz / 230 Hz / 910 Hz / 3.6 kHz / 14 kHz),
 * BassBoost (0-100%) e Virtualizer (0-100%) collegato all'`audioSessionId` di ExoPlayer.
 * Invia i broadcast OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION e gestisce con grazia
 * l'assenza di supporto hardware senza mai andare in crash.
 */
@Singleton
class GMusicEqualizerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentAudioSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var latestSettings: UserSettings = UserSettings()

    private val _support = MutableStateFlow(AudioEffectsSupport())
    val support: StateFlow<AudioEffectsSupport> = _support.asStateFlow()

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        releaseEffects()
        currentAudioSessionId = audioSessionId

        // Broadcast apertura sessione effetti audio
        try {
            val openIntent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(openIntent)
        } catch (_: Exception) {
        }

        var eqOk = false
        var bassOk = false
        var virtOk = false

        try {
            equalizer = Equalizer(0, audioSessionId)
            eqOk = true
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer non supportato sul dispositivo: ${e.message}")
            equalizer = null
        }

        try {
            val bb = BassBoost(0, audioSessionId)
            if (bb.strengthSupported) {
                bassBoost = bb
                bassOk = true
            } else {
                bb.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost non supportato sul dispositivo: ${e.message}")
            bassBoost = null
        }

        try {
            val virt = Virtualizer(0, audioSessionId)
            if (virt.strengthSupported) {
                virtualizer = virt
                virtOk = true
            } else {
                virt.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer non supportato sul dispositivo: ${e.message}")
            virtualizer = null
        }

        _support.value = AudioEffectsSupport(
            equalizerSupported = eqOk,
            bassBoostSupported = bassOk,
            virtualizerSupported = virtOk
        )

        applySettings(latestSettings)
    }

    fun applySettings(settings: UserSettings) {
        latestSettings = settings
        val enabled = settings.eqEnabled

        try {
            equalizer?.let { eq ->
                eq.enabled = enabled
                val numBands = eq.numberOfBands.toInt().coerceAtMost(5)
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { -1500 }.toInt()
                val maxMb = range.getOrElse(1) { 1500 }.toInt()
                for (i in 0 until numBands) {
                    val db = settings.eqBandsDb.getOrElse(i) { 0 }.coerceIn(-15, 15)
                    val targetMillibels = (db * 100).coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(i.toShort(), targetMillibels)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore applicazione bande Equalizer: ${e.message}")
        }

        try {
            bassBoost?.let { bb ->
                bb.enabled = enabled && settings.bassBoostPercent > 0
                if (bb.strengthSupported) {
                    val strength = (settings.bassBoostPercent.coerceIn(0, 100) * 10).toShort()
                    bb.setStrength(strength)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore applicazione BassBoost: ${e.message}")
        }

        try {
            virtualizer?.let { virt ->
                virt.enabled = enabled && settings.virtualizerPercent > 0
                if (virt.strengthSupported) {
                    val strength = (settings.virtualizerPercent.coerceIn(0, 100) * 10).toShort()
                    virt.setStrength(strength)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore applicazione Virtualizer: ${e.message}")
        }
    }

    fun releaseEffects() {
        if (currentAudioSessionId > 0) {
            try {
                val closeIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                }
                context.sendBroadcast(closeIntent)
            } catch (_: Exception) {
            }
        }
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentAudioSessionId = 0
    }

    companion object {
        private const val TAG = "GMusicAudio"
    }
}
