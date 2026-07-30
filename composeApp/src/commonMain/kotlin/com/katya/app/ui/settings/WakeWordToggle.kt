package com.katya.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.settings_wake_word
import katya.composeapp.generated.resources.settings_wake_word_description
import katya.composeapp.generated.resources.settings_wake_word_download_vosk
import katya.composeapp.generated.resources.settings_wake_word_trigger
import org.jetbrains.compose.resources.stringResource

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WakeWordToggle(
    isWakeWordEnabled: Boolean,
    onToggleWakeWord: (Boolean) -> Unit,
    wakeWordTrigger: String,
    onChangeWakeWordTrigger: (String) -> Unit,
    wakeWordModelLang: String,
    onChangeWakeWordModelLang: (String) -> Unit,
    isWakeWordVibrationEnabled: Boolean,
    onToggleWakeWordVibration: (Boolean) -> Unit,
    isWakeWordSoundEnabled: Boolean,
    onToggleWakeWordSound: (Boolean) -> Unit,
    isVoskDownloading: Boolean,
    voskDownloadProgress: Float?,
    isVoskReady: Boolean,
    onDownloadVosk: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_wake_word),
            description = stringResource(Res.string.settings_wake_word_description),
            checked = isWakeWordEnabled,
            onCheckedChange = onToggleWakeWord,
        )
        if (isWakeWordEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wakeWordTrigger,
                onValueChange = onChangeWakeWordTrigger,
                label = { Text(stringResource(Res.string.settings_wake_word_trigger)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = when (wakeWordModelLang) {
                        "ru" -> "Русская"
                        "en" -> "Английская"
                        else -> "Изменить на портале"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Язык модели (Model Language)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(text = { Text("Русская") }, onClick = {
                        onChangeWakeWordModelLang("ru")
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text("Английская") }, onClick = {
                        onChangeWakeWordModelLang("en")
                        expanded = false
                    })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(text = "Вибрация при активации", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = isWakeWordVibrationEnabled, onCheckedChange = onToggleWakeWordVibration)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(text = "Звук при активации", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = isWakeWordSoundEnabled, onCheckedChange = onToggleWakeWordSound)
            }

            if (!isVoskReady) {
                if (isVoskDownloading) {
                    LinearProgressIndicator(
                        progress = { voskDownloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Text(
                        text = "Загрузка: ${((voskDownloadProgress ?: 0f) * 100).toInt()}%",
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    TextButton(
                        onClick = onDownloadVosk,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(Res.string.settings_wake_word_download_vosk))
                    }
                }
            }
        }
    }
}
