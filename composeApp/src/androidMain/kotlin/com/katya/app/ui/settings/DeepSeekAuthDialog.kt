package com.katya.app.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                val cookies = CookieManager.getInstance().getCookie(url)
                                if (cookies != null) {
                                    val tokenMatch = Regex("user_session=([^;]+)").find(cookies)
                                    
                                    if (tokenMatch != null) {
                                        onTokenExtracted(tokenMatch.groupValues[1])
                                    }
                                }
                            }
                        }
                        loadUrl("https://chat.deepseek.com/")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
