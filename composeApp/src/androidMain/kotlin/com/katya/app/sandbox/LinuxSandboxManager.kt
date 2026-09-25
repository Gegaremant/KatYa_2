package com.katya.app.sandbox

import android.content.Context
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.katya.app.SandboxSessions
import com.katya.app.TerminalLine
import com.katya.app.data.AppSettings
import com.katya.app.data.ConversationStorage
import com.katya.app.data.Distro
import com.katya.app.tools.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds
private const val APT_UPDATE_TIMEOUT_SECONDS = 300L
private const val APT_INSTALL_TIMEOUT_SECONDS = 900L

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
    private val appSettings: AppSettings,
    private val componentsRepository: com.katya.app.components.ComponentsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    private val sandboxDir: File
        get() = File(context.filesDir, "linux-sandbox")

    val rootfsPath: String get() = File(sandboxDir, "rootfs").absolutePath

    // Sandbox /root is bind-mounted from externally-visible app storage so files
    // produced by the agent can be opened via FileProvider Intents. Computed
    // lazily on first access; mkdirs and the one-time legacy-home migration run
    // once per process, then the cached path is reused for every shell call.
    val homePath: String by lazy {
        val external = context.getExternalFilesDir(null)
        val target = if (external != null) {
            File(external, "sandbox-home")
        } else {
            File(sandboxDir, "home")
        }
        target.mkdirs()
        val legacy = File(sandboxDir, "home")
        val newHomeIsEmpty = target.listFiles().isNullOrEmpty()
        if (legacy.isDirectory && legacy.absolutePath != target.absolutePath && newHomeIsEmpty) {
            try {
                legacy.listFiles()?.forEach { entry ->
                    val dest = File(target, entry.name)
                    if (!dest.exists()) entry.copyRecursively(dest, overwrite = false)
                }
            } catch (e: Exception) {
                android.util.Log.w("LinuxSandbox", "Legacy home migration failed: ${e.message}")
            }
        }
        target.absolutePath
    }

    val tmpPath: String get() = File(sandboxDir, "tmp").absolutePath

    // Proot/xray/talloc живут в filesDir/katya-native/{abi} — они НЕ вшиты в APK,
    // а скачиваются через ComponentsRepository («Альтернативные ссылки»).
    val nativeLibDir: String get() = File(context.filesDir, "katya-native/${com.katya.app.components.currentAbi()}").absolutePath
    val prootPath: String get() = File(nativeLibDir, "libproot.so").absolutePath

    private val downloader = RootfsDownloader(HttpClient(OkHttp))

    init {
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        if (!rootfs.isDirectory || !proot.exists() || !proot.canExecute()) return
        val d = currentDistro()
        val bashExists = when (d) {
            Distro.TERMUX -> File(rootfs, "usr/bin/bash").exists()
            Distro.DEBIAN -> File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists()
        }
        if (bashExists) {
            _state.value = SandboxState.Ready
        }
    }

    /** Публичный вызов после установки rootfs/нативных компонентов — обновляет состояние. */
    fun recheckInstallation() {
        checkExistingInstallation()
        val rootfs = File(sandboxDir, "rootfs")
        if (rootfs.isDirectory) {
            val d = currentDistro()
            val bashExists = when (d) {
                Distro.TERMUX -> File(rootfs, "usr/bin/bash").exists()
                Distro.DEBIAN -> File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists()
            }
            // Подтягиваем каталог: чтобы галочка в UI не врала, если rootfs уже лежит
            // на диске (например, остался от предыдущей версии с вшитыми компонентами).
            if (bashExists) {
                componentsRepository.markInstalled(componentsRepository.currentRootfsId())
            }
        }
        if (File(prootPath).exists()) {
            componentsRepository.markInstalled("native_${com.katya.app.components.currentAbi()}")
        }
        if (_state.value !is SandboxState.Ready) {
            AppLogger.d("LinuxSandbox", "Rootfs/native установлены, но песочница не собрана (нужен запуск setup)")
        }
    }

    /**
     * Called right after a rootfs/native component finishes installing.
     *
     * Until now nothing built the sandbox at install time: `recheckInstallation()`
     * only logged "нужен запуск setup" and the first caller to need the sandbox
     * (VLESS / DeepSeek) had to trigger the whole apt bootstrap itself — straight
     * into the sandbox gate, where it aborted before any of its own work.
     */
    fun buildIfComponentsPresent() {
        recheckInstallation()
        if (_state.value is SandboxState.Ready) return
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        if (rootfs.isDirectory && proot.exists() && proot.canExecute()) {
            AppLogger.action("Песочница", "компоненты на месте — запускаю сборку")
            setup()
        }
    }

    /** Determine the distro from a saved setting, or detect from an existing rootfs for backward compat. */
    private fun currentDistro(): Distro {
        if (_state.value == SandboxState.NotInstalled) return appSettings.getDistro()
        val rootfs = File(sandboxDir, "rootfs")
        return detectDistro(rootfs)
    }

    private fun detectDistro(rootfsDir: File): Distro {
        if (File(rootfsDir, "data").isDirectory) return Distro.TERMUX
        if (File(rootfsDir, "etc").isDirectory && (File(rootfsDir, "bin").isDirectory || File(rootfsDir, "usr/bin").isDirectory)) return Distro.DEBIAN
        return appSettings.getDistro()
    }

    /**
     * proot-distro tarballs (debian-*-pd-*.tar.xz) wrap the whole rootfs in a
     * single top-level directory (e.g. `debian-trixie-aarch64/`). Old builds
     * extracted them one level too deep, leaving `/bin/bash`, `/usr/bin/sh` etc.
     * missing at the expected paths — proot then fails with "'/bin/sh' not found"
     * for every command. Detect such a nested rootfs and move its contents up into
     * [rootfsDir] so existing installs recover without re-downloading hundreds of MB.
     *
     * Returns true only if [rootfsDir] is usable as-is afterwards (flat with a shell,
     * or flattened successfully). Returns false when the rootfs exists but is broken
     * (no shell anywhere, mixed garbage entries) — the caller must wipe and reinstall.
     */
    private fun flattenNestedRootfs(rootfsDir: File): Boolean {
        if (!rootfsDir.isDirectory) return true // nothing to do; caller will download
        val hasShellHere =
            File(rootfsDir, "bin/bash").exists() ||
                File(rootfsDir, "usr/bin/bash").exists() ||
                File(rootfsDir, "bin/sh").exists() ||
                File(rootfsDir, "usr/bin/sh").exists()
        if (hasShellHere) return true // already flat and usable

        val entries = rootfsDir.listFiles() ?: return false
        if (entries.size != 1) return false
        val nested = entries[0]
        if (!nested.isDirectory) return false
        val hasShellNested =
            File(nested, "bin/bash").exists() ||
                File(nested, "usr/bin/bash").exists() ||
                File(nested, "bin/sh").exists() ||
                File(nested, "usr/bin/sh").exists()
        if (!hasShellNested) return false

        val children = nested.listFiles() ?: return false
        for (child in children) {
            val dest = File(rootfsDir, child.name)
            if (dest.exists()) dest.deleteRecursively()
            if (!child.renameTo(dest)) {
                android.util.Log.w("LinuxSandbox", "Flatten rootfs: failed to move ${child.name}")
                return false
            }
        }
        runCatching { nested.delete() }
        return true
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        File(sandboxDir, "rootfs.zip").delete()
        val rootfs = File(sandboxDir, "rootfs")
        if (rootfs.isDirectory && File(prootPath).exists()) {
            _state.value = SandboxState.Ready
        } else {
            _state.value = SandboxState.NotInstalled
        }
    }

    private suspend fun setupInternal() {
        val arch = getLinuxArch()
        val distro = appSettings.getDistro()

        val proot = File(prootPath)
        if (!proot.exists()) {
            throw IllegalStateException("Proot binary not found at $prootPath.")
        }

        // Older builds wrote the whole proot-distro tarball one level deep
        // (debian-trixie-aarch64/ inside rootfs), which broke every shell call.
        // Flatten it so an existing install is re-used instead of being wiped
        // and re-downloaded (hundreds of MB) on every old device.
        val rootfsDir = File(sandboxDir, "rootfs")
        val flattened = flattenNestedRootfs(rootfsDir)
        if (!flattened || !rootfsDir.isDirectory) {
            rootfsDir.deleteRecursively()
        }
        File(sandboxDir, "home").deleteRecursively()
        File(sandboxDir, "tmp").deleteRecursively()

        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()

        copyLibtalloc()
        copyXrayBinary()

        if (!rootfsDir.isDirectory) {
            val archiveFile = File(sandboxDir, "rootfs-download")
            try {
                _state.value = SandboxState.Downloading(0f)
                // Ссылка на rootfs живёт в БД (ComponentsRepository) — её можно менять
                // в UI без пересборки. Старые встроенные URL остаются запасным путём.
                val repoRootfsUrl = componentsRepository.currentRootfsUrl()
                if (distro == Distro.DEBIAN && !repoRootfsUrl.isNullOrBlank()) {
                    AppLogger.d("LinuxSandbox", "Using rootfs URL from repository")
                    downloader.downloadDirect(repoRootfsUrl, archiveFile) { progress ->
                        _state.value = SandboxState.Downloading(progress)
                    }
                } else {
                    downloader.download(arch, distro, archiveFile) { progress ->
                        _state.value = SandboxState.Downloading(progress)
                    }
                }

                _state.value = SandboxState.Extracting
                downloader.extract(archiveFile, rootfsDir, distro)
            } finally {
                archiveFile.delete()
            }
        }

        _state.value = SandboxState.Installing("Configuring $distro...")
        downloader.makeWritable(rootfsDir)

        val executor = createProotExecutor(distro)

        _state.value = SandboxState.Installing("Updating repositories...")
        val updateCmd = when (distro) {
            Distro.DEBIAN -> "apt-get update"
            Distro.TERMUX -> "apt update"
        }
        AppLogger.action("Песочница: обновление пакетов", "начато")
        // A cold proot on a phone needs minutes for its very first apt-get update
        // (package lists + cold page cache); the old 60s budget aborted it every time.
        val updateResult = executor.execute(updateCmd, timeoutSeconds = APT_UPDATE_TIMEOUT_SECONDS)
        if (updateResult["success"] as? Boolean != true) {
            val message = commandFailure(updateCmd, updateResult)
            AppLogger.e("LinuxSandbox", message)
            throw IllegalStateException(message)
        }
        AppLogger.action("Песочница: обновление пакетов", "OK")

        _state.value = SandboxState.Installing("Installing Python, SQLite, Curl...")
        val installCmd = when (distro) {
            Distro.DEBIAN -> "apt-get install -y --no-install-recommends python3 python3-pip sqlite3 curl"
            Distro.TERMUX -> "apt install -y python sqlite curl"
        }
        AppLogger.action("Песочница: установка Python, SQLite, Curl", "начата")
        val installResult = executor.execute(installCmd, timeoutSeconds = APT_INSTALL_TIMEOUT_SECONDS)
        if (installResult["success"] as? Boolean != true) {
            val message = commandFailure(installCmd, installResult)
            AppLogger.e("LinuxSandbox", message)
            throw IllegalStateException(message)
        }
        AppLogger.action("Песочница: установка Python, SQLite, Curl", "OK")

        _state.value = SandboxState.Ready
        AppLogger.action("Песочница", "готова")
    }

    /** Turns a [ProotExecutor] result into a message that says what actually went
     *  wrong — the raw "… failed: null" used to hide every real cause. */
    private fun commandFailure(command: String, result: Map<String, Any>): String {
        val details = sequenceOf(
            (result["error"] as? String)?.takeIf { it.isNotBlank() },
            (result["stderr"] as? String)?.takeIf { it.isNotBlank() },
            (result["stdout"] as? String)?.takeIf { it.isNotBlank() },
        ).firstOrNull { !it.isNullOrBlank() }
        val timeout = if (result["timed_out"] as? Boolean == true) " (timeout)" else ""
        val suffix = details ?: "no output, exit code ${result["exit_code"]}"
        return "$command failed$timeout: $suffix"
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    private fun copyXrayBinary() {
        val rootfsDir = File(sandboxDir, "rootfs")
        val binDir = File(rootfsDir, "bin")
        binDir.mkdirs()
        val xrayTarget = File(binDir, "xray")
        if (xrayTarget.exists()) return

        val source = File(nativeLibDir, "libxray.so")
        if (source.exists()) {
            source.copyTo(xrayTarget, overwrite = true)
            xrayTarget.setExecutable(true, false)
        }
    }

    fun createProotExecutor(d: Distro? = null): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
        distro = d ?: currentDistro(),
    )

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    // Debounce per-session transcript writes. A burst of commands (e.g. a
    // 1000-iteration loop) would otherwise re-serialize the entire conversations
    // JSON and rewrite SharedPreferences once per command.
    private val pendingSaves = mutableMapOf<String, Job>()

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = SessionShell(sessionId, inner, initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        synchronized(pendingSaves) {
            pendingSaves[sessionId]?.cancel()
            pendingSaves[sessionId] = scope.launch {
                try {
                    delay(TRANSCRIPT_SAVE_DEBOUNCE)
                    conversationStorage.updateShellTranscript(sessionId, lines)
                } finally {
                    synchronized(pendingSaves) { pendingSaves.remove(sessionId) }
                }
            }
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    /**
     * Toggle prune-pause on an existing session shell. Does NOT create a shell
     * — if there's no shell for [sessionId] yet there's no transcript to gate.
     */
    fun setSessionInteractive(sessionId: String, interacting: Boolean) {
        val shell = synchronized(shells) { shells[sessionId] } ?: return
        shell.setPrunePaused(interacting)
    }

    fun clearTranscript(sessionId: String) {
        synchronized(shells) { shells[sessionId] }?.transcript?.clear()
    }

    fun closeShell(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    private fun closeAllShells() {
        val all = synchronized(shells) {
            val snapshot = shells.values.toList()
            shells.clear()
            _sessions.value = emptyList()
            snapshot
        }
        all.forEach { it.reset() }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        val distro = currentDistro()
        val packages = when (distro) {
            Distro.TERMUX -> listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
                "openssh-client", "lftp", "rsync", "xray-core",
            )

            Distro.DEBIAN -> listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "python3-pip", "nodejs",
                "openssh-client", "lftp", "rsync",
            )
        }
        currentJob = scope.launch {
            try {
                val executor = createProotExecutor(distro)
                if (distro == Distro.TERMUX) {
                    executor.execute("sh -c \"grep -q 'edge/testing' /etc/apk/repositories || echo 'http://dl-cdn.alpinelinux.org/alpine/edge/testing' >> /etc/apk/repositories\"", timeoutSeconds = 30)
                }
                for (pkg in packages) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val cmd = when (distro) {
                        Distro.TERMUX -> "apk add --no-cache $pkg"
                        Distro.DEBIAN -> "apt-get install -y --no-install-recommends $pkg"
                    }
                    val result = executor.execute(cmd, timeoutSeconds = 120)
                    ensureActive()
                    val success = result["success"] as? Boolean ?: false
                    if (!success) {
                        val stderr = result["stderr"] as? String ?: ""
                        val stdout = result["stdout"] as? String ?: ""
                        val error = result["error"] as? String ?: ""
                        val timedOut = result["timed_out"] as? Boolean ?: false
                        val exitCode = result["exit_code"] as? Int ?: -1
                        android.util.Log.e("LinuxSandbox", "Failed to install $pkg: exit=$exitCode timedOut=$timedOut error=$error stdout=$stdout stderr=$stderr")
                        _state.value = SandboxState.Error("Failed to install $pkg: ${stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)}")
                        return@launch
                    }
                }
                runCatching { SshConfigManager(java.io.File(homePath)).ensureDefaults() }
                    .onFailure { android.util.Log.w("LinuxSandbox", "ssh defaults seed failed: ${it.message}") }
                _state.value = SandboxState.Ready
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun reset() {
        scope.launch {
            closeAllShells()
            sandboxDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!sandboxDir.isDirectory) return 0
        // Manual stack walk instead of walkTopDown(): the latter throws an
        // AssertionError if a child entry transitions from directory→non-directory
        // between the iterator's isDirectory check and DirectoryState construction.
        // The rootfs can contain unix sockets / FIFOs / broken symlinks (e.g. from
        // user-run programs like node), and concurrent install activity also races
        // the walk. We skip bad entries and keep going.
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(sandboxDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                        // skip sockets, FIFOs, broken symlinks
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry, keep iterating
                }
            }
        }
        return total / (1024 * 1024)
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return File(rootfsPath, "usr/bin/python3").exists() &&
            File(rootfsPath, "usr/bin/ssh").exists()
    }
}
