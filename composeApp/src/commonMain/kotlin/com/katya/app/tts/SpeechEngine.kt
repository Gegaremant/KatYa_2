package com.katya.app.tts

/**
 * Unified speech-synthesis facade over every voice backend the app supports:
 * the Android system TTS stack (via [nl.marc_apps.tts.TextToSpeechInstance]),
 * an OpenAI-compatible cloud API, or an on-device Piper model.
 */
interface SpeechEngine {
    fun speak(text: String)
    fun stop()

    /**
     * Called exactly once per utterance when speech finishes naturally, is
     * interrupted by [stop], or fails. Lets the UI flip the "stop" button back
     * to a "speak" button when playback actually ends.
     */
    var onSpeechCompleted: (() -> Unit)?
}
