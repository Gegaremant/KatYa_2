package com.katya.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import com.katya.app.ui.DarkColorScheme
import com.katya.app.ui.LightColorScheme
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.experimental.ExperimentalDesktopTarget
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.koin.core.context.startKoin
import java.io.File

/**
 * Запуск Кати на Windows и Linux.
 *
 * Отличие от Android ровно одно: Koin здесь не получает `androidContext`.
 * Всё остальное — тот же `appModule`, те же инструменты из общего кода и те же
 * настройки; просто они лежат в `~/.katya`, а не внутри приватного хранилища
 * приложения.
 *
 * `ExperimentalDesktopTarget` — пометка библиотеки синтезатора: её desktop-реализация
 * молода и API может измениться. Ставится здесь, а не на весь файл, чтобы
 * экспериментальность не распространялась на остальной код точки входа.
 */
@OptIn(ExperimentalDesktopTarget::class)
fun main() {
    // Skiko на Linux и Wayland игнорирует GDK_SCALE без этих двух свойств, и окно
    // рисуется в 1× на hi-res мониторе — выглядит как сломанное приложение,
    // хотя масштабирование в настройках есть.
    if (System.getProperty("sun.java2d.uiScale.enabled") == null) {
        System.setProperty("sun.java2d.uiScale.enabled", "true")
    }
    if (System.getProperty("sun.java2d.uiScale") == null) {
        System.setProperty("sun.java2d.uiScale", "auto")
    }
    if (currentPlatform is Platform.Desktop.Mac) {
        System.setProperty("apple.awt.application.appearance", "system")
    }

    val filesDir = File(getAppFilesDirectory())
    com.katya.app.tools.AppLogger.setLogFilePath(File(filesDir, "katya.log").absolutePath)
    com.katya.app.tools.AppLogger.setRootLogFilePath(File(filesDir, "root.log").absolutePath)
    installCrashLogger(filesDir)

    startKoin {
        // sandboxModule и audioModule пропущены осознанно: proot и root — это
        // телефонные вещи, а на десктопе их роль играет сама ОС. Модуль STT
        // остаётся, потому что общий код ссылается на него при старте Koin.
        modules(appModule, com.katya.app.stt.sttModule, com.katya.app.audio.audioModule)
    }
    com.katya.app.tools.AppLogger.action("Катя", "запуск · ${currentPlatform.displayName}")

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Катя",
        ) {
            // Синтезатор инициализируется после первого кадра: до этого окно
            // ещё не готово, а TTS на Windows любит падать без окна.
            var ttsReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { ttsReady = true }
            val textToSpeech = if (ttsReady) {
                rememberTextToSpeechOrNull(TextToSpeechEngine.SystemDefault)
            } else {
                null
            }
            val speechEngine = textToSpeech?.let { com.katya.app.tts.SystemTtsSpeechEngine(it) }
            App(
                navController = rememberNavController(),
                lightColorScheme = LightColorScheme,
                darkColorScheme = DarkColorScheme,
                speechEngine = speechEngine,
                isKoinStarted = true,
            )
        }
    }
}

/**
 * Ловим необработанные исключения.
 *
 * Без этого JVM печатает их в stderr, который на десктопе уходит в никуда: окно
 * просто остаётся пустым, и пользователь не знает, что произошло. Файл
 * Crash-лога лежит рядом с журналом, то есть там, куда он уже смотрит.
 */
private fun installCrashLogger(filesDir: File) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            val stack = "Thread: ${thread.name}\n${throwable.stackTraceToString()}"
            com.katya.app.tools.AppLogger.e("CRASH", stack)
            val dir = File(filesDir, "crash_logs").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                .format(java.util.Date())
            File(dir, "crash_$stamp.txt").writeText("$stack\n")
            // Храним 10 последших: старые не помогают, а место занимают.
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(10)?.forEach { it.delete() }
        }
        previous?.uncaughtException(thread, throwable) ?: throw throwable
    }
}
