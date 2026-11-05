package com.gmusic.app.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gmusic.app.R

private fun getChromeMobileUserAgent(): String {
    val model = android.os.Build.MODEL.ifBlank { "Android Device" }
    val release = android.os.Build.VERSION.RELEASE.ifBlank { "14" }
    return "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    onCookiesExtracted: (cookies: String, visitorData: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var latestCookies by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(R.string.settings_login),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    CookieManager.getInstance().flush()
                    val cookieStr = CookieManager.getInstance()
                        .getCookie("https://music.youtube.com")
                        .orEmpty()
                    if (cookieStr.contains("SAPISID") || cookieStr.contains("3PAPISID")) {
                        onCookiesExtracted(cookieStr, null)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_confirm),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = getChromeMobileUserAgent()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            CookieManager.getInstance().flush()
                            val cookies = CookieManager.getInstance()
                                .getCookie("https://music.youtube.com")
                                .orEmpty()
                            latestCookies = cookies
                            if (url != null &&
                                url.startsWith("https://music.youtube.com") &&
                                !url.contains("accounts.google.com") &&
                                (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID="))
                            ) {
                                view?.evaluateJavascript(
                                    "(window.ytcfg && window.ytcfg.get && window.ytcfg.get('VISITOR_DATA')) || ''"
                                ) { rawVisitor ->
                                    val cleanVisitor = rawVisitor?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                                    onCookiesExtracted(cookies, cleanVisitor)
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false
                    }

                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com")
                }
            }
        )
    }
}
