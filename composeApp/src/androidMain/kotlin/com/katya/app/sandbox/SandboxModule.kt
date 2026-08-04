package com.katya.app.sandbox

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sandboxModule = module {
    single<LinuxSandboxManager> { LinuxSandboxManager(androidContext(), get()) }
    single<VlessProxyManager> { VlessProxyManager(get(), get()) }
    single<FreeDeepSeekManager> { FreeDeepSeekManager(get(), get()) }
}
