package com.katya.app.sandbox

import com.katya.app.data.Distro
import com.katya.app.smartTruncate
import com.katya.app.tools.AppLogger
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

    /**
     * How to actually invoke proot.
     *
     * Straight exec of `libproot.so`, which is a normal PIE executable built for
     * Android (`interpreter /system/bin/linker64`). Its dynamic deps are satisfied
     * by `LD_LIBRARY_PATH` plus the `libtalloc.so.2` link made in [buildEnvVars].
     *
     * The vendored `libproot-loader.so` is deliberately **not** put in front of it.
     * That file is PRoot's freestanding tracee *interpreter*, not a launcher: its
     * entry point is `_start(void *cursor)`, which expects a pointer to a load
     * script in a register. PRoot hands it that script itself when it execs a
     * program inside the rootfs, and falls back to extracting its own embedded
     * copy. Exec'ing it as a program with proot's path as `argv[1]` therefore fed
     * it a pointer it never produced — it dereferenced garbage and died with
     * SIGSEGV before proot ran at all. The field log showed exactly that as
     * `exit_code=139` (128 + SIGSEGV) on every single command, which is why the
     * sandbox, the shell tools and DeepSeek were all broken at once.
     *
     * The loader still matters, and still ships in the archive: it is what keeps
     * 32-bit programs working under a 64-bit process. It reaches PRoot through
     * `PROOT_LOADER` / `PROOT_LOADER32`, which `get_loader_path()` reads — see
     * [buildEnvVars].
     */
    private fun buildProcessArgs(command: String, workingDir: String): Array<String> {
        val prefix = arrayOf(prootPath)
        return when (distro) {
            Distro.TERMUX -> arrayOf(
                *prefix,
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
                    *prefix,
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
        // PROOT_TMP_DIR and the talloc link both need the directory to exist; on a
        // fresh install `tmp/` is not there yet and Os.symlink then fails with ENOENT.
        File(tmpPath).mkdirs()

        // Feedback 05.10: `libproot.so` needs SONAME `libtalloc.so.2`, and the linker
        // refuses to start it without that exact filename. The archive ships
        // `libtalloc.so`, and it lives next to the binary — *not* in `libDir`, which is
        // the sandbox root populated only by one of the install paths. Looking there
        // meant `tallocOrig` never existed, no link was made, and every exec died with
        // `CANNOT LINK EXECUTABLE ... library "libtalloc.so.2" not found`.
        //
        // Put the name where the linker looks first (the binary's own directory), and
        // into the temp dir as well, so PROOT_TMP_DIR and LD_LIBRARY_PATH agree.
        val binaryDir = File(prootPath).parentFile
        val tallocDirs = ArrayList<File>(2)
        binaryDir?.let { tallocDirs.add(it) }
        tallocDirs.add(File(tmpPath))
        for (dir in tallocDirs) {
            val link = File(dir, "libtalloc.so.2")
            if (link.exists()) continue
            val source = File(dir, "libtalloc.so").takeIf { it.exists() }
                ?: File(libDir, "libtalloc.so").takeIf { it.exists() }
                ?: continue
            runCatching { android.system.Os.symlink(source.absolutePath, link.absolutePath) }
                .recoverCatching { source.copyTo(link, overwrite = true) }
        }

        val nativeDir = File(prootPath).parent.orEmpty()
        // PROOT_LOADER points PRoot at the tracee interpreter to use instead of the
        // copy it would extract from its own binary (`get_loader_path()` in
        // execve/enter.c reads these two variables first). Not passing the path as
        // argv[0] is deliberate — see [buildProcessArgs].
        val loader = File(nativeDir, "libproot-loader.so")
        val loaderEnv = if (loader.isFile && loader.canExecute()) "PROOT_LOADER=${loader.absolutePath}" else null
        // The 32-bit loader is what keeps 32-bit binaries inside the rootfs working on
        // a 64-bit process, so it is exported the same way.
        val loader32 = File(nativeDir, "libproot-loader32.so")
        val loader32Env = if (loader32.isFile && loader32.canExecute()) "PROOT_LOADER32=${loader32.absolutePath}" else null
        if (loaderEnv == null || loader32Env == null) {
            AppLogger.w(
                "ProotExecutor",
                "Загрузчик proot не найден или не исполняемый " +
                    "(loader=${loaderEnv != null}, loader32=${loader32Env != null}) — " +
                    "32-битные программы внутри песочницы могут не запуститься",
            )
        }
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
            )
        }
        val loaders = listOfNotNull(loaderEnv, loader32Env)
        return baseEnv + loaders + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
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
