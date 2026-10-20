package com.katya.app.tts

import android.media.MediaPlayer
import com.katya.app.data.AppSettings
import com.katya.app.network.Requests
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow

/**
 * Cloud TTS engine: synthesizes speech through an OpenAI-compatible
 * `/audio/speech` endpoint and plays the returned audio (usually MP3) with
 * [MediaPlayer]. Text-to-speech round-trips happen on a background scope; every
 * new [speak] cancels any playback still in flight.
 */
class CloudTtsSpeechEngine(
    private val appSettings: AppSettings,
    private val requests: Requests,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : SpeechEngine {

    @Volatile
    private var player: MediaPlayer? = null
    private var activeJob: Job? = null

    override var onSpeechCompleted: (() -> Unit)? = null

    // Guards stale completions: only the newest utterance may fire the callback.
    private var generation = 0L

    @Volatile
    private var tuning: VoiceTuning = VoiceTuning()

    // У облачного синтеза есть скорость, но нет тембра: `/audio/speech` тембра
    // не знает. Поэтому ползунок тембра на этом движке честно гаснет.
    override val voiceTuningSupport: VoiceTuningSupport = VoiceTuningSupport.RATE_ONLY

    override fun applyTuning(tuning: VoiceTuning) {
        this.tuning = tuning
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val myGeneration = ++generation
        activeJob?.cancel()
        AudioFocusController.acquire()
        activeJob = scope.launch {
            stopInternal()
            val result = requests.synthesizeSpeech(
                url = appSettings.getCloudTtsUrl(),
                apiKey = appSettings.getCloudTtsKey(),
                model = appSettings.getCloudTtsModel(),
                voice = appSettings.getCloudTtsVoice(),
                text = text,
                // Скорость в API измеряется множителем 0.25…4.0, поэтому проценты
                // Кати переводятся в него через корень четвёртой степени: −100 → 0.25,
                // 0 → 1, +100 → 4.
                speed = 4f.pow(tuning.ratePercent / 100f),
            )
            result.onSuccess { bytes ->
                play(bytes, myGeneration)
            }.onFailure { e ->
                AppLogger.w("CloudTts", "Synthesis failed: $e")
                if (myGeneration == generation) {
                    AudioFocusController.release()
                    onSpeechCompleted?.invoke()
                }
            }
        }
    }

    override fun stop() {
        generation++
        activeJob?.cancel()
        stopInternal()
        AudioFocusController.release()
        onSpeechCompleted?.invoke()
    }

    private fun play(bytes: ByteArray, myGeneration: Long) {
        if (bytes.isEmpty()) return
        try {
            val tmp = File.createTempFile("katya_tts", ".mp3")
            tmp.writeBytes(bytes)
            val p = MediaPlayer()
            p.setDataSource(tmp.absolutePath)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener {
                cleanup(p)
                if (myGeneration == generation) {
                    AudioFocusController.release()
                    onSpeechCompleted?.invoke()
                }
            }
            p.setOnErrorListener { player, _, _ ->
                cleanup(player)
                if (myGeneration == generation) {
                    AudioFocusController.release()
                    onSpeechCompleted?.invoke()
                }
                true
            }
            p.prepareAsync()
            player = p
        } catch (e: Exception) {
            AppLogger.w("CloudTts", "Playback failed: $e")
            if (myGeneration == generation) {
                AudioFocusController.release()
                onSpeechCompleted?.invoke()
            }
        }
    }

    private fun cleanup(p: MediaPlayer) {
        if (player === p) player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private fun stopInternal() {
        val p = player
        player = null
        if (p != null) {
            runCatching { p.stop() }
            runCatching { p.release() }
        }
    }
}
