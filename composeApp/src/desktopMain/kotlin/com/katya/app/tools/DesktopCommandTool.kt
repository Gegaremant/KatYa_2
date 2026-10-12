package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MAX_OUTPUT_CHARS = 30_000
private const val DEFAULT_TIMEOUT_SECONDS = 60L
private const val MAX_TIMEOUT_SECONDS = 600L

/**
 * Сколько ждём EOF после выхода процесса.
 *
 * Фоновый ребёнок (`server &`) наследует пайпы и держит их открытыми, поэтому
 * EOF может не наступить никогда. Читатели — на отдельных daemon-потоках, иначе
 * осиротевший процесс занял бы поток общего пула и не дал JVM выйти.
 */
private const val DRAIN_GRACE_SECONDS = 2L

private val readers = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "katya-shell-reader").apply { isDaemon = true }
}

private val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")

/**
 * Команды, которые на десктопе ломают машину, а не работу программы.
 *
 * На Android песочница держит удар, здесь же команда идёт прямо в оболочку с
 * правами пользователя. Список перенесён из desktop-реализации Kai, где он
 * закрывает ровно те же случаи.
 */
private val blocked = listOf(
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/(?:\s|$)"""),
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/\*"""),
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~(?:\s|$)"""),
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~/\*"""),
    Regex("""mkfs\."""),
    Regex("""dd\s+.*if=/dev/(zero|urandom|random)"""),
    Regex(""">\s*/dev/[sh]d[a-z]"""),
    Regex(""":\(\)\s*\{.*\|.*&\s*\}\s*;?\s*:"""),
    Regex("""chmod\s+-[^\s]*R[^\s]*\s+[0-7]+\s+/(?:\s|$)"""),
    Regex("""\bshutdown\b"""),
    Regex("""\breboot\b"""),
    Regex("""\bhalt\b"""),
    Regex("""\bpoweroff\b"""),
    Regex("""\binit\s+[06]\b"""),
    Regex("""format\s+[A-Za-z]:"""),
)

/**
 * Переменные, которые нельзя подменять из аргументов инструмента.
 *
 * `PATH`, `LD_PRELOAD` и прочие задают, какой бинарь вообще запустится; подмена
 * их из строки команды — это способ выполнить не то, что описано в описании
 * инструмента.
 */
private val BLOCKED_ENV = setOf("PATH", "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH")

/** Собирает поток, не давая процессу встать на полном пайпе. */
private class StreamCollector(stream: InputStream) {
    private val sb = StringBuilder()
    private val done: CompletableFuture<Void> = CompletableFuture.runAsync({
        stream.bufferedReader().use { reader ->
            val buf = CharArray(8192)
            while (true) {
                val read = reader.read(buf)
                if (read == -1) break
                synchronized(sb) {
                    if (sb.length < MAX_OUTPUT_CHARS) sb.append(buf, 0, read)
                }
            }
        }
    }, readers)

    fun awaitEof(seconds: Long): Boolean = try {
        done.get(seconds, TimeUnit.SECONDS)
        true
    } catch (_: TimeoutException) {
        false
    } catch (_: Exception) {
        true
    }

    fun text(): String = synchronized(sb) { sb.toString() }
}

/** Убивает процесс и всё, что он успел породить. */
private fun destroyTree(process: Process) {
    runCatching { process.descendants().forEach { it.destroyForcibly() } }
    process.destroyForcibly()
}

private fun buildDescription(): String {
    val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    val platform = when {
        "win" in os -> "Windows"
        "mac" in os -> "macOS"
        else -> "Linux"
    }
    val shell = if (isWindows) "cmd.exe" else "sh"
    return """Execute a shell command on the host machine ($platform, shell: $shell) and return stdout, stderr, and exit code.
Each command runs in a fresh shell — use "cd dir && command" for directory changes.
Output is capped at $MAX_OUTPUT_CHARS characters per stream; for large output, pipe through head/tail.
Default timeout: ${DEFAULT_TIMEOUT_SECONDS}s, max: ${MAX_TIMEOUT_SECONDS}s.
Use for file operations, system info, running scripts, installing packages, etc.
Set background=true to run long-lived processes (servers, builds). Use the manage_process tool to check on them."""
}

