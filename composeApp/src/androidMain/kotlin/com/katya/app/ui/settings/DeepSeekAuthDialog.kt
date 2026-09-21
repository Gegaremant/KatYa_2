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
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@Composable
actual fun PlatformDeepSeekAuthDialog(
    onTokenExtracted: (DeepSeekAuthSession) -> Unit,
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
        // The extracted token lives here so the status banner (above) can use it too.
        var extractedToken by remember { mutableStateOf<String?>(null) }

        // Anti-bot headers (x-hif-dliq / x-hif-leim) captured from outgoing requests
        var hifDliq by remember { mutableStateOf("") }
        var hifLeim by remember { mutableStateOf("") }

        // Token that was already sitting in localStorage when the dialog opened.
        // DeepSeek persists the JWT across sessions, so a dialog opened later finds
        // yesterday's token and previously reported it as a successful fresh login —
        // but the WebView had no real user_session cookie, so the backend rejected
        // the extracted session ("токен якобы получен, но не работает"). We snapshot
        // the stale value and require a token that is NOT it, i.e. written by a real
        // login that happened inside THIS dialog session.
        var staleTokenSnapshot by remember { mutableStateOf<String?>(null) }
        var cloudflareHintShown by remember { mutableStateOf(false) }

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
                                            // Manual paste: build a best-effort session from whatever
                                            // cookies/hif headers the WebView already captured.
                                            val manualCookies = CookieManager.getInstance()
                                                .getCookie("https://chat.deepseek.com")
                                            val manualSession = DeepSeekAuthSession(
                                                token = manualToken.text.trim(),
                                                cookie = manualCookies?.takeIf { it.isNotBlank() } ?: "",
                                                hifDliq = hifDliq,
                                                hifLeim = hifLeim,
                                            )
                                            onTokenExtracted(manualSession)
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
                            if (!autoLoginTriggered) {
                                // Native Login UI — shown before the user starts the flow.
                                // Once autoLoginTriggered is set the native fields are hidden
                                // so they don't overlap the WebView form and confuse the user.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Авторизация в DeepSeek",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    OutlinedTextField(
                                        value = dsEmail,
                                        onValueChange = { dsEmail = it },
                                        label = { Text("Email / Phone") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = dsPassword,
                                        onValueChange = { dsPassword = it },
                                        label = { Text("Пароль") },
                                        singleLine = true,
                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = {
                                            autoLoginTriggered = true
                                            statusText = "Авторизация... (подождите 5-10 секунд)"
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = dsEmail.isNotBlank() && dsPassword.isNotBlank(),
                                    ) {
                                        Text("Войти")
                                    }
                                }
                            } else {
                                // While the WebView autopilot is running, replace the native
                                // fields with a status banner so the user knows what's happening.
                                Surface(
                                    color = if (extractedToken != null)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = if (extractedToken != null)
                                            "✅ Токен/сессия получены! Закрываем..."
                                        else
                                            statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (extractedToken != null)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }

                            // WebView — always visible (needed for CAPTCHA / 2FA as well)
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

                                        // Register the hif hook at DOCUMENT START so it patches
                                        // window.fetch / XHR before DeepSeek's bundle captures them.
                                        // shouldInterceptRequest does not expose XHR/fetch headers.
                                        try {
                                            WebViewCompat.addDocumentStartJavaScript(
                                                this,
                                                HIF_HOOK_JS,
                                                setOf("*"),
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.w("DeepSeekAuth", "addDocumentStartJavaScript failed: ${e.message}")
                                        }

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
                                            // Capture DeepSeek's anti-bot headers (x-hif-dliq / x-hif-leim)
                                            // from outgoing requests. The official auth.js reads them from
                                            // network events; here WebView hands us the same request headers.
                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: android.webkit.WebResourceRequest?,
                                            ): android.webkit.WebResourceResponse? {
                                                val headers = request?.requestHeaders
                                                val dbgHost = request?.url?.host ?: "?"
                                                android.util.Log.d(
                                                    "DeepSeekAuth",
                                                    "shouldInterceptRequest host=$dbgHost hdrCount=${headers?.size ?: 0} names=${headers?.keys?.joinToString(",") ?: ""}",
                                                )
                                                if (headers != null) {
                                                    // Capture x-hif-* from ANY host, not just deepseek.com:
                                                    // in some builds the hif request is proxied or the
                                                    // anti-bot headers only appear on api/chat subdomains.
                                                    var hit = false
                                                    for ((k, v) in headers) {
                                                        val lk = k.lowercase()
                                                        if (lk == "x-hif-dliq" && v.isNotBlank() && hifDliq.isBlank()) {
                                                            hifDliq = v; hit = true
                                                        }
                                                        if (lk == "x-hif-leim" && v.isNotBlank() && hifLeim.isBlank()) {
                                                            hifLeim = v; hit = true
                                                        }
                                                    }
                                                    if (hit) {
                                                        android.util.Log.d(
                                                            "DeepSeekAuth",
                                                            "Captured hif from ${request.url?.host} (dliq=${hifDliq.take(12)}... leim=${hifLeim.take(12)}...)",
                                                        )
                                                    }
                                                }
                                                // A black/tinted screen + challenges.cloudflare.com means
                                                // Turnstile is blocking the page. No JS-hook can fix that —
                                                // tell the user to solve it manually in the visible WebView.
                                                val dbgHost2 = request?.url?.host ?: ""
                                                if (dbgHost2.contains("challenges.cloudflare.com") && !cloudflareHintShown) {
                                                    cloudflareHintShown = true
                                                    statusText = "⚠️ DeepSeek показывает проверку Cloudflare (Turnstile). Решите её в окне вручную — автологин продолжит после этого."
                                                }
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                android.util.Log.d("DeepSeekAuth", "Page started: $url")
                                                installHifHook(view)
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                android.util.Log.d("DeepSeekAuth", "Page loaded: $url")
                                                enforceLtr(view)
                                                if (autoLoginTriggered) {
                                                    // Two-step autopilot: DeepSeek shows email and password on
                                                    // separate screens. A plain el.value= assignment is ignored
                                                    // by React (value tracker), so we use the native setter and
                                                    // dispatch real input/change events. The loop reports its
                                                    // current step into window.__katyaAuthStep for the UI.
                                                    val js = buildAutoLoginJs(dsEmail, dsPassword)
                                                    view?.evaluateJavascript(js, null)
                                                }
                                            }
                                        }
                                        loadUrl("https://chat.deepseek.com/sign_in")
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
                    var hifDeadline: Long? = null
                    var attempt = 0
                    // After the token appears we wait a short grace period so the
                    // web client can fire requests carrying x-hif-dliq/x-hif-leim.
                    val HIF_GRACE_MS = 30_000L
                    // Whole-attempt budget: if the autopilot never manages to sign in
                    // (bad creds, DeepSeek layout change, CAPTCHA), fail loudly instead
                    // of spinning forever.
                    val TOTAL_TIMEOUT_MS = 60_000L
                    val startTime = System.currentTimeMillis()

                    // Snapshot the token already present in localStorage before any
                    // fresh login can happen. This value will be treated as STALE and
                    // rejected later, so we only report a token written by an actual
                    // login inside this dialog window (see step 3 below).
                    if (staleTokenSnapshot == null) {
                        // First wait a moment: the dialog was just opened, the WebView
                        // may still be settling. A quick retry loop is cheap.
                        repeat(5) { i ->
                            delay(300)
                            if (staleTokenSnapshot != null) return@repeat
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val snapJs = """
                                    (function() {
                                        try {
                                            var keys = ['userToken','token','auth_token','access_token',
                                                        'Authorization','authorization','jwt','id_token',
                                                        'user_token','session_token','ds_token',
                                                        'deepseek_token','chat_token','login_token'];
                                            for (var i = 0; i < keys.length; i++) {
                                                var val = localStorage.getItem(keys[i]);
                                                if (val && val !== 'null' && val.length > 20) {
                                                    try {
                                                        var j = JSON.parse(val);
                                                        if (j && typeof j === 'object') {
                                                            var extracted = j.value || j.token || j.access_token || j.jwt || j.id_token;
                                                            if (extracted && typeof extracted === 'string' && extracted.length > 20) {
                                                                return 'SNAP:' + extracted;
                                                            }
                                                            continue;
                                                        }
                                                    } catch(e) {}
                                                    return 'SNAP:' + val;
                                                }
                                            }
                                            return 'SNAP:';
                                        } catch(e) { return 'SNAP:'; }
                                    })();
                                """.trimIndent()
                                webViewRef?.evaluateJavascript(snapJs) { result: String? ->
                                    val clean = result?.trim('"') ?: ""
                                    if (clean.startsWith("SNAP:")) {
                                        val snap = clean.substringAfter("SNAP:", "")
                                        if (snap.length > 20) {
                                            staleTokenSnapshot = snap
                                            android.util.Log.d(
                                                "DeepSeekAuth",
                                                "Stale localStorage token snapshot: ${snap.take(20)}... (will reject it, need a FRESH login)",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    while ((extractedToken == null || (hifDeadline != null && System.currentTimeMillis() < hifDeadline)) && !useManualMode) {
                        delay(1500)
                        attempt++

                        if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS && extractedToken == null) {
                            statusText = "⏱️ Не удалось получить токен за 60 с. Проверьте логин/пароль или включите ручной ввод."
                            delay(2500)
                            onDismiss()
                            break
                        }

                        // 1. Try cookies first — most reliable source
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://chat.deepseek.com")
                        val cookieTokenMatch = cookies?.let { Regex("user_session=([^;\\s]+)").find(it) }
                        if (cookieTokenMatch != null) {
                            android.util.Log.d("DeepSeekAuth", "Cookies present (attempt $attempt)")
                            val token = cookieTokenMatch.groupValues[1]
                            if (extractedToken == null && token.length > 10) {
                                android.util.Log.d("DeepSeekAuth", "Cookie token found: ${token.take(20)}...")
                                extractedToken = token
                                isLoggedIn = true
                                hifDeadline = System.currentTimeMillis() + HIF_GRACE_MS
                                statusText = if (hifDliq.isBlank() || hifLeim.isBlank())
                                    "✅ Токен получен! Ждём антибот-хедеры..."
                                else
                                    "✅ Сессия получена! Закрываем..."
                            }
                        }

                        // 2. Anti-bot headers via JS as a fallback to shouldInterceptRequest
                        //    (some DeepSeek builds store them in localStorage/sessionStorage).
                        if (hifDliq.isBlank() || hifLeim.isBlank()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val hifJs = """
                                    (function() {
                                        try {
                                            var d = localStorage.getItem('x-hif-dliq') || sessionStorage.getItem('x-hif-dliq') || '';
                                            var l = localStorage.getItem('x-hif-leim') || sessionStorage.getItem('x-hif-leim') || '';
                                            return d + '|' + l;
                                        } catch(e) { return '|'; }
                                    })();
                                """.trimIndent()
                                webViewRef?.evaluateJavascript(hifJs) { result: String? ->
                                    if (result != null) {
                                        val clean = result.trim('"')
                                        val parts = clean.split("|")
                                        if (parts.size == 2) {
                                            if (parts[0].isNotBlank()) hifDliq = parts[0]
                                            if (parts[1].isNotBlank()) hifLeim = parts[1]
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Try localStorage via JS — DeepSeek stores JWT in various keys
                        if (extractedToken == null) {
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

                                            if (extractedToken == null && clean.startsWith("LSKEY:")) {
                                                val token = clean.substringAfter(":")
                                                if (token.length > 20 && !token.startsWith("{")) {
                                                    if (token == staleTokenSnapshot) {
                                                        // Same token that was already present when the dialog
                                                        // opened — no fresh login happened. Reject it and keep
                                                        // waiting, otherwise we'd report a fake success with a
                                                        // token the backend no longer recognises.
                                                        android.util.Log.d(
                                                            "DeepSeekAuth",
                                                            "REJECTING stale localStorage token (matches dialog-open snapshot): ${token.take(20)}... keep waiting for fresh login",
                                                        )
                                                        statusText = "Токен из localStorage устарел. Войдите в DeepSeek заново — ждём свежий вход..."
                                                    } else {
                                                        extractedToken = token
                                                        isLoggedIn = true
                                                        hifDeadline = System.currentTimeMillis() + HIF_GRACE_MS
                                                        statusText = if (hifDliq.isBlank() || hifLeim.isBlank())
                                                            "✅ Токен найден! Ждём антибот-хедеры..."
                                                        else
                                                            "✅ Сессия получена! Закрываем..."
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DeepSeekAuth", "JS eval failed", e)
                                }
                            }
                        }

                        // 4. Done: token + (hif captured or grace expired) -> build session
                        val token = extractedToken
                        if (token != null && token.isNotBlank() &&
                            (hifDeadline == null || (hifDliq.isNotBlank() && hifLeim.isNotBlank()) || System.currentTimeMillis() >= hifDeadline)
                        ) {
                            android.util.Log.d(
                                "DeepSeekAuth",
                                "Extracting session: hif_dliq=${hifDliq.take(16)}... hif_leim=${hifLeim.take(16)}...",
                            )
                            val fullCookie = cookies?.takeIf { it.isNotBlank() } ?: "user_session=$token"
                            val session = DeepSeekAuthSession(
                                token = token,
                                cookie = fullCookie,
                                hifDliq = hifDliq,
                                hifLeim = hifLeim,
                            )
                            statusText = "✅ Сессия получена! Закрываем..."
                            delay(500)
                            onTokenExtracted(session)
                            onDismiss()
                            break
                        }

                        // Show autopilot progress in the status bar (so the user can see the
                        // form is being filled and can also type manually if needed).
                        if (autoLoginTriggered && extractedToken == null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                webViewRef?.evaluateJavascript("window.__katyaAuthStep || ''") { res ->
                                    val step = res?.trim('"')?.takeIf { it.isNotBlank() }
                                    if (step != null && step != "wait") {
                                        val label = when (step) {
                                            "email" -> "заполняю email"
                                            "continue" -> "нажимаю «Продолжить»"
                                            "look-continue" -> "ищу кнопку «Продолжить»"
                                            "password" -> "заполняю пароль"
                                            "login" -> "нажимаю «Войти»"
                                            "look-login" -> "ищу кнопку «Войти»"
                                            "done" -> "вход выполнен"
                                            else -> step
                                        }
                                        statusText = "🤖 Автоподстановка: $label"
                                    }
                                }
                            }
                        }

                        // Update status periodically
                        if (attempt % 5 == 0 && extractedToken == null) {
                            statusText = "⏳ Ожидание входа... (попытка $attempt)"
                        }
                    }
                }
            }
        }
    }
}

/**
 * DeepSeek's sign-in form forces RTL on its inputs, which makes the caret jump to
 * position 0 on every keystroke (email/password typed right-to-left). The page bidi
 * direction can't be trusted, so we hammer the inputs with a LTR override on every
 * poll tick plus right after the page settles. `unicode-bidi: plaintext` keeps
 * direction neutral for empty fields while forcing the *layout* left-to-right.
 */
private fun enforceLtr(view: android.webkit.WebView?) {
    installHifHook(view)
    val css = java.net.URLEncoder.encode(
        "html,body{direction:ltr!important;unicode-bidi:plaintext!important}" +
            "input,textarea,select{unicode-bidi:isolate!important}" +
            "input,textarea{direction:ltr!important;text-align:left!important}",
        "UTF-8",
    )
    val js = """
        (function() {
            try {
                var force = function() {
                    document.documentElement.setAttribute('dir','ltr');
                    document.body && document.body.setAttribute('dir','ltr');
                    var els = document.querySelectorAll('input,textarea');
                    for (var i=0;i<els.length;i++) {
                        els[i].setAttribute('dir','ltr');
                        els[i].style.direction = 'ltr';
                        els[i].style.unicodeBidi = 'isolate';
                        els[i].style.textAlign = 'left';
                    }
                };
                force();
                // DeepSeek swaps in a fresh form node after auth check —
                // re-run until the page navigates away from sign_in.
                setInterval(force, 500);
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
    // Belt-and-suspenders CSS injection (covers inputs that JS hasn't
    // touched yet when the caret is already focused).
    view?.loadUrl("javascript:(function(){var s=document.createElement('style');s.textContent=decodeURIComponent('$css');document.head.appendChild(s);})()")
}

/**
 * Injects a lightweight JS hook into the WebView to intercept window.fetch and
 * XMLHttpRequest headers. Since Android WebView's shouldInterceptRequest does not
 * always capture XHR/fetch request headers for background subresource API calls,
 * this hook grabs `x-hif-dliq` and `x-hif-leim` right as JS sends them and mirrors
 * them into localStorage/sessionStorage so our polling loop can pick them up.
 */
private const val HIF_HOOK_JS = """
        (function() {
            if (window.__katyaHifHooked) return;
            window.__katyaHifHooked = true;
            try { console.log('[KatyaHif] hook installed at ' + location.pathname); } catch(e) {}
            function save(k, v) {
                try {
                    var lk = (k || '').toLowerCase();
                    if (lk.indexOf('hif') >= 0) {
                        try { console.log('[KatyaHif] header ' + lk + ' = ' + ((v||'') + '').slice(0, 16)); } catch(e) {}
                    }
                    if (lk === 'x-hif-dliq' && v) {
                        localStorage.setItem('x-hif-dliq', v);
                        sessionStorage.setItem('x-hif-dliq', v);
                    }
                    if (lk === 'x-hif-leim' && v) {
                        localStorage.setItem('x-hif-leim', v);
                        sessionStorage.setItem('x-hif-leim', v);
                    }
                } catch(e) {}
            }
            // 1. fetch wrapper
            var origFetch = window.fetch;
            if (origFetch) {
                window.fetch = function(url, options) {
                    try {
                        var opts = options || {};
                        var headers = opts.headers;
                        try {
                            var names = [];
                            if (headers instanceof Headers) { headers.forEach(function(v,k){ names.push(k); }); }
                            else if (Array.isArray(headers)) { headers.forEach(function(it){ if(it&&it.length===2) names.push(it[0]); }); }
                            else if (headers && typeof headers === 'object') { for (var kk in headers) { names.push(kk); } }
                            if (names.length) console.log('[KatyaHif] fetch hdrs=[' + names.join(',') + '] url=' + (('' + url).slice(0, 60)));
                        } catch(e) {}
                        if (headers) {
                            if (headers instanceof Headers) {
                                save('x-hif-dliq', headers.get('x-hif-dliq'));
                                save('x-hif-leim', headers.get('x-hif-leim'));
                            } else if (Array.isArray(headers)) {
                                headers.forEach(function(item) {
                                    if (item && item.length === 2) save(item[0], item[1]);
                                });
                            } else if (typeof headers === 'object') {
                                for (var k in headers) {
                                    if (Object.prototype.hasOwnProperty.call(headers, k)) {
                                        save(k, headers[k]);
                                    }
                                }
                            }
                        }
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };
            }
            // 2. XHR setRequestHeader wrapper
            var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
            if (origSetHeader) {
                XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
                    try {
                        save(header, value);
                    } catch(e) {}
                    return origSetHeader.apply(this, arguments);
                };
            }
        })();
    """

/**
 * Fallback injection for pages that are already loaded (document-start hook is
 * registered in the WebView factory via WebViewCompat.addDocumentStartJavaScript).
 */
private fun installHifHook(view: android.webkit.WebView?) {
    view?.evaluateJavascript(HIF_HOOK_JS, null)
}

/**
 * Two-step sign-in automation for chat.deepseek.com.
 *
 * DeepSeek's form is a React app: writing `input.value = 'x'` does nothing visible
 * (React keeps its own value tracker), and the email/password fields live on separate
 * steps. This script:
 *
 *  1. fills the email field via the native HTMLInputElement value setter and fires
 *     real `input`/`change` events (React sees those),
 *  2. clicks the continue button,
 *  3. waits for the password step and fills it the same way,
 *  4. clicks the login button.
 *
 * It also enforces `dir=ltr` + left alignment on every input on each tick, which
 * fixes the reverse RTL caret problem for fields DeepSeek renders late.
 * Progress is exposed on `window.__katyaAuthStep` ("email", "continue", "password",
 * "login", "wait", ...) and the loop stops itself once we leave the sign-in page.
 */
private fun buildAutoLoginJs(email: String, password: String): String {
    val emailJs = email.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    val passJs = password.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    return """
        (function() {
            if (window.__katyaAP) { return; }
            var EMAIL = '$emailJs';
            var PASS = '$passJs';
            function setVal(el, v) {
                var proto = (el instanceof HTMLTextAreaElement)
                    ? HTMLTextAreaElement.prototype
                    : HTMLInputElement.prototype;
                var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                setter.call(el, v);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }
            function ltr(el) {
                if (!el) return;
                el.setAttribute('dir', 'ltr');
                el.style.direction = 'ltr';
                el.style.textAlign = 'left';
                el.style.unicodeBidi = 'isolate';
            }
            function visible(el) {
                try {
                    if (!el || el.readOnly || el.disabled) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && el.offsetParent !== null;
                } catch (e) { return false; }
            }
            function firstVisible(sels) {
                var out = null;
                for (var i = 0; i < sels.length; i++) {
                    var nodes = document.querySelectorAll(sels[i]);
                    for (var j = 0; j < nodes.length; j++) {
                        if (visible(nodes[j])) return nodes[j];
                    }
                }
                return null;
            }
            function findBtn(txts) {
                var els = document.querySelectorAll(
                    'button, [role="button"], input[type="submit"], .ds-button, a[class*="button"]'
                );
                var best = null;
                for (var i = 0; i < els.length; i++) {
                    var el = els[i];
                    if (!visible(el)) continue;
                    var t = ((el.innerText || el.value || el.textContent || '') + '').trim().toLowerCase();
                    for (var j = 0; j < txts.length; j++) {
                        if (t.indexOf(txts[j]) >= 0) { return el; }
                    }
                }
                return null;
            }
            window.__katyaAP = setInterval(function() {
                try {
                    var em = firstVisible([
                        'input[type="email"]',
                        'input[name="email"]',
                        'input[autocomplete="username"]',
                        'input[autocomplete="email"]',
                        'input[type="text"]',
                    ]);
                    var pw = firstVisible([
                        'input[type="password"]',
                        'input[name="password"]',
                        'input[autocomplete="current-password"]',
                    ]);
                    ltr(em); ltr(pw);

                    var emVal = (em && em.value || '').trim();
                    var pwVal = (pw && pw.value || '').trim();

                    if (em && !emVal) {
                        setVal(em, EMAIL);
                        window.__katyaAuthStep = 'email';
                        return;
                    }
                    if (em && emVal && !pw) {
                        var cbtn = findBtn(['продолжить', 'continue', 'далее', 'next', 'дальше']);
                        if (cbtn) { cbtn.click(); window.__katyaAuthStep = 'continue'; }
                        else { window.__katyaAuthStep = 'look-continue'; }
                        return;
                    }
                    if (pw && !pwVal) {
                        setVal(pw, PASS);
                        window.__katyaAuthStep = 'password';
                        return;
                    }
                    if (pw && pwVal && em && emVal) {
                        var lbtn = findBtn(['войти', 'вход', 'log in', 'sign in', 'submit', 'вход на сайт']);
                        if (lbtn) { lbtn.click(); window.__katyaAuthSubmitted = true; window.__katyaAuthStep = 'login'; }
                        else { window.__katyaAuthStep = 'look-login'; }
                        return;
                    }
                    window.__katyaAuthStep = 'wait';
                } catch (e) {
                    window.__katyaAuthStep = 'err:' + ((e && e.message) || e);
                    console.log('DeepSeekAuth autopilot error: ' + e);
                }
                if (window.__katyaAuthSubmitted &&
                    location.pathname && location.pathname.indexOf('sign_in') < 0) {
                    clearInterval(window.__katyaAP);
                    window.__katyaAP = null;
                    window.__katyaAuthStep = 'done';
                }
            }, 1200);
        })();
    """.trimIndent()
}
