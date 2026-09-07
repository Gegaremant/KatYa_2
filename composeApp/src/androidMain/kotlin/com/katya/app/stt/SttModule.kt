package com.katya.app.stt

import org.koin.dsl.module

actual val sttModule = module {
    single<WakeWordPlatform> { VoskWakeWordManager(get()) }
    single { CloudSttEngine(get(), get()) }
    single { com.katya.app.tts.CloudTtsSpeechEngine(get(), get()) }
    single { com.katya.app.tts.PiperTtsSpeechEngine(get(), get()) }

    // Piper voice management singleton shared by SettingsViewModel, the TTS engine
    // and the backup flow; resolves Context + AppSettings from Koin.
    single<com.katya.app.tts.PiperVoiceManager> {
        com.katya.app.tts.AndroidPiperVoiceManager(get(), get())
    }

    // Register the STT controller as a shared singleton so that both the chat UI and the
    // foreground DaemonService (watch/hearable integration) resolve the same instance.
    // Previously this was only reachable via the createSttController() factory, which left
    // DaemonService's `by inject()` undefinable and crashed the daemon with a Koin
    // NoDefinitionFoundException at startup.
    single<SttController> { createSttController() }
}
