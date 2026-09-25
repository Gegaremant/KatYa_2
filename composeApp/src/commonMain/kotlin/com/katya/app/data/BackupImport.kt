package com.katya.app.data

import com.katya.app.BackupPayload
import com.katya.app.applyBackupPayload
import com.katya.app.tools.AppLogger

/**
 * Applies a backup the user has confirmed.
 *
 * Order matters: the archive's files land first, so the settings import can
 * already point at the restored database, and the conversation list is reloaded
 * afterwards so the UI shows what is now actually on disk.
 *
 * Returns the number of settings that could not be imported.
 */
suspend fun DataRepository.applyPreparedImport(
    json: String,
    sections: Set<ImportSection>,
    mode: ImportMode,
    payload: BackupPayload?,
): Int {
    if (payload != null && payload.hasRestorableFiles) {
        try {
            applyBackupPayload(payload)
        } catch (error: Exception) {
            AppLogger.e("BackupImport", "Failed to restore files from backup: ${error.message}")
        }
    }
    val errors = importSettingsFromJson(json, sections, mode == ImportMode.Replace)
    loadConversations()
    return errors
}
