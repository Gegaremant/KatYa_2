package com.katya.app.stt

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WakeWordPlatform {
    val isDownloading: StateFlow<Boolean>
    val downloadProgress: StateFlow<Float?>
    val wakeWordTriggered: SharedFlow<Unit>

    // STT State
    val isListeningToSpeech: StateFlow<Boolean>
    val partialSttResults: StateFlow<String>
    val finalSttResults: SharedFlow<String>

    fun isModelReady(modelUrl: String): Boolean
    fun startDownload(modelUrl: String)
    fun startListening(modelUrl: String, triggerWord: String)
    
    // Starts continuous speech recognition. If triggerWord is non-empty, it will ONLY look for wake word. 
    // If empty, it returns everything to finalSttResults.
    fun startSpeechRecognition(modelUrl: String)
    fun stopListening()
    fun triggerWakeWordResponse(vibrate: Boolean, sound: Boolean)
}

expect val sttModule: org.koin.core.module.Module
