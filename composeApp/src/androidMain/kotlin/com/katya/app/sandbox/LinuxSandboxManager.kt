package com.katya.app.sandbox

import android.content.Context
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.katya.app.SandboxSessions
import com.katya.app.TerminalLine
import com.katya.app.data.AppSettings
import com.katya.app.data.ConversationStorage
import com.katya.app.data.Distro
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
    private val appSettings: AppSettings,
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

    // Run proot directly from nativeLibraryDir where Android grants execute permission
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    private val downloader = RootfsDownloader(HttpClient(CIO))

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

        File(sandboxDir, "rootfs").deleteRecursively()
        File(sandboxDir, "home").deleteRecursively()
        File(sandboxDir, "tmp").deleteRecursively()

        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()

        copyLibtalloc()
        copyXrayBinary()

        val rootfsDir = File(sandboxDir, "rootfs")
        if (!rootfsDir.isDirectory) {
            val archiveFile = File(sandboxDir, "rootfs-download")
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(arch, distro, archiveFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
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
        val updateResult = executor.execute(updateCmd, timeoutSeconds = 60)
        if (updateResult["success"] as? Boolean != true) {
            throw IllegalStateException("$updateCmd failed: ${updateResult["stderr"]}")
        }

        _state.value = SandboxState.Installing("Installing Python, SQLite, Curl...")
        val installCmd = when (distro) {
            Distro.DEBIAN -> "apt-get install -y --no-install-recommends python3 python3-pip sqlite3 curl"
            Distro.TERMUX -> "apt install -y python sqlite curl"
        }
        val installResult = executor.execute(installCmd, timeoutSeconds = 300)
        if (installResult["success"] as? Boolean != true) {
            throw IllegalStateException("$installCmd failed: ${installResult["stderr"]}")
        }

        _state.value = SandboxState.Ready
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
