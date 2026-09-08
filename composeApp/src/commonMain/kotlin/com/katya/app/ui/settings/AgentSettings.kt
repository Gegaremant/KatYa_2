@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.katya.app.RHVOICE_PACKAGE
import com.katya.app.data.HeartbeatLogEntry
import com.katya.app.data.MemoryEntry
import com.katya.app.data.ScheduledTask
import com.katya.app.data.TaskStatus
import com.katya.app.data.TaskTrigger
import com.katya.app.device.DeviceAdminManager
import com.katya.app.formatFileSize
import com.katya.app.isAppInstalled
import com.katya.app.openTtsSettings
import com.katya.app.ui.KaiOutlinedTextField
import com.katya.app.ui.components.SettingsListItem
import com.katya.app.ui.handCursor
import com.katya.app.ui.icons.Replay
import com.katya.app.ui.katyaAdaptiveCardSurface
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
private fun DeviceAdminSection(
    isDeviceAdmin: Boolean,
    onOpenSettings: () -> Unit,
) {
    val isActive = DeviceAdminManager.isDeviceAdminActive()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Администратор устройства",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = if (isActive) "Активен" else "Неактивен",
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = if (isActive) {
                "Приложение имеет права администратора."
            } else {
                "Права администратора не активны. Запросите их для управления устройством."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (isActive) {
                    DeviceAdminManager.openDeviceAdminSettings()
                } else {
                    DeviceAdminManager.requestDeviceAdmin()
                }
            },
            modifier = Modifier.handCursor(),
        ) {
            Text(if (isActive) "Открыть настройки" else "Запросить права")
        }
    }
}

@Composable
private fun TrustAgentSection(
    onOpenSettings: () -> Unit,
) {
    val isActive = DeviceAdminManager.isTrustAgentEnabled()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Агенты доверия",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = if (isActive) "Активен" else "Неактивен",
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = if (isActive) {
                "Приложение является агентом доверия."
            } else {
                "Агент доверия не активен. Настройте его в системных параметрах."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { DeviceAdminManager.openTrustAgentSettings() },
            modifier = Modifier.handCursor(),
        ) {
            Text("Открыть настройки агентов доверия")
        }
    }
}

