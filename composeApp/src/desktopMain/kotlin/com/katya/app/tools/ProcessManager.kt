package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_OUTPUT_CHARS = 30_000

private val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")

/**
 * Фоновые процессы десктопной версии.
 *
 * На Android здесь живут процессы внутри proot-песочницы, и у них общий с ней
 * таймаут. Здесь команда уходит в `sh`/`cmd` напрямую, поэтому нужен свой
 * учёт: идентификатор сессии, чтобы `manage_process` мог её опросить, и
 * принудительное убийство дерева потомков — иначе `server &` пережил бы
 * инструмент и продолжал занимать порт.
 */
object ProcessManager {

    class Session(
        val id: String,
        val command: String,
        val startTime: Long,
        val process: Process,
        @Volatile var stdout: String = "",
        @Volatile var stderr: String = "",
        @Volatile var finished: Boolean = false,
        @Volatile var exitCode: Int? = null,
        @Volatile var timedOut: Boolean = false,
    ) {
        /**
         * `exit_code` nullable: у живого процесса кода выхода ещё нет, а `Any` не
         * принимает null. Поэтому `mapOf<String, Any?>` и приведение на границе —
         * так же, как в android-версии.
         */
        @Suppress("UNCHECKED_CAST")
        fun info(): Map<String, Any> = mapOf(
            "session_id" to id,
            "command" to command,
            "started_at" to startTime,
            "status" to if (finished) "finished" else "running",
            "exit_code" to exitCode,
            "timed_out" to timedOut,
        ) as Map<String, Any>
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val nextId = AtomicInteger(1)

    fun startBackground(
        command: String,
        timeoutSeconds: Long,
        workingDir: File?,
        envMap: Map<String, String>,
    ): Map<String, Any> {
        val builder = if (isWindows) ProcessBuilder("cmd", "/c", command) else ProcessBuilder("sh", "-c", command)
        if (workingDir != null && workingDir.isDirectory) builder.directory(workingDir)
        if (envMap.isNotEmpty()) builder.environment().putAll(envMap)
        val process = builder.start()
        val session = Session(
            id = "bg-${nextId.getAndIncrement()}",
            command = command,
            startTime = System.currentTimeMillis(),
            process = process,
        )
        sessions[session.id] = session

        val out = StringBuilder()
        val err = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        synchronized(out) {
                            if (out.length < MAX_OUTPUT_CHARS) out.append(line).append('\n')
                        }
                    }
                }
            }
        }, "katya-bg-stdout").apply { isDaemon = true }
        val readerErr = Thread({
            runCatching {
                process.errorStream.bufferedReader().use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        synchronized(err) {
                            if (err.length < MAX_OUTPUT_CHARS) err.append(line).append('\n')
                        }
                    }
                }
            }
        }, "katya-bg-stderr").apply { isDaemon = true }
        reader.start()
        readerErr.start()

        val watcher = Thread({
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                session.timedOut = true
                runCatching { process.descendants().forEach { it.destroyForcibly() } }
                process.destroyForcibly()
            }
            runCatching { reader.join(500) }
            runCatching { readerErr.join(500) }
            session.stdout = synchronized(out) { out.toString() }
            session.stderr = synchronized(err) { err.toString() }
            session.exitCode = if (session.timedOut) -1 else runCatching { process.exitValue() }.getOrDefault(-1)
            session.finished = true
        }, "katya-bg-watch").apply { isDaemon = true }
        watcher.start()

        @Suppress("UNCHECKED_CAST")
        return mapOf(
            "success" to true,
            "session_id" to session.id,
            "status" to "running",
            "message" to "Process started in background. Use manage_process to check status.",
        ) as Map<String, Any>
    }

    fun list(): Map<String, Any> = mapOf(
        "running" to sessions.values.filter { !it.finished }.map { it.info() },
        "finished" to sessions.values.filter { it.finished }.map { it.info() },
    )

    fun log(sessionId: String, offset: Int, limit: Int): Map<String, Any> {
        val session = sessions[sessionId] ?: return mapOf("success" to false, "error" to "No such session: $sessionId")
        val out = session.stdout.drop(offset).take(limit)
        val err = session.stderr.drop(offset).take(limit)
        @Suppress("UNCHECKED_CAST")
        return mapOf(
            "success" to true,
            "session_id" to sessionId,
            "status" to if (session.finished) "finished" else "running",
            "exit_code" to session.exitCode,
            "timed_out" to session.timedOut,
            "stdout" to out,
            "stderr" to err,
        ) as Map<String, Any>
    }

    fun kill(sessionId: String): Map<String, Any> {
        val session = sessions[sessionId] ?: return mapOf("success" to false, "error" to "No such session: $sessionId")
        if (session.finished) return mapOf("success" to false, "error" to "Process already finished")
        runCatching { session.process.descendants().forEach { it.destroyForcibly() } }
        session.process.destroyForcibly()
        return mapOf("success" to true, "session_id" to sessionId, "status" to "killed")
    }

    fun remove(sessionId: String): Map<String, Any> {
        val removed = sessions.remove(sessionId) != null
        return mapOf("success" to removed, "session_id" to sessionId)
    }
}

/**
 * `manage_process` для десктопа.
 *
 * Имя то же, что на Android, — модель учится одному набору инструментов, и
 * описание у обоих совпадает по смыслу: та же сессия, тот же опрос, тот же
 * `session_id`.
 */
val manageProcessTool = object : Tool {
    override val schema = ToolSchema(
        name = "manage_process",
        description = "Manage background shell processes started with execute_shell_command (background=true). " +
            "Actions: list, log (session_id, offset, limit), kill (session_id), remove (session_id).",
        parameters = mapOf(
            "action" to ParameterSchema("string", "list | log | kill | remove", true),
            "session_id" to ParameterSchema("string", "Target session id (for log/kill/remove)", false),
            "offset" to ParameterSchema("integer", "Character offset into captured output (default 0)", false),
            "limit" to ParameterSchema("integer", "Max characters to return (default 4000)", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()?.lowercase() ?: "list"
        val sessionId = args["session_id"]?.toString() ?: ""
        val offset = (args["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, MAX_OUTPUT_CHARS) ?: 4000
        return when (action) {
            "list" -> ProcessManager.list()
            "log" -> ProcessManager.log(sessionId, offset, limit)
            "kill" -> ProcessManager.kill(sessionId)
            "remove" -> ProcessManager.remove(sessionId)
            else -> mapOf("success" to false, "error" to "Unknown action: $action")
        }
    }
}
