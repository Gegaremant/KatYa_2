package com.katya.app.tools

import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

private val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")

private const val DEFAULT_TIMEOUT_MS = 30_000L

/**
 * Выполнение команды на самой машине.
 *
 * На Android здесь два разных исполнителя: `su -c` с правами root и оболочка
 * приложения. На десктопе разницы нет — команда идёт от пользователя, и
 * повышать права нечем. `useRoot` намеренно игнорируется, а не «поднимается до
 * root»: на Windows и macOS такого механизма нет, а обещать его пользователю
 * нельзя.
 */
actual class CommandExecutor actual constructor() {

    actual fun executeCommand(
        command: String,
        workDir: String?,
        useRoot: Boolean,
        isLogAction: Boolean,
    ): String {
        if (isLogAction) {
            com.katya.app.tools.AppLogger.action("Выполнение команды", command.replace("\"", "\\\""))
        }
        val builder = if (isWindows) {
            ProcessBuilder("cmd", "/c", command)
        } else {
            ProcessBuilder("sh", "-c", command)
        }
        workDir?.let { dir ->
            val file = File(dir)
            if (file.isDirectory) builder.directory(file)
        }
        val result = runCatching {
            val process = builder.start()
            // Вывод читаем до ожидания: иначе процесс заполняет пайп и зависает,
            // а мы бы ждали его вечно.
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            if (!process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                return@runCatching (out + "\n[таймаут ${DEFAULT_TIMEOUT_MS / 1000} с]").trim()
            }
            listOf(out, err).filter { it.isNotBlank() }.joinToString("\n")
        }.getOrElse {
            "Execution error: ${it.message}"
        }
        return result.ifEmpty { "Command executed successfully (no output)." }
    }

    /** Root на десктопе не повышается: sudo требует пароля, а кэшировать его нельзя. */
    actual fun isRootAvailable(): Boolean = false

    actual fun isRootAvailableUncached(): Boolean = false
}
