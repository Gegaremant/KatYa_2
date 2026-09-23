package com.katya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

@Composable
fun ServersContent(
    appSettings: AppSettings = koinInject(),
) {
    val scope = rememberCoroutineScope()

    // Connection Mode
    var connectionMode by remember { mutableStateOf(appSettings.getActiveConnectionMode()) }

    // Device Status
    var showDeviceStatus by remember { mutableStateOf(appSettings.isShowDeviceStateEnabled()) }

    // Connection Status
    var showConnectionStatus by remember { mutableStateOf(appSettings.isShowConnectionStateEnabled()) }

    // Voice thoughts

    val isVoiceResponseEnabled = appSettings.isVoiceResponseEnabled()

    // Logging
    var isLoggingEnabled by remember { mutableStateOf(appSettings.isLoggingEnabled()) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showRootLogsDialog by remember { mutableStateOf(false) }
    var logFilePath by remember { mutableStateOf(appSettings.getLogFilePath() ?: "") }

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
            val vlessChecked = connectionMode == "VLESS"
            ToggleableHeadline(
                title = "VLESS Прокси",
                description = "Использовать прокси-сервер VLESS",
                checked = vlessChecked,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        connectionMode = "VLESS"
                        appSettings.setActiveConnectionMode("VLESS")
                        // The proxy manager, the global proxy selector and the daemon all
                        // gate on vless_enabled — the connection mode alone leaves the
                        // tunnel permanently "disabled" in their eyes.
                        appSettings.setVlessEnabled(true)
                    } else {
                        connectionMode = "NONE"
                        appSettings.setActiveConnectionMode("NONE")
                        appSettings.setVlessEnabled(false)
                    }
                    // Restart the daemon so the VLESS proxy manager actually starts (or,
                    // when toggled off, stops) the tunnel. Without this the flag was set
                    // but no process ever listened on 127.0.0.1:10809 — requests routed
                    // into a dead port and VLESS "не подключался".
                    scope.launch {
                        val daemon = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.DaemonController>()
                        daemon.start()
                    }
                },
            )

            // Состояние списка поднимаем из-под AnimatedVisibility, чтобы индикатор ниже
            // видел актуальные прокси (и после добавления нового — тоже).
            val proxiesStr = appSettings.getVlessProxyProfilesJson()
            var proxies by remember {
                mutableStateOf(
                    try {
                        Json.decodeFromString<List<VlessProxyProfile>>(proxiesStr)
                    } catch (e: Exception) {
                        emptyList()
                    },
                )
            }
            var activeProxyId by remember { mutableStateOf(appSettings.getActiveVlessProxyId()) }

            AnimatedVisibility(
                visible = vlessChecked,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        proxies.forEach { proxy ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = (activeProxyId == proxy.id),
                                    onClick = {
                                        activeProxyId = proxy.id
                                        appSettings.setActiveVlessProxyId(proxy.id)
                                        appSettings.setVlessUri(proxy.uri)
                                    },
                                )
                                Text(proxy.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                IconButton(onClick = {
                                    proxies = proxies.filter { it.id != proxy.id }
                                    appSettings.setVlessProxyProfilesJson(Json.encodeToString(proxies))
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Add new
                        var newName by remember { mutableStateOf("") }
                        var newUri by remember { mutableStateOf("") }
                        var showSuccess by remember { mutableStateOf(false) }
                        var buttonText by remember { mutableStateOf("Сохранить") }
                        var isChecking by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    KaiOutlinedTextField(
                                        value = newName,
                                        onValueChange = {
                                            newName = it
                                            buttonText = "Сохранить"
                                        },
                                        placeholder = { Text("Название (например, NL-1)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    KaiOutlinedTextField(
                                        value = newUri,
                                        onValueChange = {
                                            newUri = it
                                            buttonText = "Сохранить" // Reset button text when input changes
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
                                        val isValidUri = newUri.startsWith("vless://") || newUri.startsWith("http://") || newUri.startsWith("https://")
                                        if (newName.isNotBlank() && newUri.isNotBlank() && isValidUri && !isChecking) {
                                            isChecking = true
                                            buttonText = "Проверка..."
                                            scope.launch {
                                                val oldUri = appSettings.getVlessUri()
                                                val oldId = appSettings.getActiveVlessProxyId()
                                                val oldMode = appSettings.getActiveConnectionMode()
                                                val daemon = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.DaemonController>()

                                                appSettings.setActiveConnectionMode("VLESS")
                                                appSettings.setVlessEnabled(true)
                                                appSettings.setVlessUri(newUri)
                                                daemon.start()

                                                var connected = false
                                                for (i in 1..15) {
                                                    kotlinx.coroutines.delay(1000)
                                                    connected = com.katya.app.network.checkLocalProxyConnection()
                                                    if (connected) break
                                                }

                                                val id = "vless_${kotlin.random.Random.nextInt()}"
                                                proxies = proxies + VlessProxyProfile(id, newName, newUri)
                                                appSettings.setVlessProxyProfilesJson(kotlinx.serialization.json.Json.encodeToString(proxies))
                                                appSettings.setActiveVlessProxyId(id)

                                                if (connected) {
                                                    buttonText = "Сохранить"
                                                    showSuccess = true
                                                } else {
                                                    buttonText = "Нет связи (сохранено)"
                                                    showSuccess = true
                                                }
                                                newName = ""
                                                newUri = ""

                                                kotlinx.coroutines.delay(3000)
                                                showSuccess = false
                                                isChecking = false
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

            // Индикатор подключения туннеля — виден всегда на карточке VLESS.
            // Это общий транспорт для всего приложения (DeepSeek, Telegram,
            // «замедленные» сайты), поэтому живой статус нужен прямо тут.
            // Красный «не подключён» показываем только когда тумблер включён и есть
            // что подключать: без прокси и при выключенном режиме это не ошибка,
            // а нейтральное состояние — иначе ошибка кричит впустую.
            val vlessConnected by appSettings.isVlessConnectedFlow.collectAsState()
            val hasConfiguredProxy = proxies.isNotEmpty() || appSettings.getVlessUri().isNotBlank()
            val showError = vlessChecked && hasConfiguredProxy && !vlessConnected
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = when {
                        showError -> StatusColorError
                        vlessChecked && hasConfiguredProxy && vlessConnected -> StatusColorConnected
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !vlessChecked -> "VLESS выключен"
                        !hasConfiguredProxy -> "VLESS включён · прокси не заданы"
                        vlessConnected -> "VLESS-туннель подключён"
                        else -> "VLESS-туннель не подключён"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showError) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "проверка или ошибка",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Загрузки компонентов / «Альтернативные ссылки»
        SettingsCard {
            var altExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { altExpanded = !altExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Загрузки · Альтернативные ссылки",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (altExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (altExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    if (components.isEmpty()) {
                        Text(
                            "Нет компонентов",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    components.forEach { component ->
                        DownloadableComponentRow(
                            component = component,
                            isForThisDevice = component.abi == null || component.abi == deviceAbi,
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

        Spacer(Modifier.height(16.dp))

        // Local Servers
        SettingsCard {
            val localChecked = connectionMode == "LOCAL"
            ToggleableHeadline(
                title = "Локальные серверы (SSH)",
                description = "Подключение к домашнему серверу",
                checked = localChecked,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        connectionMode = "LOCAL"
                        appSettings.setActiveConnectionMode("LOCAL")
                    } else {
                        connectionMode = "NONE"
                        appSettings.setActiveConnectionMode("NONE")
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

                    var ip by remember { mutableStateOf(appSettings.getServerIp()) }
                    var port by remember { mutableStateOf(appSettings.getServerPort().toString()) }
                    var user by remember { mutableStateOf(appSettings.getServerUser()) }
                    var password by remember { mutableStateOf(appSettings.getServerPassword()) }
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
    isForThisDevice: Boolean,
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
            if (!isForThisDevice) {
                Text(
                    text = "другая ABI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
