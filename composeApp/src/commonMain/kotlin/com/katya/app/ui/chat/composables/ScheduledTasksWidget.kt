package com.katya.app.ui.chat.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.katya.app.data.ScheduledTask
import com.katya.app.data.TaskStatus
import com.katya.app.data.TaskTrigger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun ScheduledTask.timeLabel(): String = when (trigger) {
    TaskTrigger.CRON -> "Повтор: ${cron ?: "—"}"

    TaskTrigger.HEARTBEAT -> "Каждое сердцебиение"

    TaskTrigger.TIME -> {
        val dt = scheduledAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val date = "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year}"
        val time = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        "$date $time"
    }
}

/**
 * Compact live list of scheduled tasks shown at the top of the chat. Rendered
 * only while at least one task is still pending; every cancellation removes the
 * row immediately thanks to the reactive TaskStore flow feed in ChatViewModel.
 */
@Composable
fun ScheduledTasksWidget(
    tasks: List<ScheduledTask>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = tasks.filter {
        it.status == TaskStatus.PENDING
    }
    if (pending.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "Запланированные задачи · ${pending.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            pending.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = task.timeLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    IconButton(onClick = { onCancel(task.id) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Отменить задачу",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
