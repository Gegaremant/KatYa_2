package com.katya.app

/**
 * A backup archive read into memory, before anything has been written to disk.
 *
 * Reading a backup and applying it are deliberately separate steps. The old
 * implementation unpacked a zip straight onto the filesystem as soon as the file
 * was picked, so opening the import preview — and then cancelling it — had
 * already replaced the conversation database and the model files. Reading now
 * only produces this; nothing touches storage until
 * [applyBackupPayload] runs, which happens after the user confirms the diff.
 */
data class BackupPayload(
    /** The `config.json` entry, or null when the archive has none. */
    val configJson: String? = null,
    /**
     * Database files by name, e.g. `conversations.db`, `conversations.db-wal`.
     * Only names from a fixed allow-list are ever accepted here.
     */
    val databaseFiles: Map<String, ByteArray> = emptyMap(),
    /**
     * Model and speech-recognition files by their path relative to the app's
     * files directory, e.g. `vosk/model.am`. Paths are validated to stay inside
     * that directory (Zip Slip).
     */
    val modelFiles: Map<String, ByteArray> = emptyMap(),
) {
    val hasRestorableFiles: Boolean get() = databaseFiles.isNotEmpty() || modelFiles.isNotEmpty()
}
