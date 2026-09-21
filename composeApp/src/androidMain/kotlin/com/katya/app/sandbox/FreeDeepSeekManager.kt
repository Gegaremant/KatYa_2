package com.katya.app.sandbox

import android.util.Log
import com.katya.app.data.DataRepository
import com.katya.app.data.Service
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

sealed class DeepSeekProxyState {
    data object Stopped : DeepSeekProxyState()
    data object Installing : DeepSeekProxyState()
    data object Starting : DeepSeekProxyState()
    data object Running : DeepSeekProxyState()
    data class Error(val message: String) : DeepSeekProxyState()
}

class FreeDeepSeekManager(
    private val dataRepository: DataRepository,
    private val linuxSandboxManager: LinuxSandboxManager,
) {
    private val _state = MutableStateFlow<DeepSeekProxyState>(DeepSeekProxyState.Stopped)
    val state: StateFlow<DeepSeekProxyState> = _state.asStateFlow()

    private var proxyJob: Job? = null
    private var prootHandle: ProotHandle? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(force: Boolean = false) {
        // A job can be "active" while its coroutine is in its dying breath
        // (state already flipped to Stopped/Error after awaitExit). Restarting
        // then is the right thing to do — only skip while actually live.
        val live = proxyJob?.isActive == true &&
            (_state.value is DeepSeekProxyState.Installing ||
                _state.value is DeepSeekProxyState.Starting ||
                _state.value is DeepSeekProxyState.Running)
        if (!force && live) {
            AppLogger.d("FreeDeepSeekManager", "Already running or starting (state=${_state.value}), skipping start()")
            return
        }
        stop()

        val instance = dataRepository.getConfiguredServiceInstances().find { it.serviceId == "freedeepseekproxy" }
        if (instance == null) {
            _state.value = DeepSeekProxyState.Error("Service not configured")
            return
        }
        proxyJob = scope.launch {
            try {
                // Wait for sandbox. Fresh rootfs download can take minutes, so poll
                // with a generous ceiling instead of a fixed 30s window.
                if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                    linuxSandboxManager.setup()
                    val started = System.currentTimeMillis()
                    while (System.currentTimeMillis() - started < 15 * 60 * 1000L) {
                        val s = linuxSandboxManager.state.value
                        if (s is SandboxState.Ready) break
                        if (s is SandboxState.Error) {
                            _state.value = DeepSeekProxyState.Error("Sandbox error: $s")
                            return@launch
                        }
                        kotlinx.coroutines.delay(2000)
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        _state.value = DeepSeekProxyState.Error("Sandbox not ready: ${linuxSandboxManager.state.value}")
                        return@launch
                    }
                }

                _state.value = DeepSeekProxyState.Installing
                val executor = linuxSandboxManager.createProotExecutor()

                // Ensure Node.js and Git are installed
                val distro = try {
                    org.koin.core.context.GlobalContext.get().get<com.katya.app.data.AppSettings>()
                        .getDistro()
                } catch (_: Exception) {
                    com.katya.app.data.Distro.DEBIAN
                }
                val installCmd = when (distro) {
                    com.katya.app.data.Distro.DEBIAN -> "apt-get install -y --no-install-recommends nodejs npm git"
                    com.katya.app.data.Distro.TERMUX -> "apk add nodejs npm git"
                }
                executor.execute(installCmd)

                val repoPath = "/root/FreeDeepSeekAPI"
                val checkRepo = executor.execute("test -d $repoPath")
                if (checkRepo["exit_code"] != 0) {
                    AppLogger.d("FreeDeepSeekManager", "Cloning FreeDeepSeekAPI")
                    executor.execute("git clone https://github.com/ForgetMeAI/FreeDeepSeekAPI.git $repoPath")
                    executor.execute("cd $repoPath && npm install")
                } else {
                    AppLogger.d("FreeDeepSeekManager", "Pulling latest FreeDeepSeekAPI")
                    executor.execute("cd $repoPath && git pull")
                    executor.execute("cd $repoPath && npm install")
                }

                _state.value = DeepSeekProxyState.Starting

                // Retrieve the DeepSeek session (if any) to pre-populate auth file.
                // The api key stores the bare token; the per-instance session JSON
                // carries the full cookie + anti-bot headers (x-hif-dliq/x-hif-leim)
                // required by newer FreeDeepseekAPI builds.
                val sessionToken = dataRepository.getInstanceApiKey(instance.instanceId)
                val sessionJson = dataRepository.getInstanceDeepSeekSession(instance.instanceId)

                // Write deepseek-auth.json via Android File API:
                // homePath is bind-mounted as /root inside proot, so:
                // <homePath>/FreeDeepSeekAPI/deepseek-auth.json == /root/FreeDeepSeekAPI/deepseek-auth.json inside proot
                val authContent = buildDeepSeekAuthJson(sessionToken, sessionJson)
                if (authContent != null) {
                    val authFile = File(linuxSandboxManager.homePath, "FreeDeepSeekAPI/deepseek-auth.json")
                    authFile.parentFile?.mkdirs()
                    authFile.writeText(authContent)
                    AppLogger.d("FreeDeepSeekManager", "Wrote auth file to: ${authFile.absolutePath}, exists=${authFile.exists()}, size=${authFile.length()}")
                } else {
                    AppLogger.d("FreeDeepSeekManager", "No valid session token, starting server without auth (user must authorize via DeepSeek button)")
                }

                // Verify the file is visible inside proot
                val verifyResult = executor.execute("cat $repoPath/deepseek-auth.json")
                val verifyExit = verifyResult["exit_code"]
                val verifyContent = verifyResult["stdout"]?.toString()?.take(80)
                AppLogger.d("FreeDeepSeekManager", "Auth file inside proot: exit_code=$verifyExit, content=$verifyContent")

                val proxyEnv = if (dataRepository.isVlessEnabled()) {
                    "HTTP_PROXY=http://127.0.0.1:10809 HTTPS_PROXY=http://127.0.0.1:10809 http_proxy=http://127.0.0.1:10809 https_proxy=http://127.0.0.1:10809 ALL_PROXY=socks5://127.0.0.1:10808 all_proxy=socks5://127.0.0.1:10808 "
                } else {
                    ""
                }

                prootHandle = executor.executeStreaming(
                    // Non-interactive: skip the startup menu entirely (menu semantics changed
                    // between FreeDeepseekAPI versions, so echoing a menu number is fragile).
                    // PORT/HOST env vars pick the listening address. Requires deepseek-auth.json,
                    // which we pre-write above when a token is set; otherwise the server exits
                    // fatally instead of hanging on the interactive menu in the background.
                    command = "cd $repoPath && ${proxyEnv}NON_INTERACTIVE=1 PORT=11434 HOST=127.0.0.1 npm start",
                    onStdout = {
                        AppLogger.d("DeepSeekOut", it)
                        if (it.contains("running on") || it.contains("listening") || it.contains("started")) {
                            _state.value = DeepSeekProxyState.Running
                        }
                    },
                    onStderr = { Log.e("DeepSeekErr", it) },
                )
                prootHandle?.awaitExit()
                _state.value = DeepSeekProxyState.Stopped
            } catch (e: Exception) {
                Log.e("FreeDeepSeekManager", "Error running DeepSeek proxy", e)
                _state.value = DeepSeekProxyState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stop() {
        val wasActive = proxyJob?.isActive == true || prootHandle != null
        proxyJob?.cancel()
        proxyJob = null

        prootHandle?.cancel()
        prootHandle = null
        _state.value = DeepSeekProxyState.Stopped
        // Keep logs quiet on fresh cold starts where there is nothing to stop yet;
        // the noisy "Stopping DeepSeek proxy" pairs in the log were causing confusion.
        if (wasActive) AppLogger.d("FreeDeepSeekManager", "Stopping DeepSeek proxy")
    }

    suspend fun runDoctor(): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val executor = linuxSandboxManager.createProotExecutor()
            val repoPath = "/root/FreeDeepSeekAPI"
            val proxyEnv = if (dataRepository.isVlessEnabled()) {
                "HTTP_PROXY=http://127.0.0.1:10809 HTTPS_PROXY=http://127.0.0.1:10809 http_proxy=http://127.0.0.1:10809 https_proxy=http://127.0.0.1:10809 ALL_PROXY=socks5://127.0.0.1:10808 all_proxy=socks5://127.0.0.1:10808 "
            } else {
                ""
            }
            AppLogger.d("FreeDeepSeekManager", "Running npm run doctor...")
            val result = executor.execute("cd $repoPath && ${proxyEnv}npm run doctor")
            val out = result["stdout"]?.toString() ?: ""
            val err = result["stderr"]?.toString() ?: ""
            AppLogger.d("FreeDeepSeekManager", "Doctor completed. Code: ${result["exit_code"]}")
            if (out.isNotBlank()) out else err
        } catch (e: Exception) {
            AppLogger.e("FreeDeepSeekManager", "Doctor failed: ${e.message}")
            "Error: ${e.message}"
        }
    }

    /**
     * Builds `deepseek-auth.json` for FreeDeepseekAPI.
     *
     * Priority: full JSON session (token+cookie+hif+wasmUrl) if the user went
     * through the in-app DeepSeek dialog; otherwise falls back to the bare api
     * key (token only) for backwards compatibility.
     *
     * JSON is built with kotlinx.serialization so token/cookie strings with
     * quotes or backslashes can't corrupt the file. Returns null when there is
     * no usable token.
     */
    private fun buildDeepSeekAuthJson(sessionToken: String, sessionJson: String): String? {
        var token = sessionToken
        var cookie = "user_session=$sessionToken"
        var hifDliq = ""
        var hifLeim = ""
        var wasmUrl = "https://fe-static.deepseek.com/chat/static/sha3_wasm_bg.7b9ca65ddd.wasm"

        val parsed = sessionJson.trim().takeIf { it.startsWith("{") }?.let { raw ->
            try {
                Json { ignoreUnknownKeys = true }.decodeFromString(
                    com.katya.app.ui.settings.DeepSeekAuthSession.serializer(),
                    raw,
                )
            } catch (e: Exception) {
                AppLogger.w("FreeDeepSeekManager", "Stored DeepSeek session is not valid JSON: ${e.message}")
                null
            }
        }
        if (parsed != null) {
            if (parsed.token.isNotBlank()) token = parsed.token
            if (parsed.cookie.isNotBlank()) cookie = parsed.cookie
            hifDliq = parsed.hifDliq.orEmpty()
            hifLeim = parsed.hifLeim.orEmpty()
            if (parsed.wasmUrl.isNotBlank()) wasmUrl = parsed.wasmUrl
        }

        if (token.isBlank() || token.startsWith("{")) return null

        val session = com.katya.app.ui.settings.DeepSeekAuthSession(
            token = token,
            cookie = cookie,
            hifDliq = hifDliq,
            hifLeim = hifLeim,
            wasmUrl = wasmUrl,
        )
        return Json.encodeToString(
            com.katya.app.ui.settings.DeepSeekAuthSession.serializer(),
            session,
        )
    }
}
