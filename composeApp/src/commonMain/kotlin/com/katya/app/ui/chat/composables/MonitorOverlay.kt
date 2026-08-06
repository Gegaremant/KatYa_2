package com.katya.app.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.katya.app.data.MonitorOverlayMode
import com.katya.app.monitor.MonitorStats
import com.katya.app.data.ServiceEntry

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(4.dp),
    ) {
        if (stats.error != null) {
            Text(
                text = "Monitor Error: ${stats.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
            )
        } else if (!stats.isRunning) {
            Text(
                text = "Starting Monitor...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
            )
        } else {
            if (mode == MonitorOverlayMode.SHORT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val katyaStatus = systemStatus ?: if (isProcessing) "Думаю..." else "Ожидание"
                        Text(
                            text = katyaStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                        
                        val networkStatus = if (selectedService?.serviceId == "srv-llm") {
                            stats.srvShort ?: "Srv: N/A"
                        } else {
                            val serviceName = selectedService?.serviceName ?: "Auto"
                            "API: Connected to $serviceName"
                        }
                        Text(
                            text = networkStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (mode == MonitorOverlayMode.FULL) {
                // Full mode: Scrollable box
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
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
