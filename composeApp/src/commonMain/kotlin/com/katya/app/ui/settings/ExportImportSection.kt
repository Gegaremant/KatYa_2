package com.katya.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.katya.app.BackupPayload
import com.katya.app.data.ImportMode
import com.katya.app.data.ImportPreviews
import com.katya.app.data.ImportSection
import com.katya.app.data.SharedJson
import com.katya.app.data.detectImportSections
import com.katya.app.saveFileToDevice
import com.katya.app.tools.AppLogger
import com.katya.app.ui.components.VerticalScrollbarForScroll
import com.katya.app.ui.components.rememberBackupImportController
import com.katya.app.ui.handCursor
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.settings_export
import katya.composeapp.generated.resources.settings_export_import_description
import katya.composeapp.generated.resources.settings_export_import_title
import katya.composeapp.generated.resources.settings_export_preview_title
import katya.composeapp.generated.resources.settings_import
import katya.composeapp.generated.resources.settings_import_confirm
import katya.composeapp.generated.resources.settings_import_diff_empty
import katya.composeapp.generated.resources.settings_import_diff_summary
import katya.composeapp.generated.resources.settings_import_diff_title
import katya.composeapp.generated.resources.settings_import_error
import katya.composeapp.generated.resources.settings_import_mode_merge
import katya.composeapp.generated.resources.settings_import_mode_merge_description
import katya.composeapp.generated.resources.settings_import_mode_replace
import katya.composeapp.generated.resources.settings_import_mode_replace_description
import katya.composeapp.generated.resources.settings_import_partial
import katya.composeapp.generated.resources.settings_import_preview_title
import katya.composeapp.generated.resources.settings_import_section_conversations
import katya.composeapp.generated.resources.settings_import_section_email
import katya.composeapp.generated.resources.settings_import_section_heartbeat
import katya.composeapp.generated.resources.settings_import_section_mcp
import katya.composeapp.generated.resources.settings_import_section_memory
import katya.composeapp.generated.resources.settings_import_section_models
import katya.composeapp.generated.resources.settings_import_section_scheduling
import katya.composeapp.generated.resources.settings_import_section_services
import katya.composeapp.generated.resources.settings_import_section_settings
import katya.composeapp.generated.resources.settings_import_section_soul
import katya.composeapp.generated.resources.settings_import_section_tools
import katya.composeapp.generated.resources.settings_import_success
import katya.composeapp.generated.resources.settings_mcp_cancel
import katya.composeapp.generated.resources.settings_sms
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
internal fun ExportImportSection(
    onExportSettings: suspend (Set<ImportSection>) -> ByteArray,
    onPrepareExport: () -> Map<ImportSection, String?>,
    prepareImport: (String, Set<ImportSection>) -> ImportPreviews,
    onImportSettings: suspend (String, Set<ImportSection>, ImportMode, BackupPayload?) -> ImportResult,
) {
    val scope = rememberCoroutineScope()
    var exportPreview by remember { mutableStateOf<ImmutableMap<ImportSection, String?>?>(null) }

    val importController = rememberBackupImportController(
        preparePreview = prepareImport,
        applyImport = onImportSettings,
    )

    importController.pending?.let { pending ->
        ImportPreviewDialog(
            sectionDetails = pending.sectionDetails.toImmutableMap(),
            previews = pending.previews,
            onConfirm = importController.confirm,
            onDismiss = importController.dismiss,
        )
    }

    exportPreview?.let { sectionDetails ->
        ExportPreviewDialog(
            sectionDetails = sectionDetails,
            onConfirm = { selectedSections ->
                exportPreview = null
                scope.launch {
                    val zipBytes = withContext(Dispatchers.IO) { onExportSettings(selectedSections) }
                    if (zipBytes.isNotEmpty()) {
                        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        val yy = now.year.toString().takeLast(2)
                        val mm = now.monthNumber.toString().padStart(2, '0')
                        val dd = now.dayOfMonth.toString().padStart(2, '0')
                        val success = withContext(Dispatchers.IO) {
                            com.katya.app.saveFileToDevice(
                                bytes = zipBytes,
                                baseName = "$yy-$mm-${dd}_Katya_backup",
                                extension = "zip",
                            )
                        }
                        if (success) {
                            com.katya.app.showToast("Конфигурация успешно сохранена (${zipBytes.size} байт)")
                            AppLogger.i("ExportImport", "Конфигурация успешно сохранена (${zipBytes.size} байт)")
                        } else {
                            com.katya.app.showToast("Ошибка сохранения конфигурации")
                            AppLogger.e("ExportImport", "Ошибка сохранения конфигурации")
                        }
                    } else {
                        com.katya.app.showToast("Ошибка: Экспортируемый файл пуст (0 байт)")
                        AppLogger.e("ExportImport", "Ошибка: Экспортируемый файл пуст (0 байт)")
                    }
                }
            },
            onDismiss = { exportPreview = null },
        )
    }

    Text(
        text = stringResource(Res.string.settings_export_import_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.settings_export_import_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                exportPreview = onPrepareExport().toImmutableMap()
            },
            modifier = Modifier.handCursor(),
        ) {
            Text(stringResource(Res.string.settings_export))
        }
        OutlinedButton(
            onClick = {
                importController.launchPicker()
            },
            modifier = Modifier.handCursor(),
        ) {
            Text(stringResource(Res.string.settings_import))
        }
    }
    val importResult = importController.result
    if (importResult != null) {
        Spacer(Modifier.height(8.dp))
        val (text, color) = when (val result = importResult) {
            is ImportResult.Success -> stringResource(Res.string.settings_import_success) to MaterialTheme.colorScheme.primary
            is ImportResult.PartialSuccess -> stringResource(Res.string.settings_import_partial, result.errorCount) to MaterialTheme.colorScheme.primary
            is ImportResult.Failure -> stringResource(Res.string.settings_import_error) to MaterialTheme.colorScheme.error
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/**
 * The pre-import review: which mode, which sections, and — the part that actually
 * matters — the line-by-line diff of what changes.
 *
 * Both modes are prepared before the dialog opens, so switching between them is
 * instant and the counts update with the mode.
 */
@Composable
internal fun ImportPreviewDialog(
    sectionDetails: ImmutableMap<ImportSection, String?>,
    previews: ImportPreviews,
    onConfirm: (Set<ImportSection>, ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ImportMode.Merge) }
    var selectedSections by remember { mutableStateOf<Set<ImportSection>>(sectionDetails.keys) }
    val sortedEntries = remember(sectionDetails) { sectionDetails.entries.sortedBy { it.key } }
    val preview = previews[mode]
    val visibleDiff = remember(preview, selectedSections) {
        preview.diff.filter { it.section in selectedSections }
    }
    val changedRows = visibleDiff.filter { it.changed }
    val unchangedCount = visibleDiff.size - changedRows.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_import_preview_title))
        },
        text = {
            val importScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(importScrollState)) {
                    ImportModeOption(
                        selected = mode == ImportMode.Merge,
                        title = stringResource(Res.string.settings_import_mode_merge),
                        description = stringResource(Res.string.settings_import_mode_merge_description),
                        onClick = { mode = ImportMode.Merge },
                    )
                    Spacer(Modifier.height(4.dp))
                    ImportModeOption(
                        selected = mode == ImportMode.Replace,
                        title = stringResource(Res.string.settings_import_mode_replace),
                        description = stringResource(Res.string.settings_import_mode_replace_description),
                        onClick = { mode = ImportMode.Replace },
                    )
                    Spacer(Modifier.height(12.dp))
                    for ((section, count) in sortedEntries) {
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSections = if (section in selectedSections) {
                                        selectedSections - section
                                    } else {
                                        selectedSections + section
                                    }
                                }
                                .handCursor()
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = section in selectedSections,
                                onCheckedChange = { checked ->
                                    selectedSections = if (checked) selectedSections + section else selectedSections - section
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sectionDisplayName(section),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (count != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.settings_import_diff_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (visibleDiff.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_import_diff_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.settings_import_diff_summary, changedRows.size, unchangedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        for (row in changedRows) {
                            Text(
                                text = "${row.key}: ${row.current} → ${row.incoming}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                VerticalScrollbarForScroll(
                    scrollState = importScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections, mode) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_import_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_mcp_cancel))
            }
        },
    )
}

