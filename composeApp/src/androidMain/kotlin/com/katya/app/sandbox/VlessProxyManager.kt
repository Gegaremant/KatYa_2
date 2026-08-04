package com.katya.app.sandbox

import android.util.Log
import com.katya.app.data.DataRepository
import com.katya.app.tools.AppLogger
import com.katya.app.tools.VlessParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class VlessProxyManager(
    private val dataRepository: DataRepository,
    private val linuxSandboxManager: LinuxSandboxManager,
) {
    private var proxyJob: Job? = null
    private var prootHandle: ProotHandle? = null
    private var rootProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        stop()

        if (!dataRepository.isVlessEnabled()) return
        val uri = dataRepository.getVlessUri()
        if (uri.isBlank()) return

        proxyJob = scope.launch {
            try {
                // Wait for sandbox to be ready
                if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                    linuxSandboxManager.setup()
                    // Wait for it to become ready
                    var waitCount = 0
                    while (linuxSandboxManager.state.value !is SandboxState.Ready && waitCount < 30) {
                        kotlinx.coroutines.delay(1000)
                        waitCount++
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        Log.e("VlessProxyManager", "Sandbox not ready, aborting proxy start")
                        return@launch
                    }
                }

                var finalUri = uri
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    finalUri = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val response = java.net.URL(uri).readText()
                            var decoded = response
                            try {
                                if (!response.contains("://")) {
                                    decoded = String(android.util.Base64.decode(response.trim(), android.util.Base64.DEFAULT))
                                }
                            } catch (e: Exception) {}
                            
                            decoded.lines().firstOrNull { it.trim().startsWith("vless://") }?.trim() ?: uri
                        } catch (e: Exception) {
                            Log.e("VlessProxyManager", "Failed to fetch subscription", e)
                            uri
                        }
                    }
                }
                
                // Generate config.json
                val configJson = VlessParser.generateXrayConfig(finalUri)
                val configFilePath = File(linuxSandboxManager.homePath, "xray_config.json")
                configFilePath.writeText(configJson)

                val configPathInSandbox = "/root/xray_config.json"
                val xrayBinary = "/usr/bin/xray"

                // Check for root
                val isRooted = try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    process.waitFor() == 0
                } catch (e: Exception) {
                    false
                }

                if (isRooted) {
                    AppLogger.d("VlessProxyManager", "Starting xray with root privileges")
                    val prootPath = linuxSandboxManager.prootPath
                    val rootfs = linuxSandboxManager.rootfsPath
                    val home = linuxSandboxManager.homePath
                    val tmp = linuxSandboxManager.tmpPath

                    val command = "$prootPath -0 --rootfs=$rootfs --bind=/dev --bind=/proc --bind=/sys --bind=$home:/root --bind=$tmp:/tmp -w /root /bin/sh -c '$xrayBinary -c $configPathInSandbox'"

                    rootProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    rootProcess?.waitFor()
                } else {
                    AppLogger.d("VlessProxyManager", "Starting xray with proot (non-root)")
                    val executor = linuxSandboxManager.createProotExecutor()
                    prootHandle = executor.executeStreaming(
                        command = "$xrayBinary -c $configPathInSandbox",
                        onStdout = { AppLogger.d("XrayOut", it) },
                        onStderr = { Log.e("XrayErr", it) },
                    )
                    prootHandle?.awaitExit()
                }
            } catch (e: Exception) {
                Log.e("VlessProxyManager", "Error starting VLESS proxy", e)
            }
        }
    }

    fun stop() {
        AppLogger.d("VlessProxyManager", "Stopping VLESS proxy")
        proxyJob?.cancel()
        proxyJob = null

        prootHandle?.cancel()
        prootHandle = null

        rootProcess?.destroyForcibly()
        rootProcess = null
    }
}
