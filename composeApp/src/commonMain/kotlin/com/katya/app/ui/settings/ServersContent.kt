package com.katya.app.ui.settings

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
import com.katya.app.ui.components.SettingsCard
import com.katya.app.ui.components.ToggleableHeadline
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.katya.app.ui.components.KaiOutlinedTextField

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
            Text("VLESS Прокси", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
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
                            selected = (connectionMode == "VLESS" && activeProxyId == proxy.id),
                            onClick = {
                                activeProxyId = proxy.id
                                connectionMode = "VLESS"
                                appSettings.setActiveVlessProxyId(proxy.id)
                                appSettings.setActiveConnectionMode("VLESS")
                                appSettings.setVlessUri(proxy.uri) // Fallback support
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
        
        Spacer(Modifier.height(16.dp))
        
        // Local Servers
        SettingsCard {
            Text("Локальные серверы (SSH)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
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
                            selected = (connectionMode == "LOCAL" && activeServerId == server.id),
                            onClick = {
                                activeServerId = server.id
                                connectionMode = "LOCAL"
                                appSettings.setActiveLocalServerId(server.id)
                                appSettings.setActiveConnectionMode("LOCAL")
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
        
        Spacer(Modifier.height(16.dp))
        
        // Switches
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ToggleableHeadline(
                    title = "Постоянное авто-восстановление",
                    description = "Автоматически переподключаться при обрыве связи",
                    checked = autoRecovery,
                    onCheckedChange = { 
                        autoRecovery = it
                        appSettings.setConstantAutoRecoveryEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                ToggleableHeadline(
                    title = "Показ состояния устройства",
                    description = "Показывать статус батареи, CPU, RAM",
                    checked = showDeviceStatus,
                    onCheckedChange = { 
                        showDeviceStatus = it
                        appSettings.setShowDeviceStateEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                ToggleableHeadline(
                    title = "Показ состояния подключения",
                    description = "Отображать пинг и скорость интернета",
                    checked = showConnectionStatus,
                    onCheckedChange = { 
                        showConnectionStatus = it
                        appSettings.setShowConnectionStateEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                ToggleableHeadline(
                    title = "Показ и озвучивание размышлений",
                    description = "Катя будет проговаривать свои мысли вслух",
                    checked = voiceThoughts,
                    onCheckedChange = { 
                        voiceThoughts = it
                        appSettings.setShowAndVoiceThoughtsEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                ToggleableHeadline(
                    title = "Включить ведение логов",
                    description = "Записывать системные события",
                    checked = isLoggingEnabled,
                    onCheckedChange = { isLoggingEnabled = it }
                )
                if (isLoggingEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showLogsDialog = true }) {
                        Text("Посмотреть логи")
                    }
                }
            }
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
