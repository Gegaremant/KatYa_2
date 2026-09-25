package com.katya.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import com.katya.app.BackupPayload
import com.katya.app.data.ImportMode
import com.katya.app.data.ImportPreviews
import com.katya.app.data.ImportSection
import com.katya.app.data.SharedJson
import com.katya.app.data.detectImportSections
import com.katya.app.readBackupZip
import com.katya.app.tools.AppLogger
import com.katya.app.ui.settings.ImportResult
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject

/**
 * A picked backup file, prepared for review but not yet applied.
 *
 * Reading the archive and computing the diff both happen here, while nothing has
 * been written: the database and the model files only move into place after the
 * user confirms.
 */
@Immutable
internal class PendingBackupImport(
    /** The settings half of the backup, as JSON text. */
    val json: String,
    /** The archive's files, still in memory. Null for a plain `.json` file. */
    val payload: BackupPayload?,
    /** The diff for both import modes, computed up front so switching is instant. */
    val previews: ImportPreviews,
    /** Which sections the file actually has something for. */
    val sectionDetails: Map<ImportSection, String?>,
)

/** Drives the pick → review → apply flow, shared by settings and first-run restore. */
@Stable
internal class BackupImportController(
    val launchPicker: () -> Unit,
    val pending: PendingBackupImport?,
    val result: ImportResult?,
    val dismiss: () -> Unit,
    val confirm: (Set<ImportSection>, ImportMode) -> Unit,
)

/**
 * Builds a [BackupImportController].
 *
 * [preparePreview] supplies the diff for both modes; [applyImport] performs the
 * actual import. Keeping the diff computation here — rather than in the dialog —
 * means the user can flip between "Дополнить" and "Заменить" without a round trip
 * through the settings store.
 */
@Composable
internal fun rememberBackupImportController(
    preparePreview: (String, Set<ImportSection>) -> ImportPreviews,
    applyImport: suspend (String, Set<ImportSection>, ImportMode, BackupPayload?) -> ImportResult,
    onDone: (ImportResult) -> Unit = {},
): BackupImportController {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingBackupImport?>(null) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    val isPreview = LocalInspectionMode.current

    val picker = if (isPreview) {
        null
    } else {
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("zip", "json")),
        ) { file ->
            if (file == null) return@rememberFilePickerLauncher
            scope.launch {
                runCatching {
                    val bytes = file.readBytes()
                    val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
                    // Reading only: nothing is written before the user confirms.
                    val payload = if (isZip) {
                        withContext(Dispatchers.IO) { readBackupZip(bytes) } ?: error("invalid backup zip")
                    } else {
                        null
                    }
                    val json = payload?.configJson ?: bytes.decodeToString()
                    val jsonObject = SharedJson.parseToJsonElement(json).jsonObject
                    val previews = preparePreview(json, ImportSection.entries.toSet())
                    val sectionDetails = if (previews.merge.hasSnapshot) {
                        // A full snapshot can speak for every section, even the ones
                        // that are empty — a missing conversation list means "none",
                        // not "unknown".
                        ImportSection.entries.associateWith { null }
                    } else {
                        detectImportSections(jsonObject)
                    }
                    AppLogger.i("BackupImport", "Backup read: snapshot=${previews.merge.hasSnapshot}, files=${payload?.hasRestorableFiles == true}")
                    pending = PendingBackupImport(json, payload, previews, sectionDetails)
                }.onFailure { error ->
                    AppLogger.e("BackupImport", "Failed to read backup: ${error.message}")
                    result = ImportResult.Failure
                }
            }
        }
    }

    return BackupImportController(
        launchPicker = { picker?.launch() },
        pending = pending,
        result = result,
        dismiss = { pending = null },
        confirm = { sections, mode ->
            val current = pending ?: return@BackupImportController
            pending = null
            scope.launch {
                val importResult = applyImport(current.json, sections, mode, current.payload)
                result = importResult
                onDone(importResult)
            }
        },
    )
}
