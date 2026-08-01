package com.katya.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.katya.app.data.AppSettings
import com.katya.app.tools.AppLogger
import com.katya.app.tunnel.SshTunnelService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import com.katya.app.getDirectoryPath

@Composable
fun ServersContent(
    appSettings: AppSettings = koinInject(),
    tunnelService: SshTunnelService = koinInject(),
) {
    var ip by remember { mutableStateOf(appSettings.getServerIp()) }
    var port by remember { mutableStateOf(appSettings.getServerPort().toString()) }
    var user by remember { mutableStateOf(appSettings.getServerUser()) }
    var password by remember { mutableStateOf(appSettings.getServerPassword()) }

    var tunnelLocalPort by remember { mutableStateOf("11434") }
    var tunnelRemotePort by remember { mutableStateOf("11434") }

    var showSavedMessage by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoggingEnabled by remember { mutableStateOf(appSettings.isLoggingEnabled()) }
    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggingEnabled) {
        appSettings.setLoggingEnabled(isLoggingEnabled)
        AppLogger.isEnabled = isLoggingEnabled
    }

    val tunnelState by tunnelService.tunnelState.collectAsState()
    val scope = rememberCoroutineScope()

    val monitorOverlayMode by appSettings.monitorOverlayModeFlow.collectAsState()
    val onChangeMonitorOverlayMode: (com.katya.app.data.MonitorOverlayMode) -> Unit = { appSettings.setMonitorOverlayMode(it) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Настройки мониторинга серверов", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Настройте SSH доступы для удаленного мониторинга и управления серверами. Укажите IP-адрес, порт и учетные данные для подключения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP-адрес / Имя хоста") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { char -> char.isDigit() } },
            label = { Text("SSH Порт") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Имя пользователя SSH") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль SSH") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            appSettings.setServerIp(ip)
            appSettings.setServerPort(port.toIntOrNull() ?: 22)
            appSettings.setServerUser(user)
            appSettings.setServerPassword(password)
            showSavedMessage = true
        }) {
            Text("Сохранить настройки")
        }

        if (showSavedMessage) {
            Spacer(Modifier.height(8.dp))
            Text("Данные успешно сохранены!", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

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
                label = { Text("Локальный порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !tunnelState.isRunning,
            )
            OutlinedTextField(
                value = tunnelRemotePort,
                onValueChange = { tunnelRemotePort = it.filter { char -> char.isDigit() } },
                label = { Text("Удаленный порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !tunnelState.isRunning,
            )
        }
        var tunnelPersistentReconnect by remember { mutableStateOf(appSettings.isTunnelPersistentReconnectEnabled()) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Постоянное авто-восстановление", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Switch(
                checked = tunnelPersistentReconnect,
                onCheckedChange = {
                    tunnelPersistentReconnect = it
                    appSettings.setTunnelPersistentReconnectEnabled(it)
                },
                enabled = !tunnelState.isRunning,
            )
        }
        
        var autoBackupDir by remember { mutableStateOf(appSettings.getAutoBackupDirectory()) }
        
        val directoryPicker = rememberDirectoryPickerLauncher { directory ->
            if (directory != null) {
                val path = getDirectoryPath(directory)
                if (path != null) {
                    autoBackupDir = path
                    appSettings.setAutoBackupDirectory(path)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = autoBackupDir,
                onValueChange = { 
                    autoBackupDir = it
                    appSettings.setAutoBackupDirectory(it) 
                },
                label = { Text("Папка авто-бэкапа (введите полный путь)") },
                modifier = Modifier.weight(1f),
                enabled = !tunnelState.isRunning,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { directoryPicker.launch() },
                enabled = !tunnelState.isRunning,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Обзор")
            }
        }
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                if (tunnelState.isRunning) {
                    tunnelService.stopTunnel()
                } else {
                    val local = tunnelLocalPort.toIntOrNull() ?: 11434
                    val remote = tunnelRemotePort.toIntOrNull() ?: 11434
                    val sshPort = port.toIntOrNull() ?: 22
                    tunnelService.startTunnel(local, remote, ip, sshPort, user, password, tunnelPersistentReconnect)
                }
            }
        }) {
            Text(if (tunnelState.isRunning) "Остановить туннель" else "Поднять туннель")
        }

        if (tunnelState.message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(tunnelState.message, color = MaterialTheme.colorScheme.primary)
        }
        if (tunnelState.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("Ошибка: ${tunnelState.error}", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Мониторинг", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Режим оверлея", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    com.katya.app.data.MonitorOverlayMode.OFF to "Откл",
                    com.katya.app.data.MonitorOverlayMode.SHORT to "Кратко",
                    com.katya.app.data.MonitorOverlayMode.FULL to "Полная",
                ).forEach { (mode, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = monitorOverlayMode == mode,
                        onClick = { onChangeMonitorOverlayMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Логи", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Включить ведение логов", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = isLoggingEnabled, onCheckedChange = { isLoggingEnabled = it })
        }
        Button(onClick = { showLogsDialog = true }) {
            Text("Посмотреть логи")
        }

        if (showLogsDialog) {
            LogsDialog(onDismiss = { showLogsDialog = false })
        }
    }
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
