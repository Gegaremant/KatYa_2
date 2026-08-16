package com.katya.app.stt

import kotlinx.coroutines.flow.StateFlow

interface SttController {
    val isListening: StateFlow<Boolean>
    val partialResults: StateFlow<String>
    val error: StateFlow<String?>

    fun startListening(onResult: (String) -> Unit)
    fun stopListening()
}

expect fun createSttController(): SttController
