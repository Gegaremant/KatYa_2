package com.katya.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katya.app.tools.AppLogger

@Composable
fun AppLogsScreen() {
    val logs by AppLogger.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var filterText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text("Фильтр логов") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().height(500.dp)) {
            val filteredLogs = logs.filter { it.contains(filterText, ignoreCase = true) }
            items(filteredLogs.takeLast(500).size) { index ->
                val log = filteredLogs.takeLast(500).reversed()[index]
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(logs.joinToString("\n"))) },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Копировать")
            }
            Button(onClick = { AppLogger.clear() }) {
                Text("Очистить")
            }
        }
    }
}

