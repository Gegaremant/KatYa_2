package com.katya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katya.app.data.AppSettings
import com.katya.app.data.LocalServerProfile
import com.katya.app.data.VlessProxyProfile
import com.katya.app.tools.AppLogger
import com.katya.app.ui.KaiOutlinedTextField
import org.koin.compose.koinInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Composable
fun ServersContent(
    appSettings: AppSettings = koinInject()
) {
    val scope = rememberCoroutineScope()
    
    // Connection Mode
    var connectionMode by remember { mutableStateOf(appSettings.getActiveConnectionMode()) }
    
    // Auto recovery
    var autoRecovery by remember { mutableStateOf(appSettings.isConstantAutoRecoveryEnabled()) }
    
    // Device Status
    var showDeviceStatus by remember { mutableStateOf(appSettings.isShowDeviceStateEnabled()) }
    
    // Connection Status
    var showConnectionStatus by remember { mutableStateOf(appSettings.isShowConnectionStateEnabled()) }
    
    // Voice thoughts
    var voiceThoughts by remember { mutableStateOf(appSettings.isShowAndVoiceThoughtsEnabled()) }
    
    // Logging
    var isLoggingEnabled by remember { mutableStateOf(appSettings.isLoggingEnabled()) }
    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggingEnabled) {
        appSettings.setLoggingEnabled(isLoggingEnabled)
        AppLogger.isEnabled = isLoggingEnabled
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
                    } else {
                        connectionMode = "NONE"
                        appSettings.setActiveConnectionMode("NONE")
                    }
                }
            )
            
            AnimatedVisibility(
                visible = vlessChecked,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    val proxiesStr = appSettings.getVlessProxyProfilesJson()
                    var proxies by remember { 
                        mutableStateOf(
                            try { Json.decodeFromString<List<VlessProxyProfile>>(proxiesStr) } 
                            catch (e: Exception) { emptyList() }
                        ) 
                    }
                    var activeProxyId by remember { mutableStateOf(appSettings.getActiveVlessProxyId()) }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        proxies.forEach { proxy ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = (activeProxyId == proxy.id),
                                    onClick = {
                                        activeProxyId = proxy.id
                                        appSettings.setActiveVlessProxyId(proxy.id)
                                        appSettings.setVlessUri(proxy.uri)
                                    }
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
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                KaiOutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    placeholder = { Text("Название (например, NL-1)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                KaiOutlinedTextField(
                                    value = newUri,
                                    onValueChange = { newUri = it },
                                    placeholder = { Text("vless://...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(onClick = {
                                if (newName.isNotBlank() && newUri.isNotBlank()) {
                                    val id = "vless_${kotlin.random.Random.nextInt()}"
                                    proxies = proxies + VlessProxyProfile(id, newName, newUri)
                                    appSettings.setVlessProxyProfilesJson(Json.encodeToString(proxies))
                                    newName = ""
                                    newUri = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
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
                }
            )
            
            AnimatedVisibility(
                visible = localChecked,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    val serversStr = appSettings.getLocalServerProfilesJson()
                    var servers by remember { 
                        mutableStateOf(
                            try { Json.decodeFromString<List<LocalServerProfile>>(serversStr) } 
                            catch (e: Exception) { emptyList() }
                        ) 
                    }
                    var activeServerId by remember { mutableStateOf(appSettings.getActiveLocalServerId()) }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        servers.forEach { server ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = (activeServerId == server.id),
                                    onClick = {
                                        activeServerId = server.id
                                        appSettings.setActiveLocalServerId(server.id)
                                        appSettings.setServerIp(server.ip)
                                    }
                                )
                                Text(server.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                IconButton(onClick = {
                                    servers = servers.filter { it.id != server.id }
                                    appSettings.setLocalServerProfilesJson(Json.encodeToString(servers))
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        // Add new
                        var newName by remember { mutableStateOf("") }
                        var newIp by remember { mutableStateOf("") }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                KaiOutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    placeholder = { Text("Название (Home Server)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                KaiOutlinedTextField(
                                    value = newIp,
                                    onValueChange = { newIp = it },
                                    placeholder = { Text("IP адрес") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(onClick = {
                                if (newName.isNotBlank() && newIp.isNotBlank()) {
                                    val id = "local_${kotlin.random.Random.nextInt()}"
                                    servers = servers + LocalServerProfile(id, newName, newIp)
                                    appSettings.setLocalServerProfilesJson(Json.encodeToString(servers))
                                    newName = ""
                                    newIp = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Auto recovery
        SettingsCard {
            ToggleableHeadline(
                title = "Постоянное авто-восстановление",
                description = "Автоматически переподключаться при обрыве связи",
                checked = autoRecovery,
                onCheckedChange = { 
                    autoRecovery = it
                    appSettings.setConstantAutoRecoveryEnabled(it)
                }
            )
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
                }
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
                }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Voice thoughts
        SettingsCard {
            ToggleableHeadline(
                title = "Показ и озвучивание размышлений",
                description = "Катя будет проговаривать свои мысли вслух",
                checked = voiceThoughts,
                onCheckedChange = { 
                    voiceThoughts = it
                    appSettings.setShowAndVoiceThoughtsEnabled(it)
                }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Logging
        SettingsCard {
            ToggleableHeadline(
                title = "Включить ведение логов",
                description = "Записывать системные события",
                checked = isLoggingEnabled,
                onCheckedChange = { isLoggingEnabled = it }
            )
            AnimatedVisibility(visible = isLoggingEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Button(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    ) {
                        Text("Посмотреть логи")
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
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

