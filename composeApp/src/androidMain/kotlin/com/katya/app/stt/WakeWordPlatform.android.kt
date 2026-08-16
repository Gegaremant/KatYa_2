@file:Suppress("ktlint:standard:filename")

package com.katya.app.stt

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.katya.app.inference.ModelDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class VoskWakeWordManager(private val context: Context) : WakeWordPlatform {

    private var currentModelUrl: String = ""
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadJob: Job? = null

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    override val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _wakeWordTriggered = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    override val wakeWordTriggered: kotlinx.coroutines.flow.SharedFlow<Unit> = _wakeWordTriggered

    private val _isListeningToSpeech = MutableStateFlow(false)
    override val isListeningToSpeech: StateFlow<Boolean> = _isListeningToSpeech.asStateFlow()

    private val _partialSttResults = MutableStateFlow("")
    override val partialSttResults: StateFlow<String> = _partialSttResults.asStateFlow()

    private val _finalSttResults = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    override val finalSttResults: kotlinx.coroutines.flow.SharedFlow<String> = _finalSttResults

    private var speechService: SpeechService? = null
    private var activeModel: Model? = null

    private fun getModelDirectory(url: String): File {
        val modelName = url.substringAfterLast("/").substringBeforeLast(".zip")
        return File(context.filesDir, "vosk/$modelName")
    }

    override fun isModelReady(modelUrl: String): Boolean {
        if (modelUrl.isEmpty()) return false
        return File(getModelDirectory(modelUrl), "am/final.mdl").exists()
    }

    override fun startDownload(modelUrl: String) {
        if (modelUrl.isEmpty() || isModelReady(modelUrl) || _isDownloading.value) return
        currentModelUrl = modelUrl

        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            var notificationStarted = false
            val zipFile = File(context.cacheDir, "vosk_model.tmp.zip")

            try {
                val connection = URL(currentModelUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Download failed: HTTP $responseCode")
                }

                startDownloadNotificationService()
                notificationStarted = true

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: 45_000_000L
                val buffer = ByteArray(65536)
                var totalBytesRead = 0L
                var lastNotifiedPercent = -1

                connection.inputStream.use { input ->
                    zipFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val percent = (totalBytesRead * 100 / contentLength).toInt().coerceIn(1, 100)
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                _downloadProgress.value = percent / 100f
                                updateDownloadNotificationProgress(percent, "Скачивание Vosk...")
                            }
                        }
                    }
                }
                connection.disconnect()

                _downloadProgress.value = 1f
                updateDownloadNotificationProgress(100, "Распаковка...")

                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        ensureActive()
                        val name = entry.name
                        val index = name.indexOf("/")
                        val relativeName = if (index != -1) name.substring(index + 1) else name

                        if (relativeName.isNotEmpty()) {
                            val destFile = File(getModelDirectory(currentModelUrl), relativeName)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()
            } catch (e: Throwable) {
                if (zipFile.exists()) zipFile.delete()
                if (getModelDirectory(currentModelUrl).exists()) getModelDirectory(currentModelUrl).deleteRecursively()
                if (e is CancellationException) throw e
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
                if (notificationStarted) stopDownloadNotificationService()
            }
        }
    }

    override fun startListening(modelUrl: String, triggerWord: String) {
        startVoskService(modelUrl, triggerWord)
    }

    override fun startSpeechRecognition(modelUrl: String) {
        startVoskService(modelUrl, "")
    }

    private fun startVoskService(modelUrl: String, triggerWord: String) {
        if (modelUrl.isEmpty() || !isModelReady(modelUrl)) return
        currentModelUrl = modelUrl

        try {
            stopListening()

            if (activeModel == null) {
                activeModel = Model(getModelDirectory(modelUrl).absolutePath)
            }
            val recognizer = Recognizer(activeModel, 16000.0f)

            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : org.vosk.android.RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    handleVoskResult(hypothesis, triggerWord, isPartial = true)
                }
                override fun onResult(hypothesis: String?) {
                    handleVoskResult(hypothesis, triggerWord, isPartial = false)
                }
                override fun onFinalResult(hypothesis: String?) {
                    handleVoskResult(hypothesis, triggerWord, isPartial = false, isFinal = true)
                }
                override fun onError(e: Exception?) {
                    _isListeningToSpeech.value = false
                }
                override fun onTimeout() {
                    _isListeningToSpeech.value = false
                }
            })
            if (triggerWord.isEmpty()) {
                _isListeningToSpeech.value = true
                _partialSttResults.value = ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isListeningToSpeech.value = false
        }
    }

    private fun handleVoskResult(hypothesis: String?, triggerWord: String, isPartial: Boolean, isFinal: Boolean = false) {
        val text = hypothesis?.let {
            // Vosk returns JSON like: { "text": "hello" } or { "partial": "hello" }
            try {
                val json = org.json.JSONObject(it)
                if (isPartial) json.optString("partial", "") else json.optString("text", "")
            } catch (e: Exception) {
                ""
            }
        }?.trim() ?: return

        if (text.isEmpty()) return

        if (triggerWord.isNotEmpty()) {
            // Wake word mode
            val lowerHypothesis = text.lowercase()
            val lowerTrigger = triggerWord.lowercase()
            if (lowerHypothesis.contains(lowerTrigger)) {
                stopListening()
                _wakeWordTriggered.tryEmit(Unit)
            }
        } else {
            // STT mode
            if (isPartial) {
                _partialSttResults.value = text
            } else {
                _partialSttResults.value = ""
                _finalSttResults.tryEmit(text)
                if (isFinal) {
                    _isListeningToSpeech.value = false
                }
            }
        }
    }

    override fun stopListening() {
        try {
            speechService?.cancel()
            speechService?.shutdown()
            speechService = null
            _isListeningToSpeech.value = false
            _partialSttResults.value = ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun triggerWakeWordResponse(vibrate: Boolean, sound: Boolean) {
        if (vibrate) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(150)
            }
        }
        if (sound) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val intent = Intent(Intent.ACTION_ASSIST).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            setPackage(context.packageName)
        }
        context.startActivity(intent)
    }

    private fun startDownloadNotificationService() {
        try {
            val intent = Intent(context, ModelDownloadService::class.java)
            context.startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopDownloadNotificationService() {
        context.stopService(Intent(context, ModelDownloadService::class.java))
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ModelDownloadService.NOTIFICATION_ID)
    }

    private fun updateDownloadNotificationProgress(percent: Int, text: String = "") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, "kai_model_download_channel")
            .setContentTitle("Katya Wake Word")
            .setContentText(if (text.isNotEmpty()) text else "Скачивание $percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, false)
        notificationManager.notify(ModelDownloadService.NOTIFICATION_ID, builder.build())
    }
}