@Composable
private fun AgentModeCard(
    agentMode: com.katya.app.data.AgentMode,
    sendDelayMs: Long,
    onChangeAgentMode: (com.katya.app.data.AgentMode) -> Unit,
    onChangeSendDelayMs: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Режим работы и задержка",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Настройте поведение агента и паузу перед отправкой сообщения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        
        Column(modifier = Modifier.fillMaxWidth().katyaAdaptiveCardSurface(RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("Режим работы", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(
                    selected = agentMode == com.katya.app.data.AgentMode.SHORT,
                    onClick = { onChangeAgentMode(com.katya.app.data.AgentMode.SHORT) }
                )
                Text("Короткий", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.material3.RadioButton(
                    selected = agentMode == com.katya.app.data.AgentMode.CONVERSATIONAL,
                    onClick = { onChangeAgentMode(com.katya.app.data.AgentMode.CONVERSATIONAL) }
                )
                Text("Собеседник", style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Задержка отправки: ${sendDelayMs} мс", style = MaterialTheme.typography.labelMedium)
            androidx.compose.material3.Slider(
                value = sendDelayMs.toFloat(),
                onValueChange = { onChangeSendDelayMs(it.toLong()) },
                valueRange = 0f..5000f,
                steps = 50,
            )
        }
    }
}

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
                        AgentModeCard(
                            agentMode = uiState.agentMode,
                            sendDelayMs = uiState.sendDelayMs,
                            onChangeAgentMode = actions.onChangeAgentMode,
                            onChangeSendDelayMs = actions.onChangeSendDelayMs,
                        )
                    }
                    SettingsCard {
                        DeviceAdminSection(
                            isDeviceAdmin = uiState.isDeviceAdmin,
                            onOpenSettings = actions.onOpenDeviceAdminSettings,
                        )
                    }
                    SettingsCard {
                        TrustAgentSection(
                            onOpenSettings = actions.onOpenTrustAgentSettings,
                        )
                    }
                    SettingsCard {
                        AudioEnginesCard(
                            sttEngine = uiState.sttEngine,
                            ttsEngine = uiState.ttsEngine,
                            ttsEngineInstalled = uiState.ttsEngineInstalled,
                            isVoiceRecognitionEnabled = uiState.isVoiceRecognitionEnabled,
                            onToggleVoiceRecognition = actions.onToggleVoiceRecognition,
                            isVoiceResponseEnabled = uiState.isVoiceResponseEnabled,
                            onToggleVoiceResponse = actions.onToggleVoiceResponse,
                            onChangeSttEngine = actions.onChangeSttEngine,
                            onChangeTtsEngine = actions.onChangeTtsEngine,
                            cloudSttUrl = uiState.cloudSttUrl,
                            cloudSttKey = uiState.cloudSttKey,
                            cloudSttModel = uiState.cloudSttModel,
                            cloudTtsUrl = uiState.cloudTtsUrl,
                            cloudTtsKey = uiState.cloudTtsKey,
                            cloudTtsModel = uiState.cloudTtsModel,
                            cloudTtsVoice = uiState.cloudTtsVoice,
                            onChangeCloudSttUrl = actions.onChangeCloudSttUrl,
                            onChangeCloudSttKey = actions.onChangeCloudSttKey,
                            onChangeCloudSttModel = actions.onChangeCloudSttModel,
                            onChangeCloudTtsUrl = actions.onChangeCloudTtsUrl,
                            onChangeCloudTtsKey = actions.onChangeCloudTtsKey,
                            onChangeCloudTtsModel = actions.onChangeCloudTtsModel,
                            onChangeCloudTtsVoice = actions.onChangeCloudTtsVoice,
                            piperInstalledVoices = uiState.piperInstalledVoices,
                            piperSelectedVoice = uiState.piperSelectedVoice,
                            piperVoiceUrl = uiState.piperVoiceUrl,
                            piperDownloadingBase = uiState.piperDownloadingBase,
                            piperDownloadProgress = uiState.piperDownloadProgress,
                            piperDownloadError = uiState.piperDownloadError,
                            onChangePiperVoiceUrl = actions.onChangePiperVoiceUrl,
                            onDownloadPiperVoice = actions.onDownloadPiperVoice,
                            onSelectPiperVoice = actions.onSelectPiperVoice,
                            onImportPiperVoice = actions.onImportPiperVoice,
                            onDeletePiperVoice = actions.onDeletePiperVoice,
                            onExportPiperVoice = actions.onExportPiperVoice,
                            isVoskReady = uiState.isVoskReady,
                            isVoskDownloading = uiState.isVoskDownloading,
                            voskDownloadProgress = uiState.voskDownloadProgress,
                            onDownloadVosk = actions.onDownloadVosk,
                        )

                        SandboxDistroCard(
                            distro = uiState.distro,
                            onChangeDistro = actions.onChangeDistro,
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
                    AgentModeCard(
                        agentMode = uiState.agentMode,
                        sendDelayMs = uiState.sendDelayMs,
                        onChangeAgentMode = actions.onChangeAgentMode,
                        onChangeSendDelayMs = actions.onChangeSendDelayMs,
                    )
                }
                SettingsCard {
                    AudioEnginesCard(
                        sttEngine = uiState.sttEngine,
                        ttsEngine = uiState.ttsEngine,
                        ttsEngineInstalled = uiState.ttsEngineInstalled,
                        isVoiceRecognitionEnabled = uiState.isVoiceRecognitionEnabled,
                        onToggleVoiceRecognition = actions.onToggleVoiceRecognition,
                        isVoiceResponseEnabled = uiState.isVoiceResponseEnabled,
                        onToggleVoiceResponse = actions.onToggleVoiceResponse,
                        onChangeSttEngine = actions.onChangeSttEngine,
                        onChangeTtsEngine = actions.onChangeTtsEngine,
                        cloudSttUrl = uiState.cloudSttUrl,
                        cloudSttKey = uiState.cloudSttKey,
                        cloudSttModel = uiState.cloudSttModel,
                        cloudTtsUrl = uiState.cloudTtsUrl,
                        cloudTtsKey = uiState.cloudTtsKey,
                        cloudTtsModel = uiState.cloudTtsModel,
                        cloudTtsVoice = uiState.cloudTtsVoice,
                        onChangeCloudSttUrl = actions.onChangeCloudSttUrl,
                        onChangeCloudSttKey = actions.onChangeCloudSttKey,
                        onChangeCloudSttModel = actions.onChangeCloudSttModel,
                        onChangeCloudTtsUrl = actions.onChangeCloudTtsUrl,
                        onChangeCloudTtsKey = actions.onChangeCloudTtsKey,
                        onChangeCloudTtsModel = actions.onChangeCloudTtsModel,
                        onChangeCloudTtsVoice = actions.onChangeCloudTtsVoice,
                        isVoskReady = uiState.isVoskReady,
                        isVoskDownloading = uiState.isVoskDownloading,
                        voskDownloadProgress = uiState.voskDownloadProgress,
                        onDownloadVosk = actions.onDownloadVosk,
                    )

                    SandboxDistroCard(
                        distro = uiState.distro,
                        onChangeDistro = actions.onChangeDistro,
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
                horizontalArrangement = Arrangement.End,
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
            },
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
        val pendingTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
            .sortedBy { it.scheduledAtEpochMs }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pendingTasks.isNotEmpty()) {
                    Text(
                        text = "Ожидающих задач: ${pendingTasks.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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
                if (pendingTasks.isEmpty()) {
                    Text(
                        text = "Нет активных задач",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    pendingTasks.forEach { task ->
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
                        val detail = task.lastResult
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "\nРезультат: $it" }
                            ?: ""
                        SettingsListItem(
                            title = task.description,
                            subtitle = subtitle + detail,
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
            },
        )
    }

    if (isAddingTask) {
        AddEditTaskSheet(
            task = null,
            onDismiss = { isAddingTask = false },
            onSave = { desc, pr, time, cron, trig ->
                onAddScheduledTask(desc, pr, time, cron, trig)
                isAddingTask = false
            },
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
                    trigger = trig,
                )
                onUpdateScheduledTask(updated)
                editingTask = null
            },
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onEditClick(task)
                    },
                    modifier = Modifier.handCursor(),
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

private fun parseLocalDateTime(str: String): Long? = try {
    val parts = str.trim().split(' ')
    val dateParts = parts[0].split('-').map { it.toInt() }
    val timeParts = parts[1].split(':').map { it.toInt() }
    val localDateTime = kotlinx.datetime.LocalDateTime(
        dateParts[0],
        dateParts[1],
        dateParts[2],
        timeParts[0],
        timeParts[1],
        0,
        0,
    )
    localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
} catch (_: Exception) {
    null
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
                label = { Text("Задача") },
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
                    TaskTrigger.CRON to "Расписание",
                    TaskTrigger.HEARTBEAT to "Пульс",
                ).forEach { (tType, label) ->
                    val isSelected = trigger == tType
                    FilterChip(
                        selected = isSelected,
                        onClick = { trigger = tType },
                        label = { Text(label) },
                        modifier = Modifier.handCursor(),
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !useManualTime,
                            onClick = { useManualTime = false },
                            modifier = Modifier.handCursor(),
                        )
                        Text("Через интервал")

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = useManualTime,
                            onClick = { useManualTime = true },
                            modifier = Modifier.handCursor(),
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
                            "0 0 * * *" to "1 день",
                        ).forEach { (presetCron, label) ->
                            FilterChip(
                                selected = cronString == presetCron,
                                onClick = { cronString = presetCron },
                                label = { Text(label) },
                                modifier = Modifier.handCursor(),
                            )
                        }
                    }
                }

                TaskTrigger.HEARTBEAT -> {
                    Text(
                        text = "Задача будет выполняться на каждом периодическом селф-чеке (heartbeat) приложения в фоновом режиме.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun AudioEnginesCard(
    sttEngine: com.katya.app.data.SttEngine,
    ttsEngine: com.katya.app.data.TtsEngine,
    ttsEngineInstalled: Boolean = true,
    isVoiceResponseEnabled: Boolean,
    onToggleVoiceResponse: (Boolean) -> Unit,
    isVoiceRecognitionEnabled: Boolean,
    onToggleVoiceRecognition: (Boolean) -> Unit,
    onChangeSttEngine: (com.katya.app.data.SttEngine) -> Unit,
    onChangeTtsEngine: (com.katya.app.data.TtsEngine) -> Unit,
    cloudSttUrl: String = "",
    cloudSttKey: String = "",
    cloudSttModel: String = "",
    cloudTtsUrl: String = "",
    cloudTtsKey: String = "",
    cloudTtsModel: String = "",
    cloudTtsVoice: String = "",
    onChangeCloudSttUrl: (String) -> Unit = {},
    onChangeCloudSttKey: (String) -> Unit = {},
    onChangeCloudSttModel: (String) -> Unit = {},
    onChangeCloudTtsUrl: (String) -> Unit = {},
    onChangeCloudTtsKey: (String) -> Unit = {},
    onChangeCloudTtsModel: (String) -> Unit = {},
    onChangeCloudTtsVoice: (String) -> Unit = {},
    piperInstalledVoices: ImmutableList<com.katya.app.tts.PiperVoiceInfo> = persistentListOf(),
    piperSelectedVoice: String? = null,
    piperVoiceUrl: String = "",
    piperDownloadingBase: String? = null,
    piperDownloadProgress: Float? = null,
    piperDownloadError: String? = null,
    onChangePiperVoiceUrl: (String) -> Unit = {},
    onDownloadPiperVoice: (String) -> Unit = {},
    onSelectPiperVoice: (String) -> Unit = {},
    onImportPiperVoice: (String, ByteArray) -> Unit = { _, _ -> },
    onDeletePiperVoice: (String) -> Unit = {},
    onExportPiperVoice: (String) -> Unit = {},
    isVoskReady: Boolean = false,
    isVoskDownloading: Boolean = false,
    voskDownloadProgress: Float? = null,
    onDownloadVosk: () -> Unit = {},
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var cloudSttExpanded by remember { mutableStateOf(false) }
    var cloudTtsExpanded by remember { mutableStateOf(false) }
    var sttExpanded by remember { mutableStateOf(true) }
    var ttsExpanded by remember { mutableStateOf(true) }
    var piperSettingsExpanded by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Слух и речь",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Выберите движки для распознавания и синтеза речи. Локальные модели работают полностью без интернета, но требуют загрузки.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(
            modifier = Modifier.fillMaxWidth()
                .katyaAdaptiveCardSurface(RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            ToggleableHeadline(
                title = "Включить распознавание речи",
                description = "Агент будет слушать вас и переводить речь в текст (состояние синхронизировано с кнопкой микрофона)",
                checked = isVoiceRecognitionEnabled,
                onCheckedChange = onToggleVoiceRecognition,
            )
            
            if (isVoiceRecognitionEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "Распознавание речи (Слух)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    com.katya.app.data.SttEngine.entries.forEach { engine ->
                    val name = when (engine) {
                        com.katya.app.data.SttEngine.GKPSR -> "GKPSR (Google Keyboard Parsing Speech Recognizer)"
                        com.katya.app.data.SttEngine.SYSTEM -> "Android STT"
                        com.katya.app.data.SttEngine.LOCAL -> "Local (Vosk)"
                        com.katya.app.data.SttEngine.CLOUD -> "Cloud API"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onChangeSttEngine(engine) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = sttEngine == engine,
                            onClick = { onChangeSttEngine(engine) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }

                    if (engine == com.katya.app.data.SttEngine.CLOUD) {
                        Text(
                            text = "(в разработке)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                        )
                    }

                    if (engine == com.katya.app.data.SttEngine.LOCAL) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when {
                                isVoskReady -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Модель Vosk установлена — распознавание работает офлайн",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                isVoskDownloading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { voskDownloadProgress ?: 0f },
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        )
                                        Text(
                                            text = if (voskDownloadProgress != null) {
                                                "${(voskDownloadProgress * 100).toInt()}%"
                                            } else {
                                                "0%"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Text(
                                        text = "Скачивается модель распознавания...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }

                                else -> {
                                    Text(
                                        text = "Для локального распознавания нужна модель Vosk. Она скачается прямо в приложение и будет работать без интернета:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = "Скачать модель Vosk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .handCursor()
                                            .clickable { onDownloadVosk() }
                                            .padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
                .katyaAdaptiveCardSurface(RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            ToggleableHeadline(
                title = "Включить синтез речи",
                description = "Агент будет озвучивать свои ответы (состояние синхронизировано с кнопкой динамика)",
                checked = isVoiceResponseEnabled,
                onCheckedChange = onToggleVoiceResponse,
            )
            
            if (isVoiceResponseEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "Синтез речи (Голос)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    com.katya.app.data.TtsEngine.entries.forEach { engine ->
                    val name = when (engine) {
                        com.katya.app.data.TtsEngine.SYSTEM -> "По умолчанию (Android)"
                        com.katya.app.data.TtsEngine.LOCAL -> "Локальный"
                        com.katya.app.data.TtsEngine.CLOUD -> "Cloud API"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onChangeTtsEngine(engine) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = ttsEngine == engine,
                            onClick = { onChangeTtsEngine(engine) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }

                    if (engine == com.katya.app.data.TtsEngine.LOCAL) {
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp)) {
                            Text("Предустановленные голоса", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(selected = true, onClick = {})
                                Text("Голос 1 (Женский)", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(16.dp))
                                androidx.compose.material3.RadioButton(selected = false, onClick = {})
                                Text("Голос 2 (Мужской)", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Тональность (Pitch)", style = MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Slider(value = 1f, onValueChange = {}, valueRange = 0.5f..2f)
                            Text("Скорость (Speed)", style = MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Slider(value = 1f, onValueChange = {}, valueRange = 0.5f..2f)
                        }
                    }

                    if (engine == com.katya.app.data.TtsEngine.CLOUD) {
                        Text(
                            text = "(в разработке)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                        )
                    }

                    // Piper is removed
                }
            }
        }
    }
    }
}

@Composable
private fun SpoilerBlock(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handCursor()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun CloudApiSpoiler(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handCursor()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚙ Настройки Cloud API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun CloudSpeechFields(
    url: String,
    apiKey: String,
    model: String,
    urlLabel: String,
    apiKeyLabel: String,
    modelLabel: String,
    onChangeUrl: (String) -> Unit,
    onChangeApiKey: (String) -> Unit,
    onChangeModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = onChangeUrl,
            label = {
                Text(urlLabel, color = MaterialTheme.colorScheme.onBackground)
            },
        )
        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = apiKey,
            onValueChange = onChangeApiKey,
            label = {
                Text(apiKeyLabel, color = MaterialTheme.colorScheme.onBackground)
            },
        )
        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = model,
            onValueChange = onChangeModel,
            label = {
                Text(modelLabel, color = MaterialTheme.colorScheme.onBackground)
            },
        )
    }
}

@Composable
private fun SandboxDistroCard(
    distro: com.katya.app.data.Distro,
    onChangeDistro: (com.katya.app.data.Distro) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Песочница (Linux)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Выберите дистрибутив Linux внутри песочницы. Существующая установка сохраняется, данные не удаляются.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .katyaAdaptiveCardSurface(RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            com.katya.app.data.Distro.entries.forEach { d ->
                val name = when (d) {
                    com.katya.app.data.Distro.DEBIAN -> "Debian (рекомендуется)"
                    com.katya.app.data.Distro.TERMUX -> "Termux"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onChangeDistro(d) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = distro == d,
                        onClick = { onChangeDistro(d) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        
        PlatformExternalStorageButton()
    }
}

@Composable
private fun PiperVoicesCard(
    modifier: Modifier = Modifier,
    voices: ImmutableList<com.katya.app.tts.PiperVoiceInfo> = persistentListOf(),
    selectedVoice: String? = null,
    voiceUrl: String = "",
    downloadingBase: String? = null,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    onChangeVoiceUrl: (String) -> Unit = {},
    onDownloadVoice: (String) -> Unit = {},
    onSelectVoice: (String) -> Unit = {},
    onImportVoice: (String, ByteArray) -> Unit = { _, _ -> },
    onDeleteVoice: (String) -> Unit = {},
    onExportVoice: (String) -> Unit = {},
    onOpenHuggingFace: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val importPicker = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("tflite", "json", "zip")),
    ) { picked ->
        if (picked != null) {
            scope.launch {
                runCatching {
                    onImportVoice(picked.name, picked.readBytes())
                }.onFailure {
                    com.katya.app.showToast("Не удалось прочитать выбранный файл")
                }
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Голоса Piper",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Установленные голоса (движок синтеза речи на устройстве). " +
                "Голос хранится как .tflite файл (с опциональным .json дескриптором) в папке моделей Кати.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (voices.isEmpty()) {
            Text(
                text = "Голоса не установлены. Скачайте .tflite голос по прямой ссылке или импортируйте файл.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .katyaAdaptiveCardSurface(RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                voices.forEach { voice ->
                    val isSelected = voice.baseName == selectedVoice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSelected) { onSelectVoice(voice.baseName) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectVoice(voice.baseName) },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = voice.baseName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatFileSize(voice.sizeBytes)} · ${voice.sampleRate / 1000} кГц${if (voice.hasConfigJson) " · c дескриптором" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (downloadingBase == voice.baseName) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { onExportVoice(voice.baseName) },
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Сохранить голос на устройство",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = { onDeleteVoice(voice.baseName) },
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить голос",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Download from a direct URL
        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = voiceUrl,
            onValueChange = onChangeVoiceUrl,
            placeholder = { Text("https://…/мой-голос.onnx.tflite") },
            label = { Text("Прямая ссылка на .tflite голос", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                OutlinedButton(
                    onClick = { onDownloadVoice(voiceUrl) },
                    enabled = voiceUrl.isNotBlank() && downloadingBase == null,
                    modifier = Modifier.handCursor().padding(end = 4.dp),
                ) {
                    Text("Скачать")
                }
            },
        )
        if (downloadingBase != null) {
            LinearProgressIndicator(
                progress = { downloadProgress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (downloadError != null) {
            Text(
                text = downloadError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "Сначала найдите голос на HuggingFace, откройте файл .tflite (не .onnx) и скопируйте прямую ссылку. " +
                "Например: https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/…onx.tflite",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Русские голоса Piper",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .handCursor()
                .clickable(onClick = onOpenHuggingFace)
                .padding(vertical = 4.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { importPicker.launch() },
                enabled = downloadingBase == null,
                modifier = Modifier.handCursor(),
            ) {
                Text("Импортировать .tflite / .json / .zip")
            }
        }
    }
}
