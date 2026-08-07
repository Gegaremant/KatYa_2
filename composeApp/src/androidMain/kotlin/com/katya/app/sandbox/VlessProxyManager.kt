package com.katya.app.sandbox

import android.util.Log
import com.katya.app.data.DataRepository
import com.katya.app.data.AppSettings
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
    private val appSettings: AppSettings,
) {
    private var proxyJob: Job? = null
    private var prootHandle: ProotHandle? = null
    private var rootProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(force: Boolean = false) {
        if (!force && proxyJob?.isActive == true) {
            AppLogger.d("VlessProxyManager", "Already running or starting, skipping start()")
            return
        }
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
                        if (linuxSandboxManager.state.value is SandboxState.Error) {
                            AppLogger.e("VlessProxyManager", "Sandbox error: ${linuxSandboxManager.state.value}")
                            break
                        }
                        kotlinx.coroutines.delay(1000)
                        waitCount++
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        AppLogger.e("VlessProxyManager", "Sandbox not ready, aborting proxy start")
                        return@launch
                    }
                }

                val directProxy = com.katya.app.network.ProxyResolver.resolveDirectProxy(uri)
                if (directProxy != null) {
                    AppLogger.d("VlessProxyManager", "Using direct proxy $directProxy, skipping xray launch")
                    appSettings.setSystemStatus("Использую прямой SOCKS/HTTP прокси")
                    launchConnectionLoop()
                    return@launch
                }

                var finalUri = uri
                if (!uri.startsWith("vless://") && !uri.startsWith("http://") && !uri.startsWith("https://")) {
                    finalUri = "http://$uri"
                    AppLogger.d("VlessProxyManager", "Prepended http:// to URI: $finalUri")
                }
                
                if (finalUri.startsWith("http://") || finalUri.startsWith("https://")) {
                    finalUri = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val urlObj = java.net.URL(finalUri)
                            val connection = urlObj.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 10000 // 10 seconds
                            connection.readTimeout = 10000 // 10 seconds
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            AppLogger.d("VlessProxyManager", "Fetched subscription response (length ${response.length}): ${response.take(100)}")
                            
                            var decoded = response
                            try {
                                if (!response.contains("://")) {
                                    decoded = String(android.util.Base64.decode(response.trim(), android.util.Base64.DEFAULT))
                                }
                            } catch (e: Exception) {}
                            
                            val foundVless = decoded.lines().firstOrNull { it.trim().startsWith("vless://") }?.trim()
                            if (foundVless == null) {
                                AppLogger.e("VlessProxyManager", "Could not find vless:// link in subscription response")
                            }
                            foundVless ?: uri
                        } catch (e: Exception) {
                            AppLogger.e("VlessProxyManager", "Failed to fetch subscription: ${e.message}")
                            uri
                        }
                    }
                }
                
                // Generate config.json
                appSettings.setSystemStatus("Настраиваю туннель VLESS...")
                val configJson = VlessParser.generateXrayConfig(finalUri)
                val configFilePath = File(linuxSandboxManager.homePath, "xray_config.json")
                configFilePath.writeText(configJson)

                val configPathInSandbox = "/root/xray_config.json"
                val xrayBinary = "/usr/bin/xray"

                launchConnectionLoop()

                // Check for root
                val isRooted = try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    process.waitFor() == 0
                } catch (e: Exception) {
                    false
                }

                if (isRooted) {
                    AppLogger.d("VlessProxyManager", "Starting xray with root privileges")
                    appSettings.setSystemStatus("Запрашиваю root-права для VLESS")
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
                        onStderr = { AppLogger.e("XrayErr", it) },
                    )
                    prootHandle?.awaitExit()
                }
            } catch (e: Exception) {
                AppLogger.e("VlessProxyManager", "Error starting VLESS proxy: ${e.message}")
            } finally {
                appSettings.setSystemStatus(null)
                appSettings.setVlessConnected(false)
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

        appSettings.setVlessConnected(false)
    }

    private fun CoroutineScope.launchConnectionLoop() {
        launch {
            var failureCount = 0
            while (kotlin.coroutines.coroutineContext[Job]?.isActive == true) {
                kotlinx.coroutines.delay(2000) // wait 2s for xray/network
                val ok = checkConnection()
                appSettings.setVlessConnected(ok)
                if (!ok) {
                    failureCount++
                    if (failureCount >= 3) {
                        AppLogger.e("VlessProxyManager", "Connection failed 3 times, attempting recovery...")
                        attemptRecovery()
                        failureCount = 0
                    }
                } else {
                    failureCount = 0
                }
                kotlinx.coroutines.delay(10000) // check every 10s
            }
        }
    }

    private suspend fun attemptRecovery() {
        try {
            val isRooted = try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
            } catch (e: Exception) { false }
            
            if (isRooted) {
                AppLogger.d("VlessProxyManager", "Toggling Airplane mode...")
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE")).waitFor()
                kotlinx.coroutines.delay(2000)
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE")).waitFor()
                kotlinx.coroutines.delay(5000) // wait for network to reconnect
                start(force = true)
            } else {
                AppLogger.d("VlessProxyManager", "No root, cannot toggle airplane mode. Restarting proxy...")
                start(force = true)
            }
        } catch (e: Exception) {
            AppLogger.e("VlessProxyManager", "Recovery failed: ${e.message}")
        }
    }

    private fun checkConnection(): Boolean {
        return try {
            val uri = dataRepository.getVlessUri()
            val proxy = com.katya.app.network.ProxyResolver.resolveDirectProxy(uri) 
                ?: java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809))
                
            val connection = java.net.URL("https://www.google.com").openConnection(proxy) as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
