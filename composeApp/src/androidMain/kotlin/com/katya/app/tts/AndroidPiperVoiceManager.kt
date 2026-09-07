package com.katya.app.tts

import android.content.Context
import com.katya.app.data.AppSettings
import com.katya.app.saveLargeFileToDevice
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Piper voice management on Android: voices live in `filesDir/models/piper/` as
 * `base.onnx.tflite` + optional `base.json`. Selection is persisted through
 * [AppSettings] so it outlives restarts.
 */
class AndroidPiperVoiceManager(
    private val context: Context,
    private val appSettings: AppSettings,
) : PiperVoiceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadingBaseName = MutableStateFlow<String?>(null)
    override val downloadingBaseName: StateFlow<String?> = _downloadingBaseName

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<String?>(null)
    override val downloadError: StateFlow<String?> = _downloadError

    private fun voicesDir(): File = File(context.filesDir, "models/piper").apply { mkdirs() }

    override fun getInstalledVoices(): List<PiperVoiceInfo> {
        val dir = voicesDir()
        return dir.listFiles { f -> f.isFile && f.extension == "tflite" }
            ?.mapNotNull { modelFile ->
                val base = modelFile.name.removeSuffix(".tflite")
                val jsonFile = File(dir, "$base.json").takeIf { it.isFile }
                val sampleRate = jsonFile?.let { PiperVoiceConfig.load(it)?.sampleRate } ?: 22050
                PiperVoiceInfo(
                    baseName = base,
                    sourceFileName = modelFile.name,
                    hasConfigJson = jsonFile != null,
                    sampleRate = sampleRate,
                    sizeBytes = modelFile.length(),
                )
            }
            ?.sortedBy { it.baseName }
            .orEmpty()
    }

    override fun getSelectedVoice(): String? = appSettings.getPiperSelectedVoice()

    override fun setSelectedVoice(baseName: String) {
        appSettings.setPiperSelectedVoice(baseName)
    }

    override fun startDownload(modelUrl: String) {
        val url = modelUrl.trim()
        if (url.isBlank() || _downloadingBaseName.value != null) return
        val fileName = url.substringAfterLast('/').substringBefore('?')
        if (!fileName.endsWith(".tflite")) {
            _downloadError.value = "Ссылка должна вести на .tflite файл"
            return
        }
        val base = fileName.removeSuffix(".tflite")
        scope.launch {
            _downloadingBaseName.value = base
            _downloadProgress.value = 0f
            _downloadError.value = null
            try {
                val dir = voicesDir()
                val target = File(dir, fileName)
                downloadToFile(url, target)
                // Try the companion voice config (…base.json) next to the model.
                val jsonUrl = url.removeSuffix(".tflite") + ".json"
                runCatching { downloadToFile(jsonUrl, File(dir, "$base.json")) }
                AppLogger.d("PiperManager", "Downloaded voice '$base'")
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                AppLogger.e("PiperManager", "Voice download failed: ${e.message}")
                _downloadError.value = e.message ?: "Не удалось скачать голос"
            } finally {
                _downloadingBaseName.value = null
                _downloadProgress.value = null
            }
        }
    }

    override suspend fun importVoice(fileName: String, fileBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val dir = voicesDir()
            val name = fileName.substringAfterLast('/')
            when {
                name.endsWith(".tflite") -> File(dir, name).writeBytes(fileBytes)

                name.endsWith(".json") -> File(dir, name).writeBytes(fileBytes)

                name.endsWith(".zip") -> {
                    ZipInputStream(fileBytes.inputStream()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val entryName = entry.name.substringAfterLast('/')
                                if (entryName.endsWith(".tflite") || entryName.endsWith(".json")) {
                                    File(dir, entryName).outputStream().use { out -> zip.copyTo(out) }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }

                else -> throw IllegalArgumentException("Поддерживаются .tflite, .json и .zip файлы")
            }
            _downloadError.value = null
        }
    }

    override suspend fun deleteVoice(baseName: String) {
        withContext(Dispatchers.IO) {
            val dir = voicesDir()
            File(dir, "$baseName.tflite").delete()
            File(dir, "$baseName.json").delete()
        }
        if (getSelectedVoice() == baseName) appSettings.setPiperSelectedVoice(null)
    }

    override suspend fun exportVoice(baseName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = voicesDir()
            val modelFile = File(dir, "$baseName.tflite")
            if (!modelFile.isFile) return@withContext false
            val jsonFile = File(dir, "$baseName.json")
            val zipFile = File(dir, "$baseName.zip")
            try {
                java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
                    listOfNotNull(modelFile, jsonFile.takeIf { it.isFile }).forEach { file ->
                        zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                val ok = saveLargeFileToDevice(zipFile.absolutePath, baseName, "zip")
                zipFile.delete()
                ok
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLogger.e("PiperManager", "Voice export failed: ${t.message}")
                zipFile.delete()
                false
            }
        }
    }

    private suspend fun downloadToFile(urlString: String, target: File) {
        withContext(Dispatchers.IO) {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            val code = connection.run {
                connect()
                responseCode
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            val temp = File(target.parentFile, "${target.name}.tmp")
            val buffer = ByteArray(65536)
            var total = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                        if (contentLength > 0) {
                            _downloadProgress.value = (total.toFloat() / contentLength).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            connection.disconnect()
            if (contentLength > 0 && total < contentLength * 0.95) {
                temp.delete()
                throw IllegalStateException("Загрузка неполная: $total из ~$contentLength байт")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }
}
