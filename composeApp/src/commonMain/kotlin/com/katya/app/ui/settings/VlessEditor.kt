package com.katya.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katya.app.ui.KaiOutlinedTextField
import io.ktor.http.Url

@Composable
fun VlessEditor(
    initialName: String,
    initialUri: String,
    onSave: (name: String, uri: String) -> Unit,
    onCancel: () -> Unit,
    onCheckConnection: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var uri by remember(initialUri) { mutableStateOf(initialUri) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        KaiOutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название (например, NL-1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KaiOutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            label = { Text("vless://...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onCheckConnection(uri)
                    isChecking = true
                    checkResult = null
                },
                enabled = uri.isNotBlank() && !isChecking,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isChecking) "Проверка..." else "Проверить подключение")
            }

            Button(
                onClick = {
                    if (name.isNotBlank() && uri.isNotBlank()) {
                        onSave(name.trim(), uri.trim())
                    }
                },
                enabled = name.isNotBlank() && uri.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Сохранить")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("Отмена")
            }
        }

        if (checkResult != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = checkResult!!,
                color = if (checkResult!!.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

suspend fun checkVlessConnection(uriString: String): String {
    return try {
        val url = Url(uriString)
        if (url.protocol.name != "vless") {
            "❌ Неверный протокол. Ожидается vless://"
        } else if (url.host.isBlank() || url.port <= 0) {
            "❌ Не удалось извлечь хост или порт из URI"
        } else {
            "✅ URI корректен: ${url.host}:${url.port}"
        }
    } catch (e: Exception) {
        "❌ Ошибка парсинга URI: ${e.message}"
    }
}
