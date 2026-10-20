package com.katya.app.tts

/**
 * Unified speech-synthesis facade over every voice backend the app supports:
 * the Android system TTS stack (via [nl.marc_apps.tts.TextToSpeechInstance]),
 * an OpenAI-compatible cloud API, an on-device Piper model, or the desktop
 * synthesizers of the operating system (speech-dispatcher, SAPI5, `say`).
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

    /**
     * Применяет скорость и тембр.
     *
     * Вызывается при каждом изменении ползунков и один раз при создании движка.
     * Движок, который не умеет что-то из этого, сообщает об этом через
     * [voiceTuningSupport] и не притворяется, что настройка сработала.
     */
    fun applyTuning(tuning: VoiceTuning) {}

    /** Что этот движок действительно умеет менять. */
    val voiceTuningSupport: VoiceTuningSupport get() = VoiceTuningSupport.NONE
}