/**
 * Команды в оболочке ОС — то, чем песочница является на Android.
 *
 * На десктопе proot не нужен: команда идёт прямо в `sh`/`cmd` с правами
 * пользователя, поэтому она видит его файлы, переменные и менеджер пакетов. Описания
 * android-инструмента про `/root`, `apt-get` и `open_file` здесь не переносятся —
 * они описывали бы несуществующее окружение.
 */
object DesktopCommandTool : Tool {

    override val timeout: Duration = (MAX_TIMEOUT_SECONDS + 10).seconds

    override val schema = ToolSchema(
        name = "execute_shell_command",
        description = buildDescription(),
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute", true),
            "timeout" to ParameterSchema("integer", "Timeout in seconds (default $DEFAULT_TIMEOUT_SECONDS, max: $MAX_TIMEOUT_SECONDS)", false),
            "working_dir" to ParameterSchema("string", "Working directory for the command", false),
            "env" to ParameterSchema("object", "Environment variables to set (key-value pairs). Cannot override PATH or LD_PRELOAD.", false),
            "background" to ParameterSchema("boolean", "Run in background and return immediately with a session_id. Use manage_process tool to check status.", false),
        ),
    )

    @Suppress("UNCHECKOR_CAST")
    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")
        if (blocked.any { it.containsMatchIn(command) }) {
            return mapOf("success" to false, "error" to "Command is blocked for safety reasons")
        }
        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(1, MAX_TIMEOUT_SECONDS)
        val workingDir = (args["working_dir"] as? String)?.let { File(it) }
        val envMap = (args["env"] as? Map<String, Any>)
            ?.mapValues { it.value.toString() }
            ?.filterKeys { it.uppercase() !in BLOCKED_ENV }
            ?: emptyMap()

        if (args["background"] == true) {
            return ProcessManager.startBackground(command, timeoutSeconds, workingDir, envMap)
        }
        return try {
            runInterruptible(Dispatchers.IO) { runForeground(command, timeoutSeconds, workingDir, envMap) }
        } catch (e: Exception) {
            // Отмена (кнопка «стоп», таймаут исполнителя) обязана подняться вверх,
            // а не превратиться в результат команды — иначе «стоп» не работает.
            if (e is kotlinx.coroutines.CancellationException) throw e
            mapOf("success" to false, "error" to (e.message ?: "Failed to execute command"))
        }
    }

    private fun runForeground(
        command: String,
        timeoutSeconds: Long,
        workingDir: File?,
        envMap: Map<String, String>,
    ): Map<String, Any> {
        val builder = if (isWindows) {
            ProcessBuilder("cmd", "/c", command)
        } else {
            ProcessBuilder("sh", "-c", command)
        }
        if (workingDir != null && workingDir.isDirectory) builder.directory(workingDir)
        if (envMap.isNotEmpty()) builder.environment().putAll(envMap)

        val process = builder.start()
        try {
            val stdout = StreamCollector(process.inputStream)
            val stderr = StreamCollector(process.errorStream)
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) destroyTree(process)

            val drained = stdout.awaitEof(DRAIN_GRACE_SECONDS) and stderr.awaitEof(DRAIN_GRACE_SECONDS)
            val stderrText = if (finished && !drained) {
                stderr.text() + "\n[Output may be incomplete: a background process still holds the shell's " +
                    "output open. Use background=true for long-running processes.]"
            } else {
                stderr.text()
            }
            if (!finished) {
                return mapOf(
                    "success" to false,
                    "stdout" to stdout.text(),
                    "stderr" to stderrText,
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }
            val exit = process.exitValue()
            return mapOf(
                "success" to (exit == 0),
                "stdout" to stdout.text(),
                "stderr" to stderrText,
                "exit_code" to exit,
                "timed_out" to false,
            )
        } finally {
            if (process.isAlive) destroyTree(process)
        }
    }

    val toolInfo = ToolInfo(
        id = "execute_shell_command",
        name = "Execute Shell Command",
        description = "Execute a shell command on this computer",
    )
}
