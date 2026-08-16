package com.katya.app.stt

import org.koin.dsl.module

actual val sttModule = module {
    single<WakeWordPlatform> { VoskWakeWordManager(get()) }
}
