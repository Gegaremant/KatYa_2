package com.katya.app.stt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wake-word на десктопе не подключено.
 *
 * На Android это Vosk: обученная модель скачивается на телефон, а детектор
 * работает локально. На десктопе обучающих моделей нет вовсе, а «будить»
 * компьютер микрофоном — отдельная задача со своим поведением, которую
 * нельзя объявить готовой заглушкой.
 *
 * Объект не выкинут: на него ссылается `appModule` при создании ViewModel, и
 * отсутствие было бы падением на старте, а не «отключённой функцией».
 */
internal object DesktopUnavailableWakeWordPlatform : WakeWordPlatform {
    override val isDownloading = MutableStateFlow(false)
    override val downloadProgress = MutableStateFlow<Float?>(null)
    override val wakeWordTriggered: SharedFlow<Unit> = MutableSharedFlow()
    override val isListeningToSpeech = MutableStateFlow(false)
    override val partialSttResults = MutableStateFlow("")
    override val finalSttResults: SharedFlow<String> = MutableSharedFlow()

    override fun isModelReady(modelUrl: String): Boolean = false

    override fun startDownload(modelUrl: String) {
        downloadProgress.value = null
    }

    override fun startListening(modelUrl: String, triggerWord: String) {
        // Модели нет — слушать нечего.
    }

    override fun startSpeechRecognition(modelUrl: String) {
        // То же: локальной модели распознавания на десктопе нет.
    }

    override fun stopListening() {
        // Нечего останавливать.
    }

    override fun triggerWakeWordResponse(vibrate: Boolean, sound: Boolean) {
        // Wake-word на десктопе не подключён.
    }
}
