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

    fun start() {
        stop()

        val instance = dataRepository.getConfiguredServiceInstances().find { it.serviceId == "freedeepseekproxy" }
        if (instance == null) {
            _state.value = DeepSeekProxyState.Error("Service not configured")
            return
        }
        val sessionToken = dataRepository.getInstanceApiKey(instance.instanceId)
        if (sessionToken.isBlank()) {
            _state.value = DeepSeekProxyState.Error("Session token missing")
            return
        }

        proxyJob = scope.launch {
            try {
                // Wait for sandbox
                if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                    linuxSandboxManager.setup()
                    var waitCount = 0
                    while (linuxSandboxManager.state.value !is SandboxState.Ready && waitCount < 30) {
                        kotlinx.coroutines.delay(1000)
                        waitCount++
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        _state.value = DeepSeekProxyState.Error("Sandbox not ready")
                        return@launch
                    }
                }

                _state.value = DeepSeekProxyState.Installing
                val executor = linuxSandboxManager.createProotExecutor()
                
                // Ensure Python and Git are installed (Depends on the base image, Katya's sandbox usually has them or can install via apk/apt)
                // Assuming it's Alpine Linux based on KatYa sandbox defaults
                executor.execute("apk add python3 py3-pip git")

                val repoPath = "/root/FreeDeepSeekAPI"
                val checkRepo = executor.execute("test -d $repoPath")
                if (checkRepo["exit_code"] != 0) {
                    AppLogger.d("FreeDeepSeekManager", "Cloning FreeDeepSeekAPI")
                    executor.execute("git clone https://github.com/ForgetMeAI/FreeDeepSeekAPI.git $repoPath")
                    executor.execute("cd $repoPath && pip install --break-system-packages -r requirements.txt")
                } else {
                    AppLogger.d("FreeDeepSeekManager", "Pulling latest FreeDeepSeekAPI")
                    executor.execute("cd $repoPath && git pull")
                    executor.execute("cd $repoPath && pip install --break-system-packages -r requirements.txt")
                }

                _state.value = DeepSeekProxyState.Starting
                
                // Write .env file
                val envFile = File(linuxSandboxManager.homePath, "FreeDeepSeekAPI/.env")
                envFile.writeText("PORT=8000\nHOST=127.0.0.1\n")

                prootHandle = executor.executeStreaming(
                    command = "cd $repoPath && python3 app.py",
                    onStdout = { 
                        AppLogger.d("DeepSeekOut", it)
                        if (it.contains("Uvicorn running on") || it.contains("Application startup complete")) {
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
