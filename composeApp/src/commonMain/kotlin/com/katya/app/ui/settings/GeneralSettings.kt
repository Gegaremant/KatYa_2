package com.katya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katya.app.data.ThemeMode
import com.katya.app.ui.KaiOutlinedTextField
import com.katya.app.ui.components.KatyaSlider
import com.katya.app.ui.handCursor
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

@Composable
internal fun GeneralContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    textToSpeech: com.katya.app.tts.SpeechEngine? = null,
) {
    val appSettings = org.koin.compose.koinInject<com.katya.app.data.AppSettings>()

    // Local states
    var voiceThoughts by remember { mutableStateOf(appSettings.isShowAndVoiceThoughtsEnabled()) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Настройки общие ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Настройки общие",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                ThemeModePicker(
                    themeMode = uiState.themeMode,
                    onChangeThemeMode = actions.onChangeThemeMode,
                )

                if (uiState.showDaemonToggle) {
                    ToggleableHeadline(
                        title = "Фоновый режим",
                        description = "Катя работает всегда (включено по умолчанию)",
                        checked = uiState.isDaemonEnabled,
                        onCheckedChange = actions.onToggleDaemon,
                    )
                }

                ToggleableHeadline(
                    title = "Синтез речи",
                    description = "Озвучивать ответы голосом",
                    checked = uiState.isVoiceResponseEnabled,
                    onCheckedChange = actions.onToggleVoiceResponse,
                )

                ToggleableHeadline(
                    title = "Распознавание речи",
                    description = "Активировать микрофон для диктовки",
                    checked = uiState.isVoiceRecognitionEnabled,
                    onCheckedChange = actions.onToggleVoiceRecognition,
                )

                WakeWordToggle(
                    isWakeWordEnabled = uiState.isWakeWordEnabled,
                    onToggleWakeWord = actions.onToggleWakeWord,
                    wakeWordTrigger = uiState.wakeWordTrigger,
                    onChangeWakeWordTrigger = actions.onChangeWakeWordTrigger,
                    wakeWordModelLang = uiState.wakeWordModelLang,
                    onChangeWakeWordModelLang = actions.onSelectWakeWordModelLang,
                    isWakeWordVibrationEnabled = uiState.isWakeWordVibrationEnabled,
                    onToggleWakeWordVibration = actions.onToggleWakeWordVibration,
                    isWakeWordSoundEnabled = uiState.isWakeWordSoundEnabled,
                    onToggleWakeWordSound = actions.onToggleWakeWordSound,
                    isVoskDownloading = uiState.isVoskDownloading,
                    voskDownloadProgress = uiState.voskDownloadProgress,
                    isVoskReady = uiState.isVoskReady,
                    onDownloadVosk = actions.onDownloadVosk,
                )

                ToggleableHeadline(
                    title = "Показ и озвучивание размышлений",
                    description = "Катя будет проговаривать свои мысли вслух",
                    checked = voiceThoughts && uiState.isVoiceResponseEnabled,
                    enabled = uiState.isVoiceResponseEnabled,
                    onCheckedChange = {
                        voiceThoughts = it
                        appSettings.setShowAndVoiceThoughtsEnabled(it)
                    },
                )

                ToggleableHeadline(
                    title = "Видимость работы",
                    description = "Показывать все внутренние операции и использование инструментов",
                    checked = uiState.isAgentVisibilityEnabled,
                    onCheckedChange = actions.onToggleAgentVisibility,
                )

                ToggleableHeadline(
                    title = "Динамический интерфейс",
                    description = "Использовать динамический UI (компоненты)",
                    checked = uiState.isDynamicUiEnabled,
                    onCheckedChange = actions.onToggleDynamicUi,
                )
            }
        }

        // --- Расширенные настройки (spoiler) ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isAdvancedExpanded = !isAdvancedExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Расширенные настройки",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedVisibility(visible = isAdvancedExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val isOverlayEnabled = uiState.monitorOverlayMode != com.katya.app.data.MonitorOverlayMode.OFF
                        ToggleableHeadline(
                            title = "Оверлей отладки",
                            description = "Показывать поверх всех окон",
                            checked = isOverlayEnabled,
                            onCheckedChange = { checked ->
                                actions.onChangeMonitorOverlayMode(
                                    if (checked) com.katya.app.data.MonitorOverlayMode.SHORT else com.katya.app.data.MonitorOverlayMode.OFF
                                )
                            }
                        )

                        ToggleableHeadline(
                            title = "Управление с часов / гарнитуры",
                            description = "Использовать кнопки плеера на часах и микрофон наушника для голосовых команд",
                            checked = uiState.isWatchIntegrationEnabled,
                            onCheckedChange = actions.onToggleWatchIntegration,
                        )

                        Row(modifier = Modifier.fillMaxWidth().clickable { /* TODO: open app logs */ }.padding(16.dp)) {
                            Text("Показ логов", style = MaterialTheme.typography.titleSmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
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
    val selectedLabel = options.firstOrNull { it.first == themeMode }?.second ?: "System"
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            expanded = false
                            onChangeThemeMode(mode)
                        },
                        modifier = Modifier
                            .handCursor()
                            .then(
                                if (isSelected) Modifier.padding(horizontal = 4.dp).background(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) else Modifier
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun UiScaleSection(
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

@Composable
internal fun QuickActionsSection(
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

@Composable
fun WatchIntegrationToggle(
    isWatchIntegrationEnabled: Boolean,
    onToggleWatchIntegration: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = "Управление с часов / гарнитуры",
            description = "Использовать кнопки плеера на часах и микрофон наушника для голосовых команд",
            checked = isWatchIntegrationEnabled,
            onCheckedChange = onToggleWatchIntegration,
        )
    }
}
