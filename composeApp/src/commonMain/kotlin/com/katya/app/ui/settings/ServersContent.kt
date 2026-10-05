package com.katya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katya.app.components.ComponentDownloadLauncher
import com.katya.app.components.ComponentType
import com.katya.app.components.ComponentsRepository
import com.katya.app.components.currentAbi
import com.katya.app.data.AppSettings
import com.katya.app.data.LocalServerProfile
import com.katya.app.data.VlessProxyProfile
import com.katya.app.db.DownloadableComponent
import com.katya.app.tools.AppLogger
import com.katya.app.ui.KaiOutlinedTextField
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.ic_arrow_drop_down
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
fun ServersContent(
    appSettings: AppSettings = koinInject(),
) {
    val scope = rememberCoroutineScope()

    // Connection Mode
    // Keyed on the stored value: an unkeyed remember() froze this at first composition,
    // so importing a config changed the mode in storage — the tunnel came up on the new
    // one — while the screen kept showing the old selection.
    val storedConnectionMode = appSettings.getActiveConnectionMode()
    var connectionMode by remember(storedConnectionMode) { mutableStateOf(storedConnectionMode) }

    // Device Status
    val storedShowDeviceStatus = appSettings.isShowDeviceStateEnabled()
    var showDeviceStatus by remember(storedShowDeviceStatus) { mutableStateOf(storedShowDeviceStatus) }

    // Connection Status
    val storedShowConnectionStatus = appSettings.isShowConnectionStateEnabled()
    var showConnectionStatus by remember(storedShowConnectionStatus) { mutableStateOf(storedShowConnectionStatus) }

    // Voice thoughts

    val isVoiceResponseEnabled = appSettings.isVoiceResponseEnabled()

    // Logging
    val storedLoggingEnabled = appSettings.isLoggingEnabled()
    var isLoggingEnabled by remember(storedLoggingEnabled) { mutableStateOf(storedLoggingEnabled) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showRootLogsDialog by remember { mutableStateOf(false) }
    val storedLogFilePath = appSettings.getLogFilePath() ?: ""
    var logFilePath by remember(storedLogFilePath) { mutableStateOf(storedLogFilePath) }

    // (LaunchedEffect already added above)

    LaunchedEffect(logFilePath) {
        if (logFilePath.isNotBlank()) {
            appSettings.setLogFilePath(logFilePath)
            AppLogger.setLogFilePath(logFilePath)
        } else {
            appSettings.setLogFilePath(null)
            AppLogger.setLogFilePath(null)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // VLESS Proxies
        SettingsCard {
            // Feedback #3: this was `connectionMode == "VLESS"`, while the manager that actually
            // runs the tunnel asks `isVlessEnabled()` — which is also true when only the
            // `vless_enabled` flag is set. A backup written before `active_connection_mode`
            // existed carries that flag alone, so after an import the tunnel came up
            // while this card read "Отключен" — and because the whole editor sits inside
            // `AnimatedVisibility(visible = vlessChecked)`, the configuration that was
            // really running could not be seen or edited. One source of truth: the same
            // getter the tunnel uses decides what the switch shows.
            // Local mirror of the flag, because `isVlessEnabled()` reads the settings store
            // directly and cannot drive recomposition on its own. Seeded from that same
            // getter and written whenever the switch is touched, so the card can never
            // contradict the tunnel.
            val storedVlessEnabled = appSettings.isVlessEnabled()
            var vlessChecked by remember(storedVlessEnabled) { mutableStateOf(storedVlessEnabled) }
            val vlessConnected by appSettings.isVlessConnectedFlow.collectAsState()

            // Состояние списка поднимаем из-под AnimatedVisibility, чтобы индикатор ниже
            // видел актуальные прокси (и после добавления нового — тоже).
            val proxiesStr = appSettings.getVlessProxyProfilesJson()
            val vlessUriValue = appSettings.getVlessUri()
            // Keyed on the stored JSON and the URI, same reason as connectionMode above.
            // Feedback 05.10: a restored configuration can exist only as the bare
            // `vless_uri` — `getVlessProfiles()` folds that into the list so the running
            // config is visible and editable instead of an empty, unmanageable card.
            var proxies by remember(proxiesStr, vlessUriValue) {
                mutableStateOf(appSettings.getVlessProfiles())
            }
            val storedActiveProxyId = appSettings.getActiveVlessProxyId()
            var activeProxyId by remember(storedActiveProxyId) { mutableStateOf(storedActiveProxyId) }

            // Пункт 2.4: статус подключаемого прокси живёт на уровне заголовка
            // «VLESS Прокси»: «Подключен» (зелёная галочка), «Проверка доступа»
            // (восклицательный знак), «Не доступен» (крест) или «Отключен».
            // + кнопка «проверка» и обратный отсчёт до следующей проверки (~12 с).
            var isCheckingNow by remember { mutableStateOf(false) }
            var secondsLeft by remember { mutableIntStateOf(0) }
            // The port probe only proves *something* answers on 127.0.0.1:10809.
            // The green tick additionally requires the VLESS manager's own verdict,
            // so a leftover process or another tunnel can't pose as a working VLESS.
            var localPortOk by remember { mutableStateOf(vlessConnected) }
            val statusReason by appSettings.vlessStatusReasonFlow.collectAsState()
            // Feedback (27.09 #1): hoisted above the probe loop so it can gate it.
            val hasAnyProxy = proxies.isNotEmpty() || appSettings.getVlessUri().isNotBlank()
            // Feedback 27.09 #2: this was `vlessConnected && localPortOk`, and the second
            // term could go stale — the local port probe is a 12s UI-side guess while the
            // manager re-checks through the tunnel every 2s and owns the flag. Requiring
            // both meant a card reading "Не доступен" while the tunnel was answering 200.
            val tunnelUp = vlessConnected

            val runProbe: suspend () -> Unit = {
                isCheckingNow = true
                localPortOk = com.katya.app.network.checkLocalProxyConnection()
                isCheckingNow = false
            }

            // Feedback (27.09 #1): with no proxy configured this loop still ran, probing a
            // port nothing was listening on every 12 seconds and showing a countdown for a
            // tunnel that cannot exist. There is nothing to check until a config exists.
            LaunchedEffect(vlessChecked, vlessConnected, hasAnyProxy) {
                if (!vlessChecked || !hasAnyProxy) {
                    secondsLeft = 0
                    return@LaunchedEffect
                }
                localPortOk = vlessConnected
                while (true) {
                    secondsLeft = 12
                    while (secondsLeft > 0) {
                        delay(1000)
                        secondsLeft -= 1
                    }
                    runProbe()
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VLESS Прокси",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))

                    val statusColor = when {
                        !vlessChecked -> MaterialTheme.colorScheme.onSurfaceVariant
                        !hasAnyProxy -> MaterialTheme.colorScheme.onSurfaceVariant
                        isCheckingNow -> StatusColorChecking
                        tunnelUp -> StatusColorConnected
                        else -> StatusColorError
                    }
                    val statusIcon = when {
                        !vlessChecked -> null
                        !hasAnyProxy -> null
                        isCheckingNow -> Icons.Default.Warning
                        tunnelUp -> Icons.Default.CheckCircle
                        else -> Icons.Default.Cancel
                    }
                    val statusText = when {
                        !vlessChecked -> "Отключен"
                        !hasAnyProxy -> "Прокси не заданы"
                        isCheckingNow -> "Проверка доступности…"
                        tunnelUp -> "Подключен"
                        // The manager records *why* ("Туннель не отвечает (попытка N)");
                        // it was collected here and never rendered, so the card could only
                        // ever say "Не доступен" — the conflict the user could not see.
                        statusReason.isNotBlank() -> "Не доступен: $statusReason"
                        else -> "Не доступен"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (statusIcon != null) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                        )
                        // The probe no longer decides the status, but a disagreement is
                        // itself information: the tunnel answers while the local port does
                        // not, which usually means something else holds 10809.
                        if (tunnelUp && !localPortOk) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "локальный порт не отвечает",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (vlessChecked && hasAnyProxy) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "проверка",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    scope.launch { runProbe() }
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isCheckingNow) "…" else "через $secondsLeft с",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Why the tunnel is not up. Without it the card only said "Не
                    // доступен" while the real cause (sandbox, root, xray) was
                    // buried in the log.
                    if (vlessChecked && hasAnyProxy && !tunnelUp && statusReason.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = statusReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColorError,
                        )
                    }
                }
                Switch(
                    checked = vlessChecked,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            connectionMode = "VLESS"
                            appSettings.setActiveConnectionMode("VLESS")
                            // The proxy manager, the global proxy selector and the daemon all
                            // gate on vless_enabled — the connection mode alone leaves the
                            // tunnel permanently "disabled" in their eyes.
                            appSettings.setVlessEnabled(true)
                            vlessChecked = true
                            // Restart the daemon so the VLESS proxy manager actually starts
                            // the tunnel. Without this the flag was set but nothing ever
                            // listened on 127.0.0.1:10809.
                            scope.launch {
                                val daemon = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.DaemonController>()
                                daemon.start()
                            }
                        } else {
                            connectionMode = "NONE"
                            appSettings.setActiveConnectionMode("NONE")
                            appSettings.setVlessEnabled(false)
                            vlessChecked = false
                            // Feedback #4: turning the switch off has to stop the tunnel, and
                            // `daemon.start()` only ever starts things. The manager's `start()`
                            // reads the flag and stops the transport, but on the daemon
                            // thread — so the card said "Отключен" while xray went on serving
                            // 10808/10809. Stop the service instead.
                            scope.launch {
                                val daemon = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.DaemonController>()
                                daemon.stop()
                            }
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = vlessChecked,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        // Состояние редактора (2.2/2.3).
                        var editTargetId by remember { mutableStateOf<String?>(null) }
                        var editName by remember { mutableStateOf("") }
                        var editUri by remember { mutableStateOf("") }
                        var showSuccess by remember { mutableStateOf(false) }
                        var isChecking by remember { mutableStateOf(false) }
                        var buttonText by remember { mutableStateOf("Сохранить") }

                        // Пункт 2.2: справа от радио-кнопки — название конфигурации,
                        // «карандаш» для редактирования/просмотра + удаление.
                        proxies.forEach { proxy ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    // Feedback 05.10: a restored configuration has no id in
                                    // `active_vless_proxy_id` — it is whichever URI is live.
                                    // Without this the row the tunnel actually runs on looked
                                    // unselected, so it read as "nothing is configured".
                                    selected = (activeProxyId == proxy.id) ||
                                        (activeProxyId.isBlank() && proxy.uri == vlessUriValue),
                                    onClick = {
                                        activeProxyId = proxy.id
                                        appSettings.setActiveVlessProxyId(proxy.id)
                                        appSettings.setVlessUri(proxy.uri)
                                    },
                                )
                                Text(
                                    text = proxy.name.ifBlank { proxy.uri.substringAfter('#', "Конфигурация") },
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(onClick = {
                                    editTargetId = proxy.id
                                    editName = proxy.name
                                    editUri = proxy.uri
                                    showSuccess = false
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Изменить",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    // Feedback 05.10: the orphan row is not in the stored
                                    // list, so filtering it and saving would resurrect it on
                                    // the next read — deleting it has to clear the field the
                                    // row stands for.
                                    val isOrphan = proxy.id == com.katya.app.data.AppSettings.ORPHAN_VLESS_PROFILE_ID
                                    proxies = proxies.filter { it.id != proxy.id }
                                    appSettings.setVlessProxyProfilesJson(
                                        Json.encodeToString(proxies.filter { it.id != com.katya.app.data.AppSettings.ORPHAN_VLESS_PROFILE_ID }),
                                    )
                                    if (isOrphan) {
                                        appSettings.setVlessUri("")
                                        activeProxyId = ""
                                        appSettings.setActiveVlessProxyId("")
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Пункт 2.3: при сохранённых конфигурациях поля и кнопку
                        // «Сохранить» прячем — вместо них ссылка «Добавить новый».
                        if (proxies.isNotEmpty() && editTargetId == null && !isChecking) {
                            TextButton(onClick = {
                                editTargetId = "new"
                                editName = ""
                                editUri = ""
                                showSuccess = false
                            }) {
                                Text("+ Добавить новый")
                            }
                        }

                        if (editTargetId != null || proxies.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    KaiOutlinedTextField(
                                        value = editName,
                                        onValueChange = {
                                            editName = it
                                            buttonText = "Сохранить"
                                        },
                                        placeholder = { Text("Название (например, NL-1)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    KaiOutlinedTextField(
                                        value = editUri,
                                        onValueChange = {
                                            editUri = it
                                            buttonText = "Сохранить"
                                        },
                                        placeholder = { Text("vless:// или http://...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (showSuccess) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Saved",
                                        tint = Color.Green,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        val isValidUri = editUri.startsWith("vless://") || editUri.startsWith("http://") || editUri.startsWith("https://")
                                        if (editName.isNotBlank() && editUri.isNotBlank() && isValidUri && !isChecking) {
                                            isChecking = true
                                            buttonText = "Проверка..."
                                            scope.launch {
                                                val daemon = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.DaemonController>()

                                                appSettings.setActiveConnectionMode("VLESS")
                                                appSettings.setVlessEnabled(true)
                                                vlessChecked = true
                                                appSettings.setVlessUri(editUri)
                                                daemon.start()

                                                var connected = false
                                                for (i in 1..15) {
                                                    kotlinx.coroutines.delay(1000)
                                                    connected = com.katya.app.network.checkLocalProxyConnection()
                                                    if (connected) break
                                                }

                                                val finalName = editName.trim().ifBlank {
                                                    editUri.substringAfter('#', "").ifBlank { "VLESS ${proxies.size + 1}" }
                                                }
                                                val targetEditId = editTargetId
                                                val isNewProfile = targetEditId == null || targetEditId == "new"
                                                val id = if (isNewProfile) {
                                                    "vless_${kotlin.random.Random.nextInt()}"
                                                } else {
                                                    targetEditId ?: "vless_${kotlin.random.Random.nextInt()}"
                                                }
                                                proxies = if (isNewProfile) {
                                                    proxies + VlessProxyProfile(id, finalName, editUri)
                                                } else {
                                                    proxies.map { if (it.id == targetEditId) it.copy(id = id, name = finalName, uri = editUri) else it }
                                                }
                                                appSettings.setVlessProxyProfilesJson(
                                                    // The orphan row must not be written back —
                                                    // saving it would duplicate the live URI.
                                                    kotlinx.serialization.json.Json.encodeToString(
                                                        proxies.filter { it.id != com.katya.app.data.AppSettings.ORPHAN_VLESS_PROFILE_ID },
                                                    ),
                                                )
                                                appSettings.setActiveVlessProxyId(id)

                                                if (connected) {
                                                    buttonText = "Сохранить"
                                                    showSuccess = true
                                                } else {
                                                    buttonText = "Нет связи (сохранено)"
                                                    showSuccess = true
                                                }
                                                isChecking = false

                                                kotlinx.coroutines.delay(3000)
                                                showSuccess = false
                                                editTargetId = null
                                                editName = ""
                                                editUri = ""
                                            }
                                        }
                                    },
                                    enabled = !isChecking,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(buttonText)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Local Servers
        SettingsCard {
            val localChecked = connectionMode == "LOCAL"
            // Feedback #4: resolved here, in composition, so switching the toggle off can
            // actually stop the tunnel.
            val localTunnelService = koinInject<com.katya.app.tunnel.SshTunnelService>()
            ToggleableHeadline(
                title = "Локальные серверы (SSH)",
                description = "Подключение к домашнему серверу",
                checked = localChecked,
                onCheckedChange = { isChecked ->
                    // Feedback #4: the switch has to stop the tunnel, not just forget the
                    // mode. `stopTunnel()` used to disconnect the SSH session and leave
                    // the reconnect loop running, so with persistent reconnect on it came
                    // straight back — "выключатель не выключает туннель по факту".
                    if (isChecked) {
                        connectionMode = "LOCAL"
                        appSettings.setActiveConnectionMode("LOCAL")
                    } else {
                        connectionMode = "NONE"
                        appSettings.setActiveConnectionMode("NONE")
                        scope.launch { localTunnelService.stopTunnel() }
                    }
                },
            )

            AnimatedVisibility(
                visible = localChecked,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Keyed on the stored values so an imported configuration actually shows
                    // up here instead of leaving the old (or empty) text on screen.
                    val storedIp = appSettings.getServerIp()
                    val storedPort = appSettings.getServerPort().toString()
                    val storedUser = appSettings.getServerUser()
                    val storedPassword = appSettings.getServerPassword()
                    var ip by remember(storedIp) { mutableStateOf(storedIp) }
                    var port by remember(storedPort) { mutableStateOf(storedPort) }
                    var user by remember(storedUser) { mutableStateOf(storedUser) }
                    var password by remember(storedPassword) { mutableStateOf(storedPassword) }
                    var passwordVisible by remember { mutableStateOf(false) }
                    var showSavedMessage by remember { mutableStateOf(false) }

                    val tunnelService = koinInject<com.katya.app.tunnel.SshTunnelService>()
                    val tunnelState by tunnelService.tunnelState.collectAsState()
                    var tunnelLocalPort by remember { mutableStateOf("11434") }
                    var tunnelRemotePort by remember { mutableStateOf("11434") }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            placeholder = { Text("IP сервера") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { char -> char.isDigit() } },
                            placeholder = { Text("SSH порт (обычно 22)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            placeholder = { Text("Имя пользователя SSH") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("Пароль SSH") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) androidx.compose.material.icons.Icons.Filled.Visibility else androidx.compose.material.icons.Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(image, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("Настройка SSH-туннеля", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Создание локального перенаправления портов через настроенный сервер.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tunnelLocalPort,
                                onValueChange = { tunnelLocalPort = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Локальный порт") },
                                modifier = Modifier.weight(1f),
                                enabled = !tunnelState.isRunning,
                            )
                            OutlinedTextField(
                                value = tunnelRemotePort,
                                onValueChange = { tunnelRemotePort = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Удаленный порт") },
                                modifier = Modifier.weight(1f),
                                enabled = !tunnelState.isRunning,
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(onClick = {
                            appSettings.setServerIp(ip)
                            appSettings.setServerPort(port.toIntOrNull() ?: 22)
                            appSettings.setServerUser(user)
                            appSettings.setServerPassword(password)

                            scope.launch {
                                if (tunnelState.isRunning) {
                                    tunnelService.stopTunnel()
                                }
                                val local = tunnelLocalPort.toIntOrNull() ?: 11434
                                val remote = tunnelRemotePort.toIntOrNull() ?: 11434
                                val sshPort = port.toIntOrNull() ?: 22
                                tunnelService.startTunnel(local, remote, ip, sshPort, user, password, appSettings.isTunnelPersistentReconnectEnabled())

                                showSavedMessage = true
                                delay(2000)
                                showSavedMessage = false
                            }
                        }) {
                            Text(if (tunnelState.isRunning) "Сохранить и перезапустить туннель" else "Сохранить настройки и поднять туннель")
                        }
                        if (showSavedMessage) {
                            Spacer(Modifier.height(8.dp))
                            Text("Данные успешно сохранены!", color = MaterialTheme.colorScheme.primary)
                        }

                        if (tunnelState.message.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(tunnelState.message, color = MaterialTheme.colorScheme.primary)
                        }
                        if (tunnelState.error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Ошибка: ${tunnelState.error}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Device status
        SettingsCard {
            ToggleableHeadline(
                title = "Показ состояния устройства",
                description = "Показывать статус батареи, CPU, RAM",
                checked = showDeviceStatus,
                onCheckedChange = {
                    showDeviceStatus = it
                    appSettings.setShowDeviceStateEnabled(it)
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Connection status
        SettingsCard {
            ToggleableHeadline(
                title = "Показ состояния подключения",
                description = "Отображать пинг и скорость интернета",
                checked = showConnectionStatus,
                onCheckedChange = {
                    showConnectionStatus = it
                    appSettings.setShowConnectionStateEnabled(it)
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Logging
        SettingsCard {
            ToggleableHeadline(
                title = "Включить ведение логов",
                description = "Записывать системные события",
                checked = isLoggingEnabled,
                onCheckedChange = {
                    isLoggingEnabled = it
                    appSettings.setLoggingEnabled(it)
                },
            )
            AnimatedVisibility(visible = isLoggingEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Button(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    ) {
                        Text("Посмотреть логи")
                    }
                    OutlinedButton(
                        onClick = { showRootLogsDialog = true },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(),
                    ) {
                        Text("Root-логи (запросы root-прав)")
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (showLogsDialog) {
            LogsDialog(onDismiss = { showLogsDialog = false })
        }

        if (showRootLogsDialog) {
            RootLogsDialog(onDismiss = { showRootLogsDialog = false })
        }
    }
}

@Composable
private fun DownloadableComponentRow(
    component: DownloadableComponent,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    val isDownloading = component.status == "downloading"
    val isInstalled = component.status == "installed"
    val isFailed = component.status == "failed"

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (isInstalled) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = component.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    isInstalled -> "установлен"
                    isDownloading -> "качается"
                    isFailed -> "ошибка"
                    else -> "нет"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Зачем этот компонент и что будет, если он не скачан (#5.1).
        Spacer(Modifier.height(4.dp))
        Text(
            text = componentDescription(component),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isDownloading && component.totalBytes > 0) {
            Spacer(Modifier.height(8.dp))
            val progress = (component.downloadedBytes.toFloat() / component.totalBytes).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(progress * 100).toInt()}% · ${component.downloadedBytes / (1024 * 1024)} МБ / ${component.totalBytes / (1024 * 1024)} МБ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        var urlValue by remember(component.id) { mutableStateOf(component.url) }
        val urlChanged = urlValue.trim() != component.url
        KaiOutlinedTextField(
            value = urlValue,
            onValueChange = { urlValue = it },
            placeholder = { Text("https://…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ссылки можно редактировать",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Одна кнопка: пока ссылку редактируют — она «Сохранить» (и возвращается
        // обратно после сохранения), иначе — «Скачать»/«Переустановить» (#5.3).
        Button(
            onClick = {
                if (urlChanged) {
                    onUrlChange(urlValue.trim())
                } else {
                    onDownload()
                }
            },
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    urlChanged -> "Сохранить"
                    isInstalled -> "Переустановить"
                    else -> "Скачать"
                },
            )
        }
    }
}

/** Короткое описание компонента для панели «Альтернативные ссылки»: зачем он нужен и что будет без него. */
private fun componentDescription(component: DownloadableComponent): String {
    val base = when (ComponentType.from(component.componentType)) {
        ComponentType.ROOTFS ->
            "Образ Linux-песочницы (proot): внутри запускаются FreeDeepSeek, VLESS и SSH-туннель. Без него эти функции не работают."
        ComponentType.NATIVE ->
            "Нативные бинарии proot/talloc/xray. Без них песочница и VLESS не запускаются."
        ComponentType.MODEL ->
            "Модель распознавания или озвучки речи. Без неё голосовые функции недоступны."
    }
    return if (component.abi != null && component.abi != currentAbi()) {
        "$base Ссылка для другого устройства — на этом планшете/телефоне не используется."
    } else {
        base
    }
}

@Composable
fun RootLogsDialog(onDismiss: () -> Unit) {
    val logs by AppLogger.rootLogs.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Root-логи (запросы root-прав)") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                items(logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                }) {
                    Text("Копировать")
                }
                TextButton(onClick = { AppLogger.clearRoot() }) {
                    Text("Очистить")
                }
            }
        },
    )
}

@Composable
fun LogsDialog(onDismiss: () -> Unit) {
    val logs by AppLogger.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Логи приложения") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                items(logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                }) {
                    Text("Копировать")
                }
                TextButton(onClick = { AppLogger.clear() }) {
                    Text("Очистить")
                }
            }
        },
    )
}

/**
 * Панель «Загрузки · Альтернативные ссылки».
 *
 * Переехала сюда из «Серверов» под карточку выбора песочницы: ссылки ведут на
 * rootfs/Proot/Xray, то есть ровно на то, чем наполняется песочница, и держать
 * их на отдельной вкладке было неудобно.
 */
@Composable
internal fun AlternativeLinksCard() {
    SettingsCard {
        var altExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { altExpanded = !altExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Скачать другие",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Feedback #7: one chevron everywhere — the app's own vector, rotated, so this
            // island matches the rest instead of swapping two glyph icons.
            Icon(
                imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                contentDescription = if (altExpanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = if (altExpanded) 180f else 0f },
            )
        }

        AnimatedVisibility(
            visible = altExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                val componentsRepository = koinInject<ComponentsRepository>()
                val components by componentsRepository.components.collectAsState()
                val launcher = koinInject<ComponentDownloadLauncher>()
                val deviceAbi = currentAbi()

                // Пункт 3: не показываем компоненты под чужой ABI — чтобы нельзя было
                // скачать несовместимую сборку. Остаются только подходящие (или без ABI).
                val compatibleComponents = components.filter { component ->
                    component.abi == null || component.abi == deviceAbi
                }

                if (components.isEmpty()) {
                    Text(
                        "Нет компонентов",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                compatibleComponents.forEach { component ->
                    DownloadableComponentRow(
                        component = component,
                        onUrlChange = { newUrl -> componentsRepository.updateUrl(component.id, newUrl) },
                        onDownload = { launcher.startDownload(component.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }

                Text(
                    "Здесь лежат ссылки на Debian, Proot, Xray и другие компоненты. " +
                        "В APK они больше не встроены — всё скачивается по запросу и " +
                        "видно в этой панели. Ссылки можно заменить на свои.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
