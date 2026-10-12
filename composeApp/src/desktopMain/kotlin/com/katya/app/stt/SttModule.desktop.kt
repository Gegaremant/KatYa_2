package com.katya.app.stt

import com.katya.app.tts.PiperVoiceInfo
import com.katya.app.tts.PiperVoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Модуль десктопной версии: только то, что работает без Android.
 *
 * `WakeWordPlatform` и `SttController` описаны выше — они честно отвечают, что
 * на десктопе не работают. `PiperVoiceManager` тоже пустой, но не выкинут:
 * на него ссылается `appModule` при создании `RemoteDataRepository`, и если его
 * не зарегистрировать, Koin упадёт на старте, а не «мягко отключит функцию».
 */
actual val sttModule: Module = module {
    single<WakeWordPlatform> { DesktopUnavailableWakeWordPlatform }
    single<SttController> { createSttController() }
    single<PiperVoiceManager> { DesktopPiperVoiceManager() }
}

actual fun createSttController(): SttController = DesktopSttController()

private class DesktopSttController : SttController {
    override val isListening = MutableStateFlow(false)
    override val partialResults = MutableStateFlow("")
    override val error = MutableStateFlow<String?>(null)

    override fun startListening(onResult: (String) -> Unit) {
        // Ошибка в потоке, а не no-op: интерфейс покажет её пользователю, и тот
        // не будет гадать, почему голос не слышно. Молчание выглядит как зависшее
        // приложение.
        error.value = "Распознавание речи на десктопе пока не поддерживается"
    }

    override fun stopListening() {
        isListening.value = false
    }
}

/**
 * Голоса Piper на десктопе.
 *
 * Сама модель синтеза лежит в общем коде, и файлы `.tflite` на десктопе
 * применимы — но воспроизводит их `PiperTtsSpeechEngine`, написанная под
 * `android.media.AudioTrack`. Пока такого проигрывателя для десктопа нет,
 * заявлять о поддержке голосов было бы враньём: интерфейс покажет список, и
 * звука не будет.
 */
private class DesktopPiperVoiceManager : PiperVoiceManager {
    override val downloadingBaseName = MutableStateFlow<String?>(null)
    override val downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadError = MutableStateFlow<String?>(null)

    override fun getInstalledVoices(): List<PiperVoiceInfo> = emptyList()

    override fun getSelectedVoice(): String? = null

    override fun setSelectedVoice(baseName: String) {
        // Выбирать нечего: список пуст.
    }

    override fun startDownload(modelUrl: String) {
        downloadError.value = "Голоса Piper на десктопе пока не поддерживаются"
    }

    override suspend fun importVoice(fileName: String, fileBytes: ByteArray) {
        downloadError.value = "Голоса Piper на десктопе пока не поддерживаются"
    }

    override suspend fun deleteVoice(baseName: String) {
        // Удалять нечего.
    }

    override suspend fun exportVoice(baseName: String): Boolean = false
}
