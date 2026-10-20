package com.katya.app.tts

import com.katya.app.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance

/**
 * Adapter that forwards [speak]/[stop] to a [TextToSpeechInstance] (Android
 * system TTS or RHVoice). Keeps the rest of the app decoupled from the
 * nl.marc_apps library so cloud and on-device engines can share the same API.
 */
class SystemTtsSpeechEngine(
    val instance: TextToSpeechInstance,
) : SpeechEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override var onSpeechCompleted: (() -> Unit)? = null

    // Guards stale completions: only the newest utterance may fire the callback.
    private var generation = 0L

    override val voiceTuningSupport: VoiceTuningSupport = VoiceTuningSupport.FULL

    override fun applyTuning(tuning: VoiceTuning) {
        // Ноль процентов — это голос Кати, а не голос системы: скорость по
        // умолчанию у неё 1.5, а не 1.0. Иначе после перехода на новую шкалу
        // Катя заговорила бы в полтора раза медленнее, чем раньше.
        instance.rate = AppSettings.DEFAULT_TTS_RATE * tuning.rateFactor
        instance.pitch = AppSettings.DEFAULT_TTS_PITCH * tuning.pitchFactor
    }

    override fun speak(text: String) {
        val myGeneration = ++generation
        AudioFocusController.acquire()
        scope.launch {
            // Callback overload: fires when the utterance actually completes.
            instance.say(text, true) { result ->
                if (myGeneration == generation && result.isSuccess) {
                    AudioFocusController.release()
                    onSpeechCompleted?.invoke()
                }
            }
        }
    }

    override fun stop() {
        generation++
        instance.stop()
        AudioFocusController.release()
        onSpeechCompleted?.invoke()
    }
}
