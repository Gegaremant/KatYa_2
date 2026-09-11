package com.katya.app.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val freeDeepSeekManager: com.katya.app.sandbox.FreeDeepSeekManager = org.koin.compose.koinInject()
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var statusText by remember { mutableStateOf("Войдите в DeepSeek — токен будет извлечён автоматически") }
        var isLoggedIn by remember { mutableStateOf(false) }
        var manualToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
        var useManualMode by remember { mutableStateOf(false) }
        
        var dsEmail by remember { mutableStateOf("") }
        var dsPassword by remember { mutableStateOf("") }
        var autoLoginTriggered by remember { mutableStateOf(false) }

        var isDoctorRunning by remember { mutableStateOf(false) }
        var doctorLog by remember { mutableStateOf<String?>(null) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Status bar at top
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        if (!isDoctorRunning) {
                                            isDoctorRunning = true
                                            doctorLog = "Запуск Doctor...\nОжидайте..."
                                            kotlinx.coroutines.MainScope().launch {
                                                val res = freeDeepSeekManager.runDoctor()
                                                doctorLog = "Результат Doctor:\n$res"
                                                isDoctorRunning = false
                                            }
                                        }
                                    },
                                ) {
                                    Text(if (isDoctorRunning) "Выполняется..." else "Лечение (Doctor)")
                                }
                                TextButton(
                                    onClick = { useManualMode = !useManualMode },
                                ) {
                                    Text(if (useManualMode) "Авто" else "Вручную")
                                }
                            }
                        }
                    }
                    if (isLoggedIn) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (useManualMode) {
                        // Manual token input
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Вставьте токен вручную",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = manualToken,
                                onValueChange = { manualToken = it },
                                label = { Text("Токен") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = {
                                        if (manualToken.text.isNotBlank()) {
                                            onTokenExtracted(manualToken.text)
                                            onDismiss()
                                        }
                                    },
                                    enabled = manualToken.text.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Подтвердить")
                                }
                                OutlinedButton(
                                    onClick = {
                                        // Open in external browser
                                        val context = webViewRef?.context
                                        if (context != null) {
                                            try {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://chat.deepseek.com/"),
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("DeepSeekAuth", "Failed to open browser", e)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Открыть в браузере")
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Native Login UI
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Авторизация в DeepSeek",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedTextField(
                                    value = dsEmail,
                                    onValueChange = { dsEmail = it },
                                    label = { Text("Email / Phone") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = dsPassword,
                                    onValueChange = { dsPassword = it },
                                    label = { Text("Пароль") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        autoLoginTriggered = true
                                        statusText = "Авторизация... (подождите 5-10 секунд)"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = dsEmail.isNotBlank() && dsPassword.isNotBlank() && !autoLoginTriggered
                                ) {
                                    Text(if (autoLoginTriggered) "Выполняется вход..." else "Войти")
                                }
                            }
                            
                            // WebView for automatic extraction (Visible for CAPTCHA if needed)
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        webViewRef = this
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.databaseEnabled = true
                                        settings.setSupportMultipleWindows(true)
                                        settings.javaScriptCanOpenWindowsAutomatically = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.mixedContentMode =
                                            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR

                                        // Remove "wv" from user agent so DeepSeek doesn't detect WebView
                                        val defaultAgent = settings.userAgentString
                                        settings.userAgentString = defaultAgent.replace("; wv", "")

                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                        webChromeClient = object : android.webkit.WebChromeClient() {
                                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                                android.util.Log.d(
                                                    "DeepSeekAuth",
                                                    "JS: ${consoleMessage?.message()}",
                                                )
                                                return super.onConsoleMessage(consoleMessage)
                                            }

                                            // DeepSeek opens the sign-in/registration form in a popup window
                                            // in some builds. Without this override the popup would be served
                                            // by a raw WebView (or the system browser) where the LTR fix never
                                            // runs. Redirect the popup into the main WebView so the LTR
                                            // injection keeps applying.
                                            override fun onCreateWindow(
                                                view: android.webkit.WebView?,
                                                isDialog: Boolean,
                                                isUserGesture: Boolean,
                                                resultMsg: android.os.Message?,
                                            ): Boolean {
                                                val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                                                    ?: return false
                                                val mainView = view ?: return false
                                                transport.setWebView(mainView)
                                                resultMsg?.sendToTarget()
                                                return true
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                android.util.Log.d("DeepSeekAuth", "Page loaded: $url")
                                                if (autoLoginTriggered) {
                                                    // Try injecting JS
                                                    val js = """
                                                        (function() {
                                                            var emailInput = document.querySelector('input[type="text"]');
                                                            var passInput = document.querySelector('input[type="password"]');
                                                            if (emailInput && passInput) {
                                                                emailInput.value = '${dsEmail.replace("'", "\\'")}';
                                                                emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                                
                                                                passInput.value = '${dsPassword.replace("'", "\\'")}';
                                                                passInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                                
                                                                setTimeout(() => {
                                                                    var btn = document.querySelector('div.ds-button') || document.querySelector('button[type="button"]');
                                                                    if(btn) btn.click();
                                                                }, 500);
                                                            }
                                                        })();
                                                    """.trimIndent()
                                                    view?.evaluateJavascript(js, null)
                                                }
                                            }
                                        }
                                        loadUrl("https://chat.deepseek.com/sign_in")
                                    }
                                },
                                update = { webView ->
                                    // If trigger is set and page already loaded
                                    if (autoLoginTriggered) {
                                        val js = """
                                            (function() {
                                                var emailInput = document.querySelector('input[type="text"]');
                                                var passInput = document.querySelector('input[type="password"]');
                                                if (emailInput && passInput && emailInput.value === '') {
                                                    emailInput.value = '${dsEmail.replace("'", "\\'")}';
                                                    emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                    
                                                    passInput.value = '${dsPassword.replace("'", "\\'")}';
                                                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                    
                                                    setTimeout(() => {
                                                        var buttons = document.querySelectorAll('.ds-button, button');
                                                        for(var i = 0; i < buttons.length; i++) {
                                                            if(buttons[i].innerText && buttons[i].innerText.toLowerCase().includes('log')) {
                                                                buttons[i].click();
                                                                break;
                                                            }
                                                        }
                                                    }, 500);
                                                }
                                            })();
                                        """.trimIndent()
                                        webView.evaluateJavascript(js, null)
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                }



                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }

                if (doctorLog != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { doctorLog = null },
                        title = { Text("DeepSeek Doctor") },
                        text = {
                            // Scrollable text
                            androidx.compose.foundation.lazy.LazyColumn {
                                item {
                                    Text(doctorLog ?: "")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { doctorLog = null }) {
                                Text("Закрыть")
                            }
                        },
                    )
                }

                // Polling loop for token extraction (only in auto mode)
                LaunchedEffect(Unit) {
                    var tokenFound = false
                    var attempt = 0
                    while (!tokenFound && !useManualMode) {
                        delay(1500)
                        attempt++

                        // 1. Try cookies first — most reliable source
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://chat.deepseek.com")
                        if (cookies != null) {
                            android.util.Log.d("DeepSeekAuth", "Cookies present (attempt $attempt)")
                            val tokenMatch = Regex("user_session=([^;\\s]+)").find(cookies)
                            if (tokenMatch != null) {
                                val token = tokenMatch.groupValues[1]
                                android.util.Log.d("DeepSeekAuth", "Cookie token found: ${token.take(20)}...")
                                if (token.length > 10) {
                                    isLoggedIn = true
                                    statusText = "✅ Токен получен! Закрываем..."
                                    delay(500)
                                    onTokenExtracted(token)
                                    onDismiss()
                                    tokenFound = true
                                    break
                                }
                            }
                        }

                        if (tokenFound) break

                        // 2. Try localStorage via JS — DeepSeek stores JWT in various keys
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            try {
                                // Search through multiple known localStorage keys
                                val js = """
                                    (function() {
                                        try {
                                            // Try direct token keys
                                            var keys = ['userToken','token','auth_token','access_token',
                                                        'Authorization','authorization','jwt','id_token',
                                                        'user_token','session_token','ds_token',
                                                        'deepseek_token','chat_token','login_token'];
                                            for (var i = 0; i < keys.length; i++) {
                                                var val = localStorage.getItem(keys[i]);
                                                if (val && val !== 'null' && val.length > 20) {
                                                    // If it looks like JSON, try to extract .value or .token fields
                                                    try {
                                                        var j = JSON.parse(val);
                                                        if (j && typeof j === 'object') {
                                                            // Skip if value is null/empty
                                                            var extracted = j.value || j.token || j.access_token || j.jwt || j.id_token;
                                                            if (extracted && typeof extracted === 'string' && extracted.length > 20) {
                                                                return 'LSKEY:' + extracted;
                                                            }
                                                            // Skip JSON objects where important field is null
                                                            continue;
                                                        }
                                                    } catch(e) {}
                                                    // Raw string token
                                                    return 'LSKEY:' + val;
                                                }
                                            }
                                            // Fallback removed to prevent matching random telemetry tokens
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

                                        if (!tokenFound && clean.startsWith("LSKEY:")) {
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
