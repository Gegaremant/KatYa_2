package com.katya.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.katya.app.data.TaskScheduler
import com.katya.app.device.initDeviceProviders
import com.katya.app.sandbox.sandboxModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KatyaApplication : Application() {

    private val taskScheduler: TaskScheduler by inject()

    override fun onCreate() {
        super.onCreate()
        context = this
        initDeviceProviders(this)
        installUncaughtExceptionLogger()
        startKoin {
            androidContext(this@KatyaApplication)
            modules(appModule, sandboxModule, com.katya.app.stt.sttModule, com.katya.app.audio.audioModule)
        }
        // Track app foreground state so the scheduler only pushes a heartbeat notification
        // when the in-app banner isn't visible. ViewModel lifecycle is the wrong signal —
        // it survives backgrounding and only clears on Activity destruction.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                taskScheduler.appInForeground = true
            }
            override fun onStop(owner: LifecycleOwner) {
                taskScheduler.appInForeground = false
            }
        })
    }

    companion object {
        lateinit var context: android.content.Context
    }

    /**
     * Global crash logging: every uncaught Kotlin/Java exception (e.g. during external
     * model load) is written both to the in-memory AppLogger and to a persistent crash
     * log file under the app dir, then delegated to the previous handler so the OS
     * default behavior (process restart) is preserved.
     */
    private fun installUncaughtExceptionLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = "Thread: ${thread.name}\n${throwable.stackTraceToString()}"
            runCatching {
                com.katya.app.tools.AppLogger.e("CRASH", stack)
                appendCrashLog(stack)
            }
            previous?.uncaughtException(thread, throwable)
                ?: throw throwable
        }
    }

    private fun appendCrashLog(stack: String) {
        val dir = java.io.File(filesDir, "crash_logs")
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(dir, "crash_$stamp.txt")
        file.writeText("$stack\n")
        // Keep only the 10 most recent crash logs.
        dir.listFiles()?.sortedBy { it.lastModified() }?.let { logs ->
            logs.dropLast(10).forEach { it.delete() }
        }
    }
}
