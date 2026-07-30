package com.katya.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katya.app.data.ThemeMode
import com.katya.app.ui.KaiOutlinedTextField
import com.katya.app.ui.components.KatyaSlider
import com.katya.app.ui.handCursor
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.ic_arrow_drop_down
import katya.composeapp.generated.resources.settings_daemon_mode
import katya.composeapp.generated.resources.settings_daemon_mode_description
import katya.composeapp.generated.resources.settings_dynamic_ui
import katya.composeapp.generated.resources.settings_dynamic_ui_description
import katya.composeapp.generated.resources.settings_theme
import katya.composeapp.generated.resources.settings_theme_dark
import katya.composeapp.generated.resources.settings_theme_description
import katya.composeapp.generated.resources.settings_theme_light
import katya.composeapp.generated.resources.settings_theme_oled
import katya.composeapp.generated.resources.settings_theme_system
import katya.composeapp.generated.resources.settings_ui_scale
import katya.composeapp.generated.resources.settings_voice_response
import katya.composeapp.generated.resources.settings_voice_response_description
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

@Composable
internal fun GeneralContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    textToSpeech: nl.marc_apps.tts.TextToSpeechInstance? = null,
) {
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
                    if (uiState.showDaemonToggle) {
                        SettingsCard {
                            DaemonModeToggle(
                                isDaemonEnabled = uiState.isDaemonEnabled,
                                onToggleDaemon = actions.onToggleDaemon,
                            )
                        }
                    }
                    SettingsCard {
                        DynamicUiToggle(
                            isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                            onToggleDynamicUi = actions.onToggleDynamicUi,
                        )
                        AgentVisibilityToggle(
                            isAgentVisibilityEnabled = uiState.isAgentVisibilityEnabled,
                            onToggleAgentVisibility = actions.onToggleAgentVisibility,
                        )
                        VoiceResponseToggle(
                            isVoiceResponseEnabled = uiState.isVoiceResponseEnabled,
                            onToggleVoiceResponse = actions.onToggleVoiceResponse,
                            textToSpeech = textToSpeech,
                        )
                    }
                    SettingsCard {
                        ThemeModePicker(
                            themeMode = uiState.themeMode,
                            onChangeThemeMode = actions.onChangeThemeMode,
                        )
                    }
                    SettingsCard {
                        QuickActionsSection(
                            quickActions = uiState.quickActions,
                            onAddQuickAction = actions.onAddQuickAction,
                            onUpdateQuickAction = actions.onUpdateQuickAction,
                            onDeleteQuickAction = actions.onDeleteQuickAction,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.showUiScale) {
                        SettingsCard {
                            UiScaleSection(
                                uiScale = uiState.uiScale,
                                onChangeUiScale = actions.onChangeUiScale,
                            )
                        }
                    }
                    SettingsCard {
                        ExportImportSection(
                            onExportSettings = actions.onExportSettings,
                            onPrepareExport = actions.onPrepareExport,
                            onImportSettings = actions.onImportSettings,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.showDaemonToggle) {
                    SettingsCard {
                        DaemonModeToggle(
                            isDaemonEnabled = uiState.isDaemonEnabled,
                            onToggleDaemon = actions.onToggleDaemon,
                        )
                    }
                }
                SettingsCard {
                    DynamicUiToggle(
                        isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                        onToggleDynamicUi = actions.onToggleDynamicUi,
                    )
                    AgentVisibilityToggle(
                        isAgentVisibilityEnabled = uiState.isAgentVisibilityEnabled,
                        onToggleAgentVisibility = actions.onToggleAgentVisibility,
                    )
                    VoiceResponseToggle(
                        isVoiceResponseEnabled = uiState.isVoiceResponseEnabled,
                        onToggleVoiceResponse = actions.onToggleVoiceResponse,
                        textToSpeech = textToSpeech,
                    )
                    WakeWordToggle(
                        isWakeWordEnabled = uiState.isWakeWordEnabled,
                        onToggleWakeWord = actions.onToggleWakeWord,
                        wakeWordTrigger = uiState.wakeWordTrigger,
                        onChangeWakeWordTrigger = actions.onChangeWakeWordTrigger,
                        wakeWordModelLang = uiState.wakeWordModelLang,
                        onChangeWakeWordModelLang = actions.setWakeWordModelLang,
                        isWakeWordVibrationEnabled = uiState.isWakeWordVibrationEnabled,
                        onToggleWakeWordVibration = actions.setIsWakeWordVibrationEnabled,
                        isWakeWordSoundEnabled = uiState.isWakeWordSoundEnabled,
                        onToggleWakeWordSound = actions.setIsWakeWordSoundEnabled,
                        isVoskDownloading = uiState.isVoskDownloading,
                        voskDownloadProgress = uiState.voskDownloadProgress,
                        isVoskReady = uiState.isVoskReady,
                        onDownloadVosk = actions.onDownloadVosk,
                    )
                }
                SettingsCard {
                    ThemeModePicker(
                        themeMode = uiState.themeMode,
                        onChangeThemeMode = actions.onChangeThemeMode,
                    )
                }
                SettingsCard {
                    QuickActionsSection(
                        quickActions = uiState.quickActions,
                        onAddQuickAction = actions.onAddQuickAction,
                        onUpdateQuickAction = actions.onUpdateQuickAction,
                        onDeleteQuickAction = actions.onDeleteQuickAction,
                    )
                }
                if (uiState.showUiScale) {
                    SettingsCard {
                        UiScaleSection(
                            uiScale = uiState.uiScale,
                            onChangeUiScale = actions.onChangeUiScale,
                        )
                    }
                }
                SettingsCard {
                    ExportImportSection(
                        onExportSettings = actions.onExportSettings,
                        onPrepareExport = actions.onPrepareExport,
                        onImportSettings = actions.onImportSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun DaemonModeToggle(
    isDaemonEnabled: Boolean,
    onToggleDaemon: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_daemon_mode),
            description = stringResource(Res.string.settings_daemon_mode_description),
            checked = isDaemonEnabled,
            onCheckedChange = onToggleDaemon,
        )
    }
}

@Composable
private fun DynamicUiToggle(
    isDynamicUiEnabled: Boolean,
    onToggleDynamicUi: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_dynamic_ui),
            description = stringResource(Res.string.settings_dynamic_ui_description),
            checked = isDynamicUiEnabled,
            onCheckedChange = onToggleDynamicUi,
        )
    }
}

