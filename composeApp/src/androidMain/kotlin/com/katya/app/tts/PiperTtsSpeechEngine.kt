package com.katya.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.katya.app.data.AppSettings
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device TTS engine running a Piper voice via LiteRT.
 *
 * Expects the voice files inside `filesDir/models/piper/`:
 *  - `ru_RU-irina-medium.onnx.tflite` — the converted LiteRT model
 *  - `ru_RU-irina-medium.onnx.json`  — voice descriptor (sample rate, phoneme map)
 *
 * The active voice comes from [AppSettings.getPiperSelectedVoice]; when that base
 * name changes, the engine reloads the model on the next [speak].
 *
 * Phonemes come from the built-in rule-based [PiperPhonemizer] (no espeak-ng on
 * Android), so speech is intelligible but not as natural as the reference Piper.
 */
class PiperTtsSpeechEngine(
    private val context: Context,
    private val appSettings: AppSettings,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : SpeechEngine {

    private data class VoiceResources(
        val modelFile: File,
        val config: PiperVoiceConfig,
    )

    @Volatile private var resources: VoiceResources? = null

    @Volatile private var loadedBaseName: String? = null

    @Volatile private var interpreter: InterpreterApi? = null
    private var activeJob: Job? = null
    private var audioTrack: AudioTrack? = null

    override var onSpeechCompleted: (() -> Unit)? = null

    // Guards stale completions: only the newest utterance may fire the callback.
    private var generation = 0L

    /** Tries to (re)load the Piper voice; returns a user-facing error or null on success. */
    fun refresh(): String? = try {
        val dir = File(context.filesDir, "models/piper").apply { mkdirs() }
        val candidates = dir.listFiles { f -> f.extension == "tflite" }?.filter { it.isFile }.orEmpty()
        val selected = appSettings.getPiperSelectedVoice()
        val modelFile = candidates.firstOrNull { it.name.removeSuffix(".tflite") == selected }
            ?: candidates.firstOrNull()
            ?: return "Модель Piper не найдена. Положите .tflite и .onnx.json модели в ${dir.absolutePath}"
        val baseName = modelFile.name.removeSuffix(".tflite")
        val jsonFile = File(modelFile.parent, baseName + ".json")
            .takeIf { it.isFile }
            ?: candidates.firstOrNull { it.extension == "json" }
        val config = if (jsonFile != null) {
            PiperVoiceConfig.load(jsonFile)
                ?: return "Не удалось прочитать голосовой дескриптор ${jsonFile.name}"
        } else {
            PiperVoiceConfig(22_050, 1, linkedMapOf(" " to intArrayOf(0)), "ru")
        }

        interpreter?.close()
        val opts = Options().setNumThreads(2).setUseXNNPACK(true)
        val newInterpreter = runCatching {
            InterpreterApi.create(modelFile, opts)
        }.getOrElse { e ->
            return "Не удалось загрузить модель LiteRT: ${e.message}"
        }

        resources = VoiceResources(modelFile, config)
        interpreter = newInterpreter
        loadedBaseName = baseName
        null
    } catch (e: Exception) {
        "Ошибка Piper: ${e.message}"
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        // Reload when the user switched the voice in settings between utterances.
        val selected = appSettings.getPiperSelectedVoice()
        if (selected != null && selected != loadedBaseName) refresh()
        val res = resources ?: run {
            refresh()
            resources ?: return
        }
        val myGeneration = ++generation
        activeJob?.cancel()
        activeJob = scope.launch {
            stopPlayback()
            val sampleRate = res.config.sampleRate
            val pcm = synthesizePcm(res, text)
            if (pcm.isNotEmpty()) {
                val track = playPcm(pcm, sampleRate)
                if (track != null) awaitPlaybackEnd(track)
            }
            if (myGeneration == generation) {
                onSpeechCompleted?.invoke()
            }
        }
    }

    override fun stop() {
        generation++
        activeJob?.cancel()
        stopPlayback()
        onSpeechCompleted?.invoke()
    }

    /** Suspends until the given track finishes playing. */
    private suspend fun awaitPlaybackEnd(track: AudioTrack) {
        while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            delay(50)
        }
    }

    private fun synthesizePcm(res: VoiceResources, text: String): ByteArray {
        val interpreter = interpreter ?: return ByteArray(0)
        val config = res.config
        val symbols = PiperPhonemizer.phonemes(text.trim().take(220))
        if (symbols.isEmpty()) return ByteArray(0)

        val phonemeIds = symbols.mapNotNull { symbol ->
            config.phonemeId(symbol) ?: config.phonemeIdMap[" "]?.firstOrNull()
        }
        if (phonemeIds.isEmpty()) return ByteArray(0)

        val inputCount = interpreter.inputTensorCount
        val names = (0 until inputCount).map { interpreter.getInputTensor(it).name() }
        val useSingleInput = inputCount == 1 || names.all { it == "serving_default_input_ids:0" || it == "input_ids" || it == "input" }

        val inputArray: Array<Any>
        val ids = phonemeIds.map { it.toLong() }.toLongArray()
        inputArray = if (useSingleInput) {
            arrayOf(ids as Any)
        } else {
            arrayOf(ids as Any, 0.667f as Any, 1.0f as Any, longArrayOf(0) as Any)
        }
        val output = ArrayList<Float>()
        val outputMap = HashMap<Int, Any>()
        outputMap[0] = output

        try {
            interpreter.runForMultipleInputsOutputs(inputArray, outputMap)
        } catch (e: Exception) {
            AppLogger.w("PiperTts", "Inference failed: $e")
            return ByteArray(0)
        }
        if (output.isEmpty()) return ByteArray(0)

        // Trim leading/trailing silence (values below the spoken threshold).
        val threshold = 0.01f
        var start = 0
        var end = output.size - 1
        while (start < end && kotlin.math.abs(output[start]) < threshold) start++
        while (end > start && kotlin.math.abs(output[end]) < threshold) end--
        if (end <= start) return ByteArray(0)

        val pcm = ByteArray((end - start + 1) * 2)
        var i = 0
        for (idx in start..end) {
            val s = (output[idx].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            pcm[i++] = (s.toInt() and 0xFF).toByte()
            pcm[i++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun playPcm(pcm: ByteArray, sampleRate: Int): AudioTrack? {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack = track
        return try {
            track.write(pcm, 0, pcm.size)
            track.play()
            track
        } catch (e: Exception) {
            AppLogger.w("PiperTts", "Playback failed: $e")
            stopPlayback()
            null
        }
    }

    private fun stopPlayback() {
        audioTrack?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.runCatching { stop() }
        audioTrack?.runCatching { release() }
        audioTrack = null
    }

    private companion object {
        init {
            AppLogger.w("PiperTts", "Piper engine loaded")
        }
    }
}
