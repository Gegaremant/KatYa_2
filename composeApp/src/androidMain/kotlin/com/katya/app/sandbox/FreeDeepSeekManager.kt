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
        if (!force && (_state.value is DeepSeekProxyState.Running || _state.value is DeepSeekProxyState.Starting || _state.value is DeepSeekProxyState.Installing)) {
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
                // Wait for sandbox
                if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                    linuxSandboxManager.setup()
                    var waitCount = 0
                    while (linuxSandboxManager.state.value !is SandboxState.Ready && waitCount < 30) {
                        if (linuxSandboxManager.state.value is SandboxState.Error) break
                        kotlinx.coroutines.delay(1000)
                        waitCount++
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        _state.value = DeepSeekProxyState.Error("Sandbox not ready: ${linuxSandboxManager.state.value}")
                        return@launch
                    }
                }

                _state.value = DeepSeekProxyState.Installing
                val executor = linuxSandboxManager.createProotExecutor()
                
                // Ensure Node.js and Git are installed
                executor.execute("apk add nodejs npm git")

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

                // Retrieve the DeepSeek session token (if any) to pre-populate auth file
                val sessionToken = dataRepository.getInstanceApiKey(instance.instanceId)
                
                // Write deepseek-auth.json via Android File API:
                // homePath is bind-mounted as /root inside proot, so:
                // <homePath>/FreeDeepSeekAPI/deepseek-auth.json == /root/FreeDeepSeekAPI/deepseek-auth.json inside proot
                if (sessionToken.isNotBlank() && !sessionToken.startsWith("{")) {
                    val authFile = File(linuxSandboxManager.homePath, "FreeDeepSeekAPI/deepseek-auth.json")
                    authFile.parentFile?.mkdirs()
                    val authContent = """{"token":"$sessionToken","cookie":"user_session=$sessionToken","wasmUrl":"https://fe-static.deepseek.com/chat/static/sha3_wasm_bg.7b9ca65ddd.wasm"}"""
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
                } else ""

                prootHandle = executor.executeStreaming(
                    // Pass '4' to select "Запустить прокси" from the menu; use PORT/HOST env vars
                    command = "cd $repoPath && echo '4' | ${proxyEnv}PORT=11434 HOST=127.0.0.1 npm start",
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
        AppLogger.d("FreeDeepSeekManager", "Stopping DeepSeek proxy")
        proxyJob?.cancel()
        proxyJob = null

        prootHandle?.cancel()
        prootHandle = null
        _state.value = DeepSeekProxyState.Stopped
    }
}
