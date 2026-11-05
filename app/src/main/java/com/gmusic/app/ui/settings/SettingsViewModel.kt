package com.gmusic.app.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmusic.app.data.auth.AuthCredentialStore
import com.gmusic.app.data.download.OfflineDownloadController
import com.gmusic.app.data.local.EqualizerPreset
import com.gmusic.app.data.local.SettingsDataStore
import com.gmusic.app.data.local.UserSettings
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.playback.equalizer.AudioEffectsSupport
import com.gmusic.app.playback.equalizer.GMusicEqualizerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val equalizerManager: GMusicEqualizerManager,
    private val offlineDownloadController: OfflineDownloadController,
    private val authCredentialStore: AuthCredentialStore,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val accountAvatarUrl: StateFlow<String?> = musicRepository.accountAvatarUrl
    val accountName: StateFlow<String?> = musicRepository.accountName

    val settings: StateFlow<UserSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val effectsSupport: StateFlow<AudioEffectsSupport> = equalizerManager.support

    val isLoggedIn: StateFlow<Boolean> = authCredentialStore.isLoggedIn

    private val _cacheSizeMb = MutableStateFlow(0L)
    val cacheSizeMb: StateFlow<Long> = _cacheSizeMb.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "gmusic_audio_cache")
            val bytes = if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _cacheSizeMb.value = bytes / (1024L * 1024L)
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "gmusic_audio_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            withContext(Dispatchers.Main) {
                refreshCacheSize()
            }
        }
    }

    fun deleteAllDownloads() {
        offlineDownloadController.removeAllDownloads()
        refreshCacheSize()
    }

    fun setAppLanguage(tag: String) {
        viewModelScope.launch {
            settingsDataStore.setAppLanguage(tag)
            val localeList = if (tag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            withContext(Dispatchers.Main) {
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
    }

    fun setAdaptivePlayerColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdaptivePlayerColor(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settingsDataStore.setCrossfadeSeconds(seconds) }
    }

    fun setHighAudioQuality(high: Boolean) {
        viewModelScope.launch { settingsDataStore.setHighAudioQuality(high) }
    }

    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEqEnabled(enabled) }
    }

    fun selectEqPreset(preset: EqualizerPreset) {
        viewModelScope.launch { settingsDataStore.applyEqPreset(preset) }
    }

    fun setEqBandDb(bandIndex: Int, dbValue: Int) {
        val current = settings.value.eqBandsDb
        viewModelScope.launch { settingsDataStore.setEqBandDb(bandIndex, dbValue, current) }
    }

    fun setBassBoostPercent(percent: Int) {
        viewModelScope.launch { settingsDataStore.setBassBoostPercent(percent) }
    }

    fun setVirtualizerPercent(percent: Int) {
        viewModelScope.launch { settingsDataStore.setVirtualizerPercent(percent) }
    }

    fun saveInstances(pipedCsv: String, invidiousCsv: String) {
        viewModelScope.launch {
            settingsDataStore.setPipedInstancesCsv(pipedCsv)
            settingsDataStore.setInvidiousInstancesCsv(invidiousCsv)
        }
    }

    fun saveManualCookie(cookie: String, visitorData: String? = null) {
        authCredentialStore.saveCredentials(cookie, visitorData)
    }

    fun logout() {
        authCredentialStore.clearCredentials()
    }
}
