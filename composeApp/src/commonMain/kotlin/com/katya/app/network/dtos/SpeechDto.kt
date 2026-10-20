package com.katya.app.network.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SpeechTranscriptionResponseDto(
    val text: String = "",
)

@Serializable
data class SpeechSynthesisRequestDto(
    val model: String,
    val input: String,
    val voice: String,
    /**
     * Скорость речи, 0.25…4.0.
     *
     * Параметр есть в OpenAI-совместимом `/audio/speech`; у движков, которые его
     * не понимают, лишнее поле просто игнорируется.
     */
    val speed: Float = 1.0f,
)
