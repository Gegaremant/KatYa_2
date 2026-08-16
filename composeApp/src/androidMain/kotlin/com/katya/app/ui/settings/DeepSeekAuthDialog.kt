package com.katya.app.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var webViewRef by remember { mutableStateOf<WebView?>(null) }
            var statusText by remember { mutableStateOf("Войдите в DeepSeek — токен будет извлечён автоматически") }
            var isLoggedIn by remember { mutableStateOf(false) }

            Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Status bar at top
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (isLoggedIn) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportMultipleWindows(true)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode =
                                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                // Remove "wv" from user agent so DeepSeek doesn't detect WebView
                                val defaultAgent = settings.userAgentString
                                settings.userAgentString = defaultAgent.replace("; wv", "")

                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        android.util.Log.d(
                                            "DeepSeekAuth",
                                            "JS: ${consoleMessage?.message()}"
                                        )
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        android.util.Log.d("DeepSeekAuth", "Page loaded: $url")
                                    }
                                }
                                loadUrl("https://chat.deepseek.com/")
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Polling loop for token extraction
            LaunchedEffect(Unit) {
                var tokenFound = false
                var attempt = 0
                while (!tokenFound) {
                    delay(1500)
                    attempt++

                    // 1. Cookies check removed because `user_session` is just a session ID, not the API token.
                    // The real API token is in localStorage under `userToken`.

                    if (tokenFound) break

                    // 2. Try localStorage via JS — DeepSeek stores JWT in various keys
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            // Search through multiple known localStorage keys
                            val js = """
                                (function() {
                                    try {
                                        var val = localStorage.getItem('userToken');
                                        if (val && val !== 'null' && val.length > 20) {
                                            try {
                                                var j = JSON.parse(val);
                                                if (j && typeof j === 'object') {
                                                    var extracted = j.value || j.token;
                                                    if (extracted && typeof extracted === 'string' && extracted.length > 20) {
                                                        return 'LSKEY:' + extracted;
                                                    }
                                                }
                                            } catch(e) {}
                                        }
                                        return 'NOTFOUND';
                                    } catch(e) {
                                        return 'ERR:' + e.message;
                                    }
                                })();
                            """.trimIndent()

                            webViewRef?.evaluateJavascript(js) { result: String? ->
                                if (result != null) {
                                    val clean = result.trim('"').replace("\\\"", "\"")
                                    android.util.Log.d("DeepSeekAuth", "localStorage result: ${clean.take(80)}")

                                    if (!tokenFound && clean.startsWith("LSKEY:") || clean.startsWith("LSSCAN:")) {
                                        val token = clean.substringAfter(":")
                                        if (token.length > 20 && !token.startsWith("{")) {
                                            isLoggedIn = true
                                            statusText = "✅ Токен найден! Закрываем..."
                                            kotlinx.coroutines.MainScope().launch {
                                                delay(500)
                                                onTokenExtracted(token)
                                                onDismiss()
                                            }
                                            tokenFound = true
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DeepSeekAuth", "JS eval failed", e)
                        }
                    }

                    // Update status periodically
                    if (attempt % 5 == 0 && !tokenFound) {
                        statusText = "⏳ Ожидание входа... (попытка $attempt)"
                    }
                }
            }
            }
        }
    }
}
