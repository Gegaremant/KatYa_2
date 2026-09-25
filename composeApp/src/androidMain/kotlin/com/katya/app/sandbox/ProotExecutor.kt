package com.katya.app.sandbox

import com.katya.app.data.Distro
import com.katya.app.smartTruncate
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 900L

class ProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readerFutures: List<CompletableFuture<Void>>,
) {
    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    fun writeInput(line: String) {
        if (cancelled.get()) return
        runCatching {
            val bytes = (line + "\n").toByteArray()
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    fun awaitExit(): Int {
        // Poll so a cancel() from another thread can short-circuit the wait.
        // On Linux, close(fd) does NOT unblock a thread already inside read(fd),
        // so reader futures can sit waiting on a tracee pipe even after SIGKILL.
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readerFutures.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

class ProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String,
    private val distro: Distro = Distro.TERMUX,
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)

        return try {
            val process = Runtime.getRuntime().exec(
                buildProcessArgs(command, workingDir),
                buildEnvVars(extraEnv),
                File(rootfsPath).parentFile,
            )

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return result(
                    success = false,
                    stdout = drain(stdoutFuture),
                    stderr = drain(stderrFuture),
                    exitCode = -1,
                    timedOut = true,
                    error = "Command timed out after ${effectiveTimeout}s",
                )
            }

            result(
                success = process.exitValue() == 0,
                stdout = drain(stdoutFuture),
                stderr = drain(stderrFuture),
                exitCode = process.exitValue(),
                timedOut = false,
            )
        } catch (e: Exception) {
            // Every key is always present: callers read "stderr" for diagnostics,
            // and a map without it used to surface as a bare "… failed: null",
            // hiding the real reason (bad exec bit, missing loader, …).
            result(
                success = false,
                stdout = "",
                stderr = "",
                exitCode = -1,
                timedOut = false,
                error = "${e.javaClass.simpleName}: ${e.message ?: e.toString()}",
            )
        }
    }

    private fun result(
        success: Boolean,
        stdout: String,
        stderr: String,
        exitCode: Int,
        timedOut: Boolean,
        error: String? = null,
    ): Map<String, Any> = buildMap {
        put("success", success)
        put("stdout", stdout.smartTruncate(MAX_OUTPUT_LENGTH))
        put("stderr", stderr.smartTruncate(MAX_OUTPUT_LENGTH))
        put("exit_code", exitCode)
        put("timed_out", timedOut)
        if (error != null) put("error", error)
    }

    /** Reader threads can stay parked on a killed process' pipe; never let that
     *  turn a timeout into an exception that hides the timeout itself. */
    private fun drain(future: CompletableFuture<String>): String = runCatching { future.get(1, TimeUnit.SECONDS) }.getOrDefault("")

    fun executeStreaming(
        command: String,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): ProotHandle {
        val process = Runtime.getRuntime().exec(
            buildProcessArgs(command, workingDir),
            buildEnvVars(extraEnv),
            File(rootfsPath).parentFile,
        )
        val cancelled = AtomicBoolean(false)
        val stdoutFuture = CompletableFuture.runAsync {
            streamLines(process.inputStream.bufferedReader(), cancelled, onStdout)
        }
        val stderrFuture = CompletableFuture.runAsync {
            streamLines(process.errorStream.bufferedReader(), cancelled, onStderr)
        }
        return ProotHandle(process, cancelled, listOf(stdoutFuture, stderrFuture))
    }

    private fun buildProcessArgs(command: String, workingDir: String): Array<String> {
        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        return when (distro) {
            Distro.TERMUX -> arrayOf(
                prootPath,
                "--bind=$rootfsPath:/data/data/com.termux/files/usr",
                "--bind=$homePath:/data/data/com.termux/files/home",
                "--bind=$tmpPath:/data/data/com.termux/files/usr/tmp",
                "--bind=/dev",
                "--bind=/proc",
                "--bind=/sys",
                "--bind=/sdcard",
                "--bind=/storage",
                "-0",
                "-w", workingDir,
                "/data/data/com.termux/files/usr/bin/sh", "-c", command,
            )

            Distro.DEBIAN -> {
                // Pick the first shell that actually exists inside the rootfs.
                // Debian's /bin is a usrmerge symlink; even though extractTar now
                // creates symlinks, some devices may block them — fall back to
                // whatever bash/sh path survived rather than hardcoding /bin/bash.
                val candidates = listOf(
                    "usr/bin/bash",
                    "bin/bash",
                    "usr/bin/sh",
                    "bin/sh",
                )
                val shell = candidates.firstOrNull { File(rootfsPath, it).exists() } ?: "bin/sh"
                arrayOf(
                    prootPath,
                    "--bind=$rootfsPath:/",
                    "--bind=$homePath:/root",
                    "--bind=$tmpPath:/tmp",
                    "--bind=/dev",
                    "--bind=/proc",
                    "--bind=/sys",
                    "--bind=/sdcard",
                    "--bind=/storage",
                    "-0",
                    "-w", workingDir,
                    "/$shell", "-c", command,
                )
            }
        }
    }

    private fun buildEnvVars(extraEnv: Map<String, String>): Array<String> {
        // Ensure libtalloc.so.2 exists in tmpPath
        val tallocOrig = File(libDir, "libtalloc.so")
        val tallocLink = File(tmpPath, "libtalloc.so.2")
        if (tallocOrig.exists() && !tallocLink.exists()) {
            try {
                android.system.Os.symlink(tallocOrig.absolutePath, tallocLink.absolutePath)
            } catch (e: Exception) {
                tallocOrig.copyTo(tallocLink, overwrite = true)
            }
        }

        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = when (distro) {
            Distro.TERMUX -> arrayOf(
                "PREFIX=/data/data/com.termux/files/usr",
                "HOME=/data/data/com.termux/files/home",
                "PATH=/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets",
                "TMPDIR=/data/data/com.termux/files/usr/tmp",
                "LD_LIBRARY_PATH=$tmpPath:$libDir:/data/data/com.termux/files/usr/lib",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PROOT_TMP_DIR=$tmpPath",
                "PROOT_LOADER=$loaderPath",
            )

            Distro.DEBIAN -> arrayOf(
                "HOME=/root",
                "PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
                "TMPDIR=/tmp",
                "LD_LIBRARY_PATH=$tmpPath:$libDir:/usr/lib",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "LC_ALL=C.UTF-8",
                "DEBIAN_FRONTEND=noninteractive",
                "PROOT_TMP_DIR=$tmpPath",
                "PROOT_LOADER=$loaderPath",
            )
        }
        return baseEnv + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private fun readBounded(reader: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        try {
            var read: Int
            while (reader.read(buf).also { read = it } != -1) {
                sb.append(buf, 0, read)
                if (sb.length >= MAX_OUTPUT_LENGTH) break
            }
            if (sb.length >= MAX_OUTPUT_LENGTH) {
                while (reader.read(buf) != -1) { /* discard */ }
            }
        } catch (_: IOException) {
            // Stream closed under us (typically destroyForcibly on timeout).
            // Return what we have so the timed_out path can surface a clean result.
        }
        return sb.toString()
    }

    private fun streamLines(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onLine: (String) -> Unit,
    ) {
        try {
            while (!cancelled.get()) {
                val line = try {
                    reader.readLine()
                } catch (e: IOException) {
                    if (cancelled.get()) break
                    throw e
                } ?: break
                onLine(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }
}
