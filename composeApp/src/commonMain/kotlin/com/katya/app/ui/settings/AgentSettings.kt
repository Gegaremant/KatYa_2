@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.katya.app.data.HeartbeatLogEntry
import com.katya.app.data.MemoryEntry
import com.katya.app.data.ScheduledTask
import com.katya.app.data.TaskStatus
import com.katya.app.data.TaskTrigger
import com.katya.app.ui.KaiOutlinedTextField
import com.katya.app.ui.components.SettingsListItem
import com.katya.app.ui.handCursor
import com.katya.app.ui.icons.Replay
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.default_soul
import katya.composeapp.generated.resources.execution_log_status_fail
import katya.composeapp.generated.resources.execution_log_status_ok
import katya.composeapp.generated.resources.settings_heartbeat_recent
import katya.composeapp.generated.resources.settings_memories
import katya.composeapp.generated.resources.settings_memories_all_title
import katya.composeapp.generated.resources.settings_memories_delete
import katya.composeapp.generated.resources.settings_memories_description
import katya.composeapp.generated.resources.settings_memories_edit_cancel
import katya.composeapp.generated.resources.settings_memories_edit_save
import katya.composeapp.generated.resources.settings_memories_edit_title
import katya.composeapp.generated.resources.settings_memories_show_all
import katya.composeapp.generated.resources.settings_scheduled_tasks
import katya.composeapp.generated.resources.settings_scheduled_tasks_cancel
import katya.composeapp.generated.resources.settings_scheduled_tasks_description
import katya.composeapp.generated.resources.settings_soul
import katya.composeapp.generated.resources.settings_soul_description
import katya.composeapp.generated.resources.settings_soul_reset
import katya.composeapp.generated.resources.settings_soul_reset_cancel
import katya.composeapp.generated.resources.settings_soul_reset_confirm
import katya.composeapp.generated.resources.settings_soul_save
import katya.composeapp.generated.resources.settings_task_details_consecutive_failures
import katya.composeapp.generated.resources.settings_task_details_created
import katya.composeapp.generated.resources.settings_task_details_last_result
import katya.composeapp.generated.resources.settings_task_details_next_run
import katya.composeapp.generated.resources.settings_task_details_no_heartbeat_runs
import katya.composeapp.generated.resources.settings_task_details_no_runs
import katya.composeapp.generated.resources.settings_task_details_on_every_heartbeat
import katya.composeapp.generated.resources.settings_task_details_schedule
import katya.composeapp.generated.resources.settings_task_details_scheduled_for
import katya.composeapp.generated.resources.settings_task_details_status
import katya.composeapp.generated.resources.settings_task_details_trigger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.time.Clock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton

@Composable
internal fun AgentContent(uiState: SettingsUiState, actions: SettingsActions) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStaggered = maxWidth >= 600.dp
        if (useStaggered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        SoulEditor(
                            soulText = uiState.soulText,
                            onSaveSoul = actions.onSaveSoul,
                        )
                    }
                    SettingsCard {
                        ScheduledTaskList(
                            tasks = uiState.scheduledTasks,
                            heartbeatLog = uiState.heartbeatLog,
                            onCancelTask = actions.onCancelTask,
                            onAddScheduledTask = actions.onAddScheduledTask,
                            onUpdateScheduledTask = actions.onUpdateScheduledTask,
                            isSchedulingEnabled = uiState.isSchedulingEnabled,
                            onToggleScheduling = actions.onToggleScheduling,
                        )
                    }
                    SettingsCard {
                        MemoryList(
                            memories = uiState.memories,
                            onDeleteMemory = actions.onDeleteMemory,
                            onUpdateMemory = actions.onUpdateMemory,
                            onAddMemory = actions.onAddMemory,
                            isMemoryEnabled = uiState.isMemoryEnabled,
                            onToggleMemory = actions.onToggleMemory,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        HeartbeatSection(
                            isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                            heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                            activeHoursStart = uiState.heartbeatActiveHoursStart,
                            activeHoursEnd = uiState.heartbeatActiveHoursEnd,
                            heartbeatPrompt = uiState.heartbeatPrompt,
                            heartbeatLog = uiState.heartbeatLog,
                            heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                            heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                            isRefreshing = uiState.isRefreshingHeartbeat,
                            onToggleHeartbeat = actions.onToggleHeartbeat,
                            onChangeInterval = actions.onChangeHeartbeatInterval,
                            onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                            onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                            onChangeHeartbeatService = actions.onChangeHeartbeatService,
                            onRefresh = actions.onRefreshHeartbeat,
                        )
                    }
                    if (uiState.showEmailToggle) {
                        SettingsCard {
                            EmailSection(
                                isEmailEnabled = uiState.isEmailEnabled,
                                emailAccounts = uiState.emailAccounts,
                                pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                                pendingCount = uiState.emailPendingCount,
                                syncStates = uiState.emailSyncStates,
                                refreshingAccountIds = uiState.refreshingEmailAccountIds,
                                onToggleEmail = actions.onToggleEmail,
                                onAddAccount = actions.onAddEmailAccount,
                                onRemoveAccount = actions.onRemoveEmailAccount,
                                onChangePollInterval = actions.onChangeEmailPollInterval,
                                onRefreshAccount = actions.onRefreshEmailAccount,
                            )
                        }
                    }
                    if (uiState.showSmsSection) {
                        SettingsCard {
                            SmsSection(
                                isSmsEnabled = uiState.isSmsEnabled,
                                permissionGranted = uiState.smsPermissionGranted,
                                pollIntervalMinutes = uiState.smsPollIntervalMinutes,
                                pendingCount = uiState.smsPendingCount,
                                syncState = uiState.smsSyncState,
                                isRefreshing = uiState.isRefreshingSms,
                                isSmsSendEnabled = uiState.isSmsSendEnabled,
                                sendPermissionGranted = uiState.smsSendPermissionGranted,
                                onToggleSms = actions.onToggleSms,
                                onChangePollInterval = actions.onChangeSmsPollInterval,
                                onRefresh = actions.onRefreshSms,
                                onToggleSmsSend = actions.onToggleSmsSend,
                            )
                        }
                    }
                    if (uiState.showNotificationsSection) {
                        SettingsCard {
                            NotificationsSection(
                                isEnabled = uiState.isNotificationsEnabled,
                                accessGranted = uiState.notificationListenerAccessGranted,
                                listenerBound = uiState.notificationListenerBound,
                                pendingCount = uiState.notificationPendingCount,
                                onToggle = actions.onToggleNotifications,
                                onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                                onClearPending = actions.onClearPendingNotifications,
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsCard {
                    SoulEditor(
                        soulText = uiState.soulText,
                        onSaveSoul = actions.onSaveSoul,
                    )
                }
                SettingsCard {
                    MemoryList(
                        memories = uiState.memories,
                        onDeleteMemory = actions.onDeleteMemory,
                        onUpdateMemory = actions.onUpdateMemory,
                        onAddMemory = actions.onAddMemory,
                        isMemoryEnabled = uiState.isMemoryEnabled,
                        onToggleMemory = actions.onToggleMemory,
                    )
                }
                SettingsCard {
                    ScheduledTaskList(
                        tasks = uiState.scheduledTasks,
                        heartbeatLog = uiState.heartbeatLog,
                        onCancelTask = actions.onCancelTask,
                        onAddScheduledTask = actions.onAddScheduledTask,
                        onUpdateScheduledTask = actions.onUpdateScheduledTask,
                        isSchedulingEnabled = uiState.isSchedulingEnabled,
                        onToggleScheduling = actions.onToggleScheduling,
                    )
                }
                SettingsCard {
                    HeartbeatSection(
                        isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                        heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                        activeHoursStart = uiState.heartbeatActiveHoursStart,
                        activeHoursEnd = uiState.heartbeatActiveHoursEnd,
                        heartbeatPrompt = uiState.heartbeatPrompt,
                        heartbeatLog = uiState.heartbeatLog,
                        heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                        heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                        isRefreshing = uiState.isRefreshingHeartbeat,
                        onToggleHeartbeat = actions.onToggleHeartbeat,
                        onChangeInterval = actions.onChangeHeartbeatInterval,
                        onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                        onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                        onChangeHeartbeatService = actions.onChangeHeartbeatService,
                        onRefresh = actions.onRefreshHeartbeat,
                    )
                }
                if (uiState.showEmailToggle) {
                    SettingsCard {
                        EmailSection(
                            isEmailEnabled = uiState.isEmailEnabled,
                            emailAccounts = uiState.emailAccounts,
                            pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                            pendingCount = uiState.emailPendingCount,
                            syncStates = uiState.emailSyncStates,
                            refreshingAccountIds = uiState.refreshingEmailAccountIds,
                            onToggleEmail = actions.onToggleEmail,
                            onAddAccount = actions.onAddEmailAccount,
                            onRemoveAccount = actions.onRemoveEmailAccount,
                            onChangePollInterval = actions.onChangeEmailPollInterval,
                            onRefreshAccount = actions.onRefreshEmailAccount,
                        )
                    }
                }
                if (uiState.showSmsSection) {
                    SettingsCard {
                        SmsSection(
                            isSmsEnabled = uiState.isSmsEnabled,
                            permissionGranted = uiState.smsPermissionGranted,
                            pollIntervalMinutes = uiState.smsPollIntervalMinutes,
                            pendingCount = uiState.smsPendingCount,
                            syncState = uiState.smsSyncState,
                            isRefreshing = uiState.isRefreshingSms,
                            isSmsSendEnabled = uiState.isSmsSendEnabled,
                            sendPermissionGranted = uiState.smsSendPermissionGranted,
                            onToggleSms = actions.onToggleSms,
                            onChangePollInterval = actions.onChangeSmsPollInterval,
                            onRefresh = actions.onRefreshSms,
                            onToggleSmsSend = actions.onToggleSmsSend,
                        )
                    }
                }
                if (uiState.showNotificationsSection) {
                    SettingsCard {
                        NotificationsSection(
                            isEnabled = uiState.isNotificationsEnabled,
                            accessGranted = uiState.notificationListenerAccessGranted,
                            listenerBound = uiState.notificationListenerBound,
                            pendingCount = uiState.notificationPendingCount,
                            onToggle = actions.onToggleNotifications,
                            onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                            onClearPending = actions.onClearPendingNotifications,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoulEditor(
    soulText: String,
    onSaveSoul: (String) -> Unit,
) {
    val localizedDefault = stringResource(Res.string.default_soul)
    val displayText = soulText.ifEmpty { localizedDefault }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_soul),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (soulText.isNotEmpty()) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = stringResource(Res.string.settings_soul_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.settings_soul_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
            minLines = 8,
            maxLines = 8,
            label = {
                Text(
                    stringResource(Res.string.settings_soul),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
        )

        Text(
            text = "${editedText.length}/$maxChars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        if (hasChanges) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveSoul(editedText.trim()) },
                modifier = Modifier.align(CenterHorizontally).handCursor(),
            ) {
                Text(stringResource(Res.string.settings_soul_save))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_soul_reset)) },
            text = { Text(stringResource(Res.string.settings_soul_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onSaveSoul("")
                        editedText = localizedDefault
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryList(
    memories: ImmutableList<MemoryEntry>,
    onDeleteMemory: (String) -> Unit,
    onUpdateMemory: (String, String) -> Unit,
    onAddMemory: (String, String) -> Unit,
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
) {
    var showAllDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }

    val sortedMemories = remember(memories) {
        memories.sortedByDescending { it.updatedAt }.toImmutableList()
    }
    val previewMemories = remember(sortedMemories) { sortedMemories.take(5).toImmutableList() }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_memories),
            description = stringResource(Res.string.settings_memories_description),
            checked = isMemoryEnabled,
            onCheckedChange = onToggleMemory,
        )
        Spacer(Modifier.height(12.dp))

        if (isMemoryEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить запись")
                }
            }
            Spacer(Modifier.height(8.dp))

            previewMemories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = stringResource(Res.string.settings_memories_delete),
                    subtitleMaxLines = 3,
                    onClick = { editingMemory = memory },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (sortedMemories.size > previewMemories.size) {
                OutlinedButton(
                    onClick = { showAllDialog = true },
                    modifier = Modifier.align(CenterHorizontally).handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_show_all, sortedMemories.size))
                }
            }
        }
    }

    if (showAllDialog) {
        AllMemoriesSheet(
            memories = sortedMemories,
            onDismiss = { showAllDialog = false },
            onDeleteMemory = onDeleteMemory,
            onEditMemory = { editingMemory = it },
        )
    }

    if (showAddDialog) {
        AddMemorySheet(
            onDismiss = { showAddDialog = false },
            onSave = { k, c ->
                onAddMemory(k, c)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { memory ->
        EditMemorySheet(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { newContent ->
                onUpdateMemory(memory.key, newContent)
                editingMemory = null
            },
        )
    }
}

@Composable
private fun AllMemoriesSheet(
    memories: ImmutableList<MemoryEntry>,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onEditMemory: (MemoryEntry) -> Unit,
) {
    val deleteContentDescription = stringResource(Res.string.settings_memories_delete)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_all_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            memories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = deleteContentDescription,
                    subtitleMaxLines = 3,
                    onClick = { onEditMemory(memory) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditMemorySheet(
    memory: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by remember(memory.key) { mutableStateOf(memory.content) }
    val hasChanges = content != memory.content && content.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = memory.key,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = content,
                onValueChange = { content = it },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onSave(content.trim()) },
                    enabled = hasChanges,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScheduledTaskList(
    tasks: ImmutableList<ScheduledTask>,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onCancelTask: (String) -> Unit,
    onAddScheduledTask: (String, String, Long, String?, TaskTrigger) -> Unit,
    onUpdateScheduledTask: (ScheduledTask) -> Unit,
    isSchedulingEnabled: Boolean,
    onToggleScheduling: (Boolean) -> Unit,
) {
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var isAddingTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_scheduled_tasks),
            description = stringResource(Res.string.settings_scheduled_tasks_description),
            checked = isSchedulingEnabled,
            onCheckedChange = onToggleScheduling,
        )
        Spacer(Modifier.height(12.dp))

        val onEveryHeartbeat = stringResource(Res.string.settings_task_details_on_every_heartbeat)
        if (isSchedulingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { isAddingTask = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить задачу")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (tasks.isNotEmpty()) {
                val visibleTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
                if (visibleTasks.isEmpty()) {
                    Text(
                        text = "Нет активных задач",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    visibleTasks.forEach { task ->
                        val subtitle = when (task.trigger) {
                            TaskTrigger.HEARTBEAT -> "${task.status} - $onEveryHeartbeat"
        
                            TaskTrigger.CRON -> "${task.status} - ${task.cron?.let { describeCron(it) } ?: "cron"}"
        
                            TaskTrigger.TIME -> {
                                val instant = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                                val zone = TimeZone.currentSystemDefault()
                                val scheduledTime = instant.toLocalDateTime(zone)
                                val offset = zone.offsetAt(instant)
                                "${task.status} - $scheduledTime $offset"
                            }
                        }
                        SettingsListItem(
                            title = task.description,
                            subtitle = subtitle,
                            onClick = { selectedTaskId = task.id },
                            onDelete = { onCancelTask(task.id) },
                            deleteContentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    val selectedTask = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
    if (selectedTask != null) {
        TaskDetailsSheet(
            task = selectedTask,
            heartbeatLog = heartbeatLog,
            onDismiss = { selectedTaskId = null },
            onEditClick = { taskToEdit ->
                editingTask = taskToEdit
            }
        )
    }

    if (isAddingTask) {
        AddEditTaskSheet(
            task = null,
            onDismiss = { isAddingTask = false },
            onSave = { desc, pr, time, cron, trig ->
                onAddScheduledTask(desc, pr, time, cron, trig)
                isAddingTask = false
            }
        )
    }

    if (editingTask != null) {
        AddEditTaskSheet(
            task = editingTask,
            onDismiss = { editingTask = null },
            onSave = { desc, pr, time, cron, trig ->
                val updated = editingTask!!.copy(
                    description = desc,
                    prompt = pr,
                    scheduledAtEpochMs = time,
                    cron = cron,
                    trigger = trig
                )
                onUpdateScheduledTask(updated)
                editingTask = null
            }
        )
    }
}

@Composable
private fun TaskDetailsSheet(
    task: ScheduledTask,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onDismiss: () -> Unit,
    onEditClick: (ScheduledTask) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onEditClick(task)
                    },
                    modifier = Modifier.handCursor()
                ) {
                    Text("Редактировать")
                }
            }
            Spacer(Modifier.height(12.dp))

            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_trigger),
                value = task.trigger.name,
            )
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_status),
                value = task.status.name,
            )
            when (task.trigger) {
                TaskTrigger.TIME -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_scheduled_for),
                    value = formatTaskInstant(task.scheduledAtEpochMs),
                )

                TaskTrigger.CRON -> {
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_schedule),
                        value = task.cron?.let { describeCron(it) } ?: "cron",
                    )
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_next_run),
                        value = formatTaskInstant(task.scheduledAtEpochMs),
                    )
                }

                TaskTrigger.HEARTBEAT -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_schedule),
                    value = stringResource(Res.string.settings_task_details_on_every_heartbeat),
                )
            }
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_created),
                value = formatTaskInstant(task.createdAtEpochMs),
            )
            if (task.consecutiveFailures > 0) {
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_consecutive_failures),
                    value = task.consecutiveFailures.toString(),
                )
            }
            // The scheduler stores its retry/backoff phrasing in `lastResult` ("Failed at ...:
            // ... (retry after 120s backoff)"). Surface it so the user can see what the
            // scheduler is going to do next, not just what already happened.
            task.lastResult?.takeIf { it.isNotBlank() }?.let { result ->
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_last_result),
                    value = result,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.settings_heartbeat_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))

            if (task.trigger == TaskTrigger.HEARTBEAT) {
                // Heartbeat additions don't carry their own log — they fire as part of every
                // heartbeat run, so the heartbeat-wide log is the right surface.
                if (heartbeatLog.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_heartbeat_runs))
                } else {
                    heartbeatLog.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.error,
                        )
                    }
                }
            } else {
                if (task.recentExecutions.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_runs))
                } else {
                    task.recentExecutions.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.message,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TaskDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ExecutionLogRow(success: Boolean, timestampEpochMs: Long, message: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (success) stringResource(Res.string.execution_log_status_ok) else stringResource(Res.string.execution_log_status_fail),
            style = MaterialTheme.typography.labelSmall,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(36.dp),
        )
        Column {
            Text(
                text = formatTaskInstant(timestampEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTaskInstant(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val zone = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(zone)
    val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.day} $month ${local.year} ${local.hour}:$minute"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemorySheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val isValid = key.isNotBlank() && content.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Добавить запись в память",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = key,
                onValueChange = { key = it },
                label = { Text("Имя ключа (например, Любимый цвет)") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = content,
                onValueChange = { content = it },
                label = { Text("Содержимое памяти") },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isValid) {
                            onSave(key.trim(), content.trim())
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_save))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun parseLocalDateTime(str: String): Long? {
    return try {
        val parts = str.trim().split(' ')
        val dateParts = parts[0].split('-').map { it.toInt() }
        val timeParts = parts[1].split(':').map { it.toInt() }
        val localDateTime = kotlinx.datetime.LocalDateTime(
            dateParts[0], dateParts[1], dateParts[2],
            timeParts[0], timeParts[1], 0, 0
        )
        localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditTaskSheet(
    task: ScheduledTask?,
    onDismiss: () -> Unit,
    onSave: (description: String, prompt: String, scheduledAtEpochMs: Long, cron: String?, trigger: TaskTrigger) -> Unit,
) {
    var description by remember(task) { mutableStateOf(task?.description ?: "") }
    var prompt by remember(task) { mutableStateOf(task?.prompt ?: "") }
    var trigger by remember(task) { mutableStateOf(task?.trigger ?: TaskTrigger.TIME) }

    // TIME specific states
    var delayMinutes by remember { mutableStateOf(5) }
    var useManualTime by remember { mutableStateOf(task != null) }
    val initialTimeStr = remember {
        val instant = if (task != null) {
            Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
        } else {
            Clock.System.now()
        }
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val year = localDateTime.year
        val month = localDateTime.monthNumber.toString().padStart(2, '0')
        val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        "$year-$month-$day $hour:$minute"
    }
    var manualTimeString by remember { mutableStateOf(initialTimeStr) }

    // CRON specific states
    var cronString by remember(task) { mutableStateOf(task?.cron ?: "*/5 * * * *") }

    val isInputValid = description.isNotBlank() && prompt.isNotBlank() && when (trigger) {
        TaskTrigger.TIME -> {
            if (useManualTime) {
                parseLocalDateTime(manualTimeString) != null
            } else {
                true
            }
        }
        TaskTrigger.CRON -> cronString.isNotBlank()
        TaskTrigger.HEARTBEAT -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (task == null) "Создать новую задачу" else "Редактировать задачу",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание задачи") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Промпт для Кати") },
                minLines = 3,
                maxLines = 8,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Тип триггера запуска",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    TaskTrigger.TIME to "Однократно",
                    TaskTrigger.CRON to "По расписанию",
                    TaskTrigger.HEARTBEAT to "На селф-чек"
                ).forEach { (tType, label) ->
                    val isSelected = trigger == tType
                    FilterChip(
                        selected = isSelected,
                        onClick = { trigger = tType },
                        label = { Text(label) },
                        modifier = Modifier.handCursor()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (trigger) {
                TaskTrigger.TIME -> {
                    Text(
                        text = "Когда запустить?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useManualTime,
                            onClick = { useManualTime = false },
                            modifier = Modifier.handCursor()
                        )
                        Text("Через интервал")

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = useManualTime,
                            onClick = { useManualTime = true },
                            modifier = Modifier.handCursor()
                        )
                        Text("Задать время")
                    }

                    Spacer(Modifier.height(8.dp))

                    if (!useManualTime) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { delayMinutes = (delayMinutes - 5).coerceAtLeast(1) },
                                modifier = Modifier.handCursor(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("-5")
                            }
                            OutlinedButton(
                                onClick = { delayMinutes = (delayMinutes - 1).coerceAtLeast(1) },
                                modifier = Modifier.handCursor(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("-1")
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            ) {
                                Text(
                                    text = "$delayMinutes мин",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = { delayMinutes += 1 },
                                modifier = Modifier.handCursor(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("+1")
                            }
                            OutlinedButton(
                                onClick = { delayMinutes += 5 },
                                modifier = Modifier.handCursor(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("+5")
                            }
                        }
                    } else {
                        KaiOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = manualTimeString,
                            onValueChange = { manualTimeString = it },
                            label = { Text("Время (ГГГГ-ММ-ДД ЧЧ:ММ)") },
                            singleLine = true,
                        )
                    }
                }
                TaskTrigger.CRON -> {
                    Text(
                        text = "Расписание (Cron)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))

                    KaiOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = cronString,
                        onValueChange = { cronString = it },
                        label = { Text("Выражение cron") },
                        singleLine = true,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Быстрые пресеты:")
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "*/5 * * * *" to "5 мин",
                            "0 * * * *" to "1 час",
                            "0 */12 * * *" to "12 час",
                            "0 0 * * *" to "1 день"
                        ).forEach { (presetCron, label) ->
                            FilterChip(
                                selected = cronString == presetCron,
                                onClick = { cronString = presetCron },
                                label = { Text(label) },
                                modifier = Modifier.handCursor()
                            )
                        }
                    }
                }
                TaskTrigger.HEARTBEAT -> {
                    Text(
                        text = "Задача будет выполняться на каждом периодическом селф-чеке (heartbeat) приложения в фоновом режиме.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isInputValid) {
                            val targetTime = when (trigger) {
                                TaskTrigger.TIME -> {
                                    if (useManualTime) {
                                        parseLocalDateTime(manualTimeString) ?: 0L
                                    } else {
                                        Clock.System.now().toEpochMilliseconds() + delayMinutes * 60 * 1000L
                                    }
                                }
                                else -> 0L
                            }
                            val cronValue = if (trigger == TaskTrigger.CRON) cronString.trim() else null
                            onSave(description.trim(), prompt.trim(), targetTime, cronValue, trigger)
                        }
                    },
                    enabled = isInputValid,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
