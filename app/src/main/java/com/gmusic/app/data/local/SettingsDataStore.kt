package com.gmusic.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gmusic_settings")

data class EqualizerPreset(
    val id: String,
    val labelIt: String,
    val bandsDb: List<Int> // 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz (-15..+15 dB)
) {
    companion object {
        val ALL_PRESETS = listOf(
            EqualizerPreset("normale", "Normale", listOf(0, 0, 0, 0, 0)),
            EqualizerPreset("bass_boost", "Bass Boost", listOf(6, 4, 0, 0, 0)),
            EqualizerPreset("rock", "Rock", listOf(4, 2, -2, 3, 5)),
            EqualizerPreset("pop", "Pop", listOf(-1, 2, 4, 2, -1)),
            EqualizerPreset("jazz", "Jazz", listOf(3, 1, -2, 2, 4)),
            EqualizerPreset("vocale", "Vocale", listOf(-2, 0, 4, 3, 0))
        )
    }
}

data class UserSettings(
    val adaptivePlayerColor: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val highAudioQuality: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqPresetId: String = "normale",
    val eqBandsDb: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostPercent: Int = 0,
    val virtualizerPercent: Int = 0,
    val pipedInstancesCsv: String = DEFAULT_PIPED_INSTANCES.joinToString(","),
    val invidiousInstancesCsv: String = DEFAULT_INVIDIOUS_INSTANCES.joinToString(","),
    val lastQueueJson: String = "",
    val lastQueueIndex: Int = 0,
    val lastPositionMs: Long = 0L
) {
    val pipedInstances: List<String>
        get() = pipedInstancesCsv.split(",").map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

    val invidiousInstances: List<String>
        get() = invidiousInstancesCsv.split(",").map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

    companion object {
        val DEFAULT_PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.drgns.space"
        )
        val DEFAULT_INVIDIOUS_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacyredirect.com"
        )
    }
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ADAPTIVE_COLOR = booleanPreferencesKey("adaptive_player_color")
        val CROSSFADE_SEC = intPreferencesKey("crossfade_seconds")
        val HIGH_AUDIO_QUALITY = booleanPreferencesKey("high_audio_quality")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val EQ_BANDS_CSV = stringPreferencesKey("eq_bands_csv")
        val BASS_BOOST_PCT = intPreferencesKey("bass_boost_pct")
        val VIRTUALIZER_PCT = intPreferencesKey("virtualizer_pct")
        val PIPED_INSTANCES = stringPreferencesKey("piped_instances_csv")
        val INVIDIOUS_INSTANCES = stringPreferencesKey("invidious_instances_csv")
        val LAST_QUEUE_JSON = stringPreferencesKey("last_queue_json")
        val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val bands = prefs[Keys.EQ_BANDS_CSV]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull()?.coerceIn(-15, 15) }
            ?.takeIf { it.size == 5 }
            ?: listOf(0, 0, 0, 0, 0)

        UserSettings(
            adaptivePlayerColor = prefs[Keys.ADAPTIVE_COLOR] ?: false,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SEC] ?: 0,
            highAudioQuality = prefs[Keys.HIGH_AUDIO_QUALITY] ?: true,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPresetId = prefs[Keys.EQ_PRESET_ID] ?: "normale",
            eqBandsDb = bands,
            bassBoostPercent = (prefs[Keys.BASS_BOOST_PCT] ?: 0).coerceIn(0, 100),
            virtualizerPercent = (prefs[Keys.VIRTUALIZER_PCT] ?: 0).coerceIn(0, 100),
            pipedInstancesCsv = prefs[Keys.PIPED_INSTANCES]
                ?: UserSettings.DEFAULT_PIPED_INSTANCES.joinToString(","),
            invidiousInstancesCsv = prefs[Keys.INVIDIOUS_INSTANCES]
                ?: UserSettings.DEFAULT_INVIDIOUS_INSTANCES.joinToString(","),
            lastQueueJson = prefs[Keys.LAST_QUEUE_JSON] ?: "",
            lastQueueIndex = prefs[Keys.LAST_QUEUE_INDEX] ?: 0,
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L
        )
    }

    suspend fun setAdaptivePlayerColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADAPTIVE_COLOR] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_SEC] = seconds.coerceIn(0, 12) }
    }

    suspend fun setHighAudioQuality(high: Boolean) {
        context.dataStore.edit { it[Keys.HIGH_AUDIO_QUALITY] = high }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun applyEqPreset(preset: EqualizerPreset) {
        context.dataStore.edit {
            it[Keys.EQ_PRESET_ID] = preset.id
            it[Keys.EQ_BANDS_CSV] = preset.bandsDb.joinToString(",")
        }
    }

    suspend fun setEqBandDb(bandIndex: Int, dbValue: Int, currentBands: List<Int>) {
        val mutable = currentBands.toMutableList()
        if (bandIndex in 0 until 5) {
            mutable[bandIndex] = dbValue.coerceIn(-15, 15)
        }
        context.dataStore.edit {
            it[Keys.EQ_PRESET_ID] = "custom"
            it[Keys.EQ_BANDS_CSV] = mutable.joinToString(",")
        }
    }

    suspend fun setBassBoostPercent(percent: Int) {
        context.dataStore.edit { it[Keys.BASS_BOOST_PCT] = percent.coerceIn(0, 100) }
    }

    suspend fun setVirtualizerPercent(percent: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_PCT] = percent.coerceIn(0, 100) }
    }

    suspend fun setPipedInstancesCsv(csv: String) {
        context.dataStore.edit { it[Keys.PIPED_INSTANCES] = csv }
    }

    suspend fun setInvidiousInstancesCsv(csv: String) {
        context.dataStore.edit { it[Keys.INVIDIOUS_INSTANCES] = csv }
    }

    suspend fun savePlaybackState(queueJson: String, queueIndex: Int, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_QUEUE_JSON] = queueJson
            it[Keys.LAST_QUEUE_INDEX] = queueIndex.coerceAtLeast(0)
            it[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }
}
