package com.gmusic.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "gmusic_encrypted_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("gmusic_auth_fallback", Context.MODE_PRIVATE)
    }

    private val _isLoggedIn = MutableStateFlow(hasValidCookie())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun getRawCookies(): String? = prefs.getString(KEY_COOKIES, null)?.takeIf { it.isNotBlank() }

    fun getVisitorData(): String? = prefs.getString(KEY_VISITOR_DATA, null)?.takeIf { it.isNotBlank() }

    fun saveCredentials(cookies: String, visitorData: String? = null) {
        prefs.edit()
            .putString(KEY_COOKIES, cookies.trim())
            .apply {
                if (!visitorData.isNullOrBlank()) {
                    putString(KEY_VISITOR_DATA, visitorData.trim())
                }
            }
            .apply()
        _isLoggedIn.value = hasValidCookie()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
    }

    fun hasValidCookie(): Boolean {
        val raw = getRawCookies() ?: return false
        return extractSapisid(raw) != null
    }

    private fun extractSapisid(rawCookie: String): String? {
        val map = rawCookie.split(";")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    part.substring(0, idx).trim() to part.substring(idx + 1).trim()
                } else null
            }
            .toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    fun buildSapisidHashHeader(origin: String = "https://music.youtube.com"): String? {
        val raw = getRawCookies() ?: return null
        val sapisid = extractSapisid(raw) ?: return null
        val timestampSeconds = System.currentTimeMillis() / 1000L
        val input = "$timestampSeconds $sapisid $origin"
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestampSeconds}_$sha1"
    }

    companion object {
        private const val KEY_COOKIES = "raw_cookies"
        private const val KEY_VISITOR_DATA = "visitor_data"
    }
}
