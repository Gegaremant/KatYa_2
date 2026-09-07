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
)
