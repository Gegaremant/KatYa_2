package com.katya.app.tts

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

    override fun speak(text: String) {
        val myGeneration = ++generation
        AudioDuckingController.duck()
        scope.launch {
            // Callback overload: fires when the utterance actually completes.
            instance.say(text, true) { result ->
                if (myGeneration == generation && result.isSuccess) {
                    AudioDuckingController.unduck()
                    onSpeechCompleted?.invoke()
                }
            }
        }
    }

    override fun stop() {
        generation++
        instance.stop()
        AudioDuckingController.unduck()
        onSpeechCompleted?.invoke()
    }
}
