package com.katya.app.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katya.app.data.AppSettings
import com.katya.app.data.MonitorOverlayMode
import com.katya.app.data.ServiceEntry
import com.katya.app.monitor.MonitorStats
import org.koin.compose.koinInject

@Composable
fun MonitorOverlay(
    mode: MonitorOverlayMode,
    stats: MonitorStats,
    selectedService: ServiceEntry?,
    isProcessing: Boolean,
    systemStatus: String?,
    modifier: Modifier = Modifier,
) {
    if (mode == MonitorOverlayMode.OFF) return

    // VLESS — общий транспорт приложения (DeepSeek, Telegram, поиск на
    // «замедленных» ресурсах). Показываем живое состояние туннеля в строке
    // статуса всегда, когда VLESS выбран режимом подключения.
    val appSettings: AppSettings = koinInject()
    val vlessConnected by appSettings.isVlessConnectedFlow.collectAsState()
    val vlessMode = appSettings.getActiveConnectionMode() == "VLESS"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(4.dp),
    ) {
        val katyaStatus = systemStatus ?: if (isProcessing) "Думаю..." else "Ожидание"

        val vlessStatusText = if (vlessConnected) "VLESS: подключён" else "VLESS: не подключён"

        if (mode == MonitorOverlayMode.SHORT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = katyaStatus,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )

                    val networkStatus = if (stats.error != null) {
                        "SSH Err: ${stats.error}"
                    } else if (!stats.isRunning) {
                        "Starting Monitor..."
                    } else if (selectedService?.serviceId == "srv-llm") {
                        stats.srvShort ?: "Srv: N/A"
                    } else {
                        val serviceName = selectedService?.serviceName ?: "Auto"
                        "API: Connected to $serviceName"
                    }

                    Text(
                        text = networkStatus,
                        color = if (stats.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )

                    if (vlessMode) {
                        Text(
                            text = vlessStatusText,
                            color = if (vlessConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (mode == MonitorOverlayMode.FULL) {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = katyaStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (vlessMode) {
                    Text(
                        text = vlessStatusText,
                        color = if (vlessConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (stats.error != null) {
                    Text(
                        text = "SSH Err: ${stats.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else if (!stats.isRunning) {
                    Text(
                        text = "Starting Monitor...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    Text(
                        text = stats.locShort ?: "Loc: N/A",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = stats.srvFull ?: "Srv: Waiting for data...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
