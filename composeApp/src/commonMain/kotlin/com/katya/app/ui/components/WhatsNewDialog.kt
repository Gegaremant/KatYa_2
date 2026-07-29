package com.katya.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katya.app.data.AppSettings
import com.katya.app.inference.getTotalMemoryBytes
import org.koin.compose.koinInject

@Composable
fun WhatsNewDialog(
    appSettings: AppSettings = koinInject(),
) {
    var showDialog by remember { mutableStateOf(!appSettings.settings.getBoolean("whats_new_2_0_seen", false)) }

    if (showDialog) {
        val totalMemory = getTotalMemoryBytes()
        val memoryGb = totalMemory / (1024L * 1024L * 1024L)
        val suggestedModelName = when {
            memoryGb >= 8 -> "gemma-2-2b-it-cpu-int8.bin"
            memoryGb >= 4 -> "gemma-2-2b-it-cpu-int4.bin"
            else -> "gemma-2-2b-it-cpu-int4.bin"
        }

        AlertDialog(
            onDismissRequest = {
                appSettings.settings.putBoolean("whats_new_2_0_seen", true)
                showDialog = false
            },
            title = {
                Text("Обновление Екатерина Андрюховна 2.0 \uD83E\uDD16")
            },
            text = {
                Column {
                    Text("Катя стала еще надежнее!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("В этом релизе реализованы Точные Будильники для стабильной работы Heartbeat в фоне. Также добавлен удобный и понятный механизм выдачи прав при старте приложения.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("В телефоне обнаружено ${memoryGb}GB RAM. Если вы используете локальный движок, рекомендуем модель $suggestedModelName.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appSettings.settings.putBoolean("whats_new_2_0_seen", true)
                        showDialog = false
                    },
                ) {
                    Text("Понятно, спасибо!")
                }
            },
        )
    }
}
