package com.katya.app.audio

import org.koin.core.module.Module
import org.koin.dsl.module

actual val audioModule: Module = module {
    single<AudioHelper> { 
        object : AudioHelper {
            override fun requestExclusiveFocus() {}
            override fun abandonFocus() {}
        }
    }
}