/** One of the two import modes, as a selectable card. */
@Composable
private fun ImportModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .handCursor(),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ExportPreviewDialog(
    sectionDetails: ImmutableMap<ImportSection, String?>,
    onConfirm: (Set<ImportSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSections by remember { mutableStateOf<Set<ImportSection>>(sectionDetails.keys) }
    val sortedEntries = remember(sectionDetails) { sectionDetails.entries.sortedBy { it.key } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_export_preview_title))
        },
        text = {
            val exportScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(exportScrollState)) {
                    for ((section, count) in sortedEntries) {
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSections = if (section in selectedSections) {
                                        selectedSections - section
                                    } else {
                                        selectedSections + section
                                    }
                                }
                                .handCursor()
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = section in selectedSections,
                                onCheckedChange = { checked ->
                                    selectedSections = if (checked) selectedSections + section else selectedSections - section
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sectionDisplayName(section),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (count != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                VerticalScrollbarForScroll(
                    scrollState = exportScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_export))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_mcp_cancel))
            }
        },
    )
}

@Composable
private fun sectionDisplayName(section: ImportSection): String = when (section) {
    ImportSection.SERVICES -> stringResource(Res.string.settings_import_section_services)
    ImportSection.SOUL -> stringResource(Res.string.settings_import_section_soul)
    ImportSection.MEMORY -> stringResource(Res.string.settings_import_section_memory)
    ImportSection.SCHEDULING -> stringResource(Res.string.settings_import_section_scheduling)
    ImportSection.HEARTBEAT -> stringResource(Res.string.settings_import_section_heartbeat)
    ImportSection.EMAIL -> stringResource(Res.string.settings_import_section_email)
    ImportSection.SMS -> stringResource(Res.string.settings_sms)
    ImportSection.SPLINTERLANDS -> "Splinterlands"
    ImportSection.TOOLS -> stringResource(Res.string.settings_import_section_tools)
    ImportSection.MCP -> stringResource(Res.string.settings_import_section_mcp)
    ImportSection.CONVERSATIONS -> stringResource(Res.string.settings_import_section_conversations)
    ImportSection.MODELS -> stringResource(Res.string.settings_import_section_models)
    ImportSection.SERVERS -> "Настройки серверов"
    ImportSection.SETTINGS -> stringResource(Res.string.settings_import_section_settings)
}
