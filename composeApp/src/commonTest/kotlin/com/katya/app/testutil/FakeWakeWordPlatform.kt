package com.katya.app.testutil

import com.katya.app.stt.WakeWordPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeWakeWordPlatform : WakeWordPlatform {
    override val isDownloading: StateFlow<Boolean> = MutableStateFlow(false)
    override val downloadProgress: StateFlow<Float?> = MutableStateFlow(null)
    override fun isModelReady(modelUrl: String): Boolean = false
    override fun startDownload(modelUrl: String) {}
    override val wakeWordTriggered: SharedFlow<Unit> = MutableSharedFlow()
    override fun startListening(modelUrl: String, triggerWord: String) {}
    override fun startSpeechRecognition(modelUrl: String) {}
    override fun stopListening() {}
    override fun triggerWakeWordResponse(vibrate: Boolean, sound: Boolean) {}

    override val isListeningToSpeech: StateFlow<Boolean> = MutableStateFlow(false)
    override val partialSttResults: StateFlow<String> = MutableStateFlow("")
    override val finalSttResults: SharedFlow<String> = MutableSharedFlow()
}
