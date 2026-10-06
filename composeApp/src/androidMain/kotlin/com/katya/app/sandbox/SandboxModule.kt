package com.katya.app.sandbox

import android.content.Context
import com.katya.app.components.AndroidComponentDownloadLauncher
import com.katya.app.components.BundledComponents
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

    // Assets читаются только через Context, а про встроенные компоненты спрашивает общий
    // UI, где контекста нет. Отдаём его один раз при старте — дальше BundledComponents
    // сам знает, что смотреть. createdAtStart обязателен: определения Koin создаются
    // лениво, а контекст нужен до первого запроса из UI.
    single(createdAtStart = true) {
        BundledComponents.attachContext(androidContext())
        NativeDiagnostics.attach(androidContext())
    }
}
