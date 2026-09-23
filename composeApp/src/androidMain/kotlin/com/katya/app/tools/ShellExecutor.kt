package com.katya.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single low-level shell runner used by every host-command path
 * (CommandExecutor, RootCommandExecutor, AndroidHostShellTool, RootHelper).
 *
 * Before this consolidation each caller implemented `su`/`sh` execution with
 * subtly different (and sometimes missing) timeouts. The worst offenders read
 * the process streams *after* blocking [Process.waitFor]: a chatty command fills
 * the OS pipe buffer (64 KB) and deadlocks forever, and without a waitFor()
 * timeout a hung `su` prompt can freeze the caller indefinitely.
 *
 * This implementation:
 *  - merges stdout/stderr and drains them on a daemon thread (no pipe deadlock);
 *  - bounds the whole execution with [timeoutMs] and force-kills on expiry;
 *  - caches the root check for a few seconds so the ~24 commands issued by
 *    RootHelper.grantAllPermissions do not spawn 24 `su` probes.
 */
object ShellExecutor {

    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val ROOT_CHECK_TIMEOUT_MS = 15_000L
    private const val ROOT_CACHE_TTL_MS = 15_000L

    data class ExecResult(
        val isSuccess: Boolean,
        val output: String,
        val exitCode: Int,
    )

    @Volatile
    private var cachedRoot: Boolean? = null

    @Volatile
    private var cachedRootAtMs: Long = 0L

    /**
     * Non-suspending root probe. Safe to call from any thread; each call blocks
     * at most [ROOT_CHECK_TIMEOUT_MS] and results are cached for [ROOT_CACHE_TTL_MS].
     */
    fun hasRootAccess(): Boolean {
        val now = System.currentTimeMillis()
        cachedRoot?.let { cached ->
            if (now - cachedRootAtMs < ROOT_CACHE_TTL_MS) return cached
        }
        val result = execute("id", useRoot = true, timeoutMs = ROOT_CHECK_TIMEOUT_MS)
        val ok = result.isSuccess && result.exitCode == 0 &&
            (
                result.output.contains("uid=0", ignoreCase = true) ||
                    result.output.contains("root", ignoreCase = true)
                )
        cachedRoot = ok
        cachedRootAtMs = now
        return ok
    }

    fun invalidateRootCache() {
        cachedRoot = null
    }

    suspend fun executeSuspend(
        command: String,
        useRoot: Boolean = false,
        workDir: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ExecResult = withContext(Dispatchers.IO) {
        execute(command, useRoot = useRoot, workDir = workDir, timeoutMs = timeoutMs)
    }

    fun execute(
        command: String,
        useRoot: Boolean = false,
        workDir: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ExecResult {
        val shortCmd = command.take(200)
        if (useRoot) {
            com.katya.app.tools.AppLogger.rootAction("Запрос root-прав: $shortCmd", "выполняется")
        }
        return try {
            val argv = if (useRoot) arrayOf("su", "-c", command) else arrayOf("sh", "-c", command)
            val builder = ProcessBuilder(*argv)
            if (workDir != null) {
                val dir = File(workDir)
                if (dir.exists() && dir.isDirectory) {
                    builder.directory(dir)
                }
            }
            builder.redirectErrorStream(true)
            val result = runProcess(builder.start(), timeoutMs)
            if (useRoot) {
                com.katya.app.tools.AppLogger.rootAction(
                    "Результат root-команды: $shortCmd",
                    if (result.isSuccess && result.exitCode == 0) "OK" else "код ${result.exitCode}, ${result.output.take(120)}",
                )
            }
            result
        } catch (e: Exception) {
            if (useRoot) {
                com.katya.app.tools.AppLogger.rootAction("Root-команда провалена: $shortCmd", "ОШИБКА: ${e.message}")
            }
            ExecResult(false, "Execution error: ${e.message}", -1)
        }
    }

    private fun runProcess(process: Process, timeoutMs: Long): ExecResult {
        // Drain stdout/stderr on a daemon thread so we never block on a full pipe buffer.
        val outputBuffer = StringBuilder()
        val drainer = Thread {
            try {
                process.inputStream.use { input ->
                    input.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputBuffer.append(line).append('\n')
                        }
                    }
                }
            } catch (_: Exception) {
                // Process was destroyed; nothing left to drain.
            }
        }
        drainer.isDaemon = true
        drainer.start()

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!finished) {
            process.destroy()
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
            }
            return ExecResult(false, "Command timed out after ${timeoutMs}ms.", 124)
        }

        try {
            drainer.join(2_000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        val exitCode = try {
            process.exitValue()
        } catch (_: Exception) {
            -1
        }
        return ExecResult(exitCode == 0, outputBuffer.toString().trim(), exitCode)
    }
}
