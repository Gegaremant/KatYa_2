package com.katya.app.stt

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WakeWordPlatform {
    val isDownloading: StateFlow<Boolean>
    val downloadProgress: StateFlow<Float?>
    val wakeWordTriggered: SharedFlow<Unit>

    fun isModelReady(modelUrl: String): Boolean
    fun startDownload(modelUrl: String)
    fun startListening(modelUrl: String, triggerWord: String)
    fun stopListening()
    fun triggerWakeWordResponse(vibrate: Boolean, sound: Boolean)
}

expect val sttModule: org.koin.core.module.Module
