package com.katya.app.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Metadata for a single installed Piper voice (a `*.tflite` file + optional `*.json` config).
 */
data class PiperVoiceInfo(
    val baseName: String,
    val sourceFileName: String,
    val hasConfigJson: Boolean,
    val sampleRate: Int,
    val sizeBytes: Long,
)

/**
 * Owns Piper conversational voices: listing, selection, download from a direct URL,
 * import/delete/export. The selected voice is persisted via [com.katya.app.data.AppSettings],
 * so it survives restarts and is picked up by [PiperTtsSpeechEngine].
 */
interface PiperVoiceManager {

    fun getInstalledVoices(): List<PiperVoiceInfo>

    fun getSelectedVoice(): String?

    fun setSelectedVoice(baseName: String)

    /** Base name of the voice currently being downloaded, or null when idle. */
    val downloadingBaseName: StateFlow<String?>

    /** 0f..1f progress of the current download, or null when idle. */
    val downloadProgress: StateFlow<Float?>

    /** Non-null when the last download/import failed; cleared on the next attempt. */
    val downloadError: StateFlow<String?>

    /** Starts a download from a direct `https://…` link to a `*.tflite` file. */
    fun startDownload(modelUrl: String)

    /**
     * Imports a voice file picked by the user: `*.tflite`, `*.json` or a `*.zip`
     * containing both.
     */
    suspend fun importVoice(fileName: String, fileBytes: ByteArray)

    suspend fun deleteVoice(baseName: String)

    /** Exports the voice (tflite + json) as a zip to the user-selected location. */
    suspend fun exportVoice(baseName: String): Boolean
}