@Composable
private fun AgentVisibilityToggle(
    isAgentVisibilityEnabled: Boolean,
    onToggleAgentVisibility: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = "Видимость работы",
            description = "Показывать все внутренние операции и использование инструментов",
            checked = isAgentVisibilityEnabled,
            onCheckedChange = onToggleAgentVisibility,
        )
    }
}

@OptIn(nl.marc_apps.tts.experimental.ExperimentalVoiceApi::class)
@Composable
private fun VoiceResponseToggle(
    isVoiceResponseEnabled: Boolean,
    onToggleVoiceResponse: (Boolean) -> Unit,
    textToSpeech: nl.marc_apps.tts.TextToSpeechInstance? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf(textToSpeech?.currentVoice?.name) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_voice_response),
            description = stringResource(Res.string.settings_voice_response_description),
            checked = isVoiceResponseEnabled,
            onCheckedChange = onToggleVoiceResponse,
        )
        // Removed system TTS and RHVoice buttons to declutter UI as requested by user

        if (textToSpeech != null && textToSpeech.voices.toList().isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                KaiOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = selectedVoice ?: "По умолчанию",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Голос") },
                    trailingIcon = {
                        Icon(
                            modifier = Modifier.handCursor(),
                            imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .handCursor()
                        .clickable { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    val voices = textToSpeech.voices.toList().distinctBy { it.name }
                    voices.forEach { voice ->
                        val isSelected = voice.name == selectedVoice
                        DropdownMenuItem(
                            text = { Text(voice.name) },
                            onClick = {
                                expanded = false
                                textToSpeech.currentVoice = voice
                                selectedVoice = voice.name
                            },
                            modifier = Modifier
                                .handCursor()
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModePicker(
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.System to stringResource(Res.string.settings_theme_system),
        ThemeMode.Light to stringResource(Res.string.settings_theme_light),
        ThemeMode.Dark to stringResource(Res.string.settings_theme_dark),
        ThemeMode.OledBlack to stringResource(Res.string.settings_theme_oled),
    )
    val selectedLabel = options.first { it.first == themeMode }.second
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.settings_theme_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(
                        modifier = Modifier.handCursor(),
                        imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
            // Transparent overlay to capture clicks reliably on all platforms
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .handCursor()
                    .clickable { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                options.forEach { (mode, label) ->
                    val isSelected = mode == themeMode
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onChangeThemeMode(mode)
                        },
                        modifier = Modifier
                            .handCursor()
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UiScaleSection(
    uiScale: Float,
    onChangeUiScale: (Float) -> Unit,
) {
    var sliderValue by remember(uiScale) { mutableStateOf(uiScale) }
    val steps = 14 // 16 snap points from 50% to 200% in 10% increments (14 intermediate)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_ui_scale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${(sliderValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        KatyaSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeUiScale(sliderValue) },
            valueRange = 0.5f..2.0f,
            steps = steps,
        )
    }
}

/**
 * Раздел для управления быстрыми действиями.
 * Позволяет добавлять, редактировать и удалять кнопки с промптами, которые
 * будут отображаться над полем ввода чата.
 */
@Composable
private fun QuickActionsSection(
    quickActions: kotlinx.collections.immutable.ImmutableList<com.katya.app.data.QuickAction>,
    onAddQuickAction: (com.katya.app.data.QuickAction) -> Unit,
    onUpdateQuickAction: (com.katya.app.data.QuickAction) -> Unit,
    onDeleteQuickAction: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(
                    text = "Быстрые действия",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Управление кнопками быстрых действий, отображаемыми над полем ввода чата.\n\nПримеры:\n1. Кнопка: 'Переведи', Промпт: 'Переведи этот текст на английский'.\n2. Кнопка: 'Сократи', Промпт: 'Сделай краткую выжимку из этого текста'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text("Добавить")
            }
        }

        if (quickActions.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (action in quickActions) {
                    var isEditing by remember { mutableStateOf(false) }

                    if (isEditing) {
                        QuickActionEditor(
                            initialAction = action,
                            onSave = {
                                onUpdateQuickAction(it)
                                isEditing = false
                            },
                            onCancel = { isEditing = false },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(action.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(action.prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                androidx.compose.material3.IconButton(onClick = { isEditing = true }) {
                                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Редактировать")
                                }
                                androidx.compose.material3.IconButton(onClick = { onDeleteQuickAction(action.id) }) {
                                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddDialog = false }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.padding(16.dp)) {
                    QuickActionEditor(
                        initialAction = com.katya.app.data.QuickAction(id = "", text = "", prompt = ""),
                        onSave = {
                            onAddQuickAction(it.copy(id = kotlin.uuid.Uuid.random().toString()))
                            showAddDialog = false
                        },
                        onCancel = { showAddDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionEditor(
    initialAction: com.katya.app.data.QuickAction,
    onSave: (com.katya.app.data.QuickAction) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initialAction.text) }
    var prompt by remember { mutableStateOf(initialAction.prompt) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KaiOutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Название кнопки") },
            modifier = Modifier.fillMaxWidth(),
        )
        KaiOutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Промпт / Инструкция") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Отмена")
            }
            androidx.compose.material3.TextButton(onClick = {
                onSave(initialAction.copy(text = text, prompt = prompt))
            }, enabled = text.isNotBlank() && prompt.isNotBlank()) {
                Text("Сохранить")
            }
        }
    }
}
