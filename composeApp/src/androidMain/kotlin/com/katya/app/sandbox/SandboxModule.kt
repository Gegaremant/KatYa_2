package com.katya.app.sandbox

import android.content.Context
import com.katya.app.components.AndroidComponentDownloadLauncher
import com.katya.app.components.ComponentDownloadLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sandboxModule = module {
    single<LinuxSandboxManager> { LinuxSandboxManager(androidContext(), get(), get(), get()) }
    single<VlessProxyManager> { VlessProxyManager(get(), get(), get()) }
    single<FreeDeepSeekManager> { FreeDeepSeekManager(get(), get()) }
    single<ComponentDownloadLauncher> {
        AndroidComponentDownloadLauncher(androidContext())
    }
}
