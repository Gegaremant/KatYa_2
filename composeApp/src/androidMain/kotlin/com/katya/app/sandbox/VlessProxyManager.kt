package com.katya.app.sandbox

import android.util.Log
import com.katya.app.data.AppSettings
import com.katya.app.data.DataRepository
import com.katya.app.tools.AppLogger
import com.katya.app.tools.VlessParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
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

    private companion object {
        /**
         * Cap on consecutive transport restarts within one proxy session. If the config or
         * network is fundamentally broken, we stop hammering the binary and let the next
         * explicit start() try again — otherwise a dead config relaunches forever.
         */
        const val MAX_CONSECUTIVE_RECOVERIES = 3

        /** Ceiling for waiting on a fresh rootfs download/install at first run. */
        const val MAX_SANDBOX_WAIT_MS = 15 * 60 * 1000L
    }

    private var recoveryCount = 0

    fun start(force: Boolean = false) {
        // If the proxy is already running and VLESS is still enabled, keep it as is.
        // But if the user just turned VLESS OFF, "already running" must not skip the
        // stop() below — otherwise the tunnel keeps serving a disabled config while
        // the global ProxySelector already stopped routing through it.
        if (!force && proxyJob?.isActive == true && dataRepository.isVlessEnabled()) {
            AppLogger.d("VlessProxyManager", "Already running or starting, skipping start()")
            return
        }
        stop()

        if (!dataRepository.isVlessEnabled()) {
            AppLogger.d("VlessProxyManager", "VLESS disabled — not starting proxy")
            return
        }
        val uri = dataRepository.getVlessUri()
        if (uri.isBlank()) {
            AppLogger.e("VlessProxyManager", "VLESS URI is blank — not starting proxy")
            return
        }
        AppLogger.d("VlessProxyManager", "VLESS URI (truncated): ${uri.take(100)}")
        recoveryCount = 0

        proxyJob = scope.launch {
            try {
                // Wait for sandbox to be ready. A fresh Debian rootfs download can
                // take minutes — polling a fixed 30s would abort every first start.
                // Keep waiting until Ready/Error with a generous ceiling.
                if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                    linuxSandboxManager.setup()
                    val started = System.currentTimeMillis()
                    while (System.currentTimeMillis() - started < MAX_SANDBOX_WAIT_MS) {
                        val s = linuxSandboxManager.state.value
                        if (s is SandboxState.Ready) break
                        if (s is SandboxState.Error) {
                            AppLogger.e("VlessProxyManager", "Sandbox error: $s")
                            break
                        }
                        kotlinx.coroutines.delay(2000)
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
                    // Keep the job alive until cancelled; the connection loop owns liveness.
                    awaitCancellation()
                }

                val finalUri = resolveFinalUri(uri)
                appSettings.setSystemStatus("Настраиваю туннель VLESS...")
                val configJson = VlessParser.generateXrayConfig(finalUri)
                AppLogger.d("VlessProxyManager", "Generated config JSON length: ${configJson.length}")
                val configFilePath = File(linuxSandboxManager.homePath, "xray_config.json")
                configFilePath.writeText(configJson)
                AppLogger.d("VlessProxyManager", "Config written to: ${configFilePath.absolutePath}")

                launchConnectionLoop()

                // Transport process lifecycle: run xray, and when it exits (crash/kill)
                // relaunch it after a short pause — without tearing down the sandbox or
                // the whole proxy job (the old recovery called start(force=true), which
                // produced endless "Stopping VLESS proxy" log pairs and airplane-mode toggles).
                while (isActive) {
                    launchXrayProcess(configFilePath)
                    kotlinx.coroutines.delay(5000)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("VlessProxyManager", "Error starting VLESS proxy: ${e.message}\n${e.stackTraceToString()}")
            } finally {
                appSettings.setSystemStatus(null)
                appSettings.setVlessConnected(false)
            }
        }
    }

    /**
     * Resolves a bare HTTP(S) subscription URL into an actual vless:// link.
     * Falls back to the original URI when fetching or parsing fails.
     */
    private suspend fun resolveFinalUri(uri: String): String {
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
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
        AppLogger.d("VlessProxyManager", "Final URI after processing: ${finalUri.take(100)}")
        return finalUri
    }

    /**
     * Starts (or restarts) the xray transport process using the already-generated config.
     * Blocks until the process exits; the caller's loop decides whether to relaunch.
     * Works for both rooted (su) and non-rooted devices.
     */
    private suspend fun launchXrayProcess(configFilePath: File) {
        rootProcess?.destroyForcibly()
        rootProcess = null

        val nativeLibDir = linuxSandboxManager.nativeLibDir
        val xrayNativeBinary = File(nativeLibDir, "libxray.so")
        if (!xrayNativeBinary.exists()) {
            AppLogger.e("VlessProxyManager", "xray binary not found at ${xrayNativeBinary.absolutePath}")
            return
        }

        val isRooted = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val ok = process.waitFor() == 0
            AppLogger.rootAction("Запрос root-прав (VLESS/xray)", if (ok) "доступ предоставлен — OK" else "отклонён/нет su")
            ok
        } catch (e: Exception) {
            AppLogger.rootAction("Запрос root-прав (VLESS/xray)", "ОШИБКА: ${e.message}")
            false
        }

        if (isRooted) {
            AppLogger.d("VlessProxyManager", "Starting xray with root privileges natively")
            appSettings.setSystemStatus("Запрашиваю root-права для VLESS")
            val command = "${xrayNativeBinary.absolutePath} -c ${configFilePath.absolutePath}"
            AppLogger.d("VlessProxyManager", "Root command: $command")
            AppLogger.rootAction("Запуск xray от root: $command", "выполняется")
            rootProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            rootProcess?.waitFor()
        } else {
            AppLogger.d("VlessProxyManager", "Starting xray natively (non-root)")
            val command = arrayOf(xrayNativeBinary.absolutePath, "-c", configFilePath.absolutePath)
            AppLogger.d("VlessProxyManager", "Non-root command: ${command.joinToString(" ")}")
            rootProcess = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(rootProcess!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                AppLogger.d("XrayOut", line ?: "")
            }
            rootProcess?.waitFor()
        }
        rootProcess = null
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
            var announcedFallback = false
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
                        // Three strikes: don't keep hammering the tunnel for every request.
                        // isVlessConnected stays false, so the ProxySelector and the sandbox
                        // DeepSeek server fall back to the standard direct channel. Recovery
                        // itself is capped at MAX_CONSECUTIVE_RECOVERIES — after that the next
                        // attempt only comes from an explicit start().
                        if (!announcedFallback) {
                            AppLogger.e(
                                "VlessProxyManager",
                                "Туннель не поднялся после 3 проверок — ошибка записана, " +
                                    "запросы идут через стандартный канал. Мучать прокси больше не будем.",
                            )
                            appSettings.setSystemStatus("VLESS недоступен — стандартный канал")
                            announcedFallback = true
                        }
                    }
                } else {
                    failureCount = 0
                    if (announcedFallback) appSettings.setSystemStatus(null)
                    announcedFallback = false
                }
                kotlinx.coroutines.delay(10000) // check every 10s
            }
        }
    }

    /**
     * Restarts only the xray transport process. Deliberately does NOT call start(force=true):
     * that used to cancel the whole proxy job, relaunch the connection loop and (on rooted
     * devices) toggle airplane mode — turning a flaky network into an endless
     * "Stopping VLESS proxy / airplane mode toggle" cycle.
     */
    private suspend fun attemptRecovery() {
        try {
            if (rootProcess?.isAlive == true) {
                AppLogger.d("VlessProxyManager", "Xray process alive, skipping recovery")
                return
            }
            val configFilePath = File(linuxSandboxManager.homePath, "xray_config.json")
            if (!configFilePath.exists()) {
                AppLogger.e("VlessProxyManager", "Recovery skipped: no config file yet")
                return
            }
            if (recoveryCount >= MAX_CONSECUTIVE_RECOVERIES) {
                AppLogger.e("VlessProxyManager", "Recovery limit reached ($MAX_CONSECUTIVE_RECOVERIES) — waiting for the next explicit start()")
                return
            }
            recoveryCount++
            AppLogger.d("VlessProxyManager", "Restarting xray transport (attempt $recoveryCount/$MAX_CONSECUTIVE_RECOVERIES)")
            launchXrayProcess(configFilePath)
        } catch (e: Exception) {
            AppLogger.e("VlessProxyManager", "Recovery failed: ${e.message}")
        }
    }

    private var lastCheckErrorLogMs = 0L

    private fun checkConnection(): Boolean = try {
        val uri = dataRepository.getVlessUri()
        val proxy = com.katya.app.network.ProxyResolver.resolveDirectProxy(uri)
            ?: java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809))

        val connection = java.net.URL("https://1.1.1.1").openConnection(proxy) as java.net.HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.connect()
        val code = connection.responseCode
        connection.disconnect()
        AppLogger.d("VlessProxyManager", "Connection check response code: $code")
        code in 200..399
    } catch (e: Exception) {
        // The loop re-checks every ~12s — a full stack trace per tick floods the log
        // without adding information. One concise line per 30s is enough to diagnose.
        val now = System.currentTimeMillis()
        if (now - lastCheckErrorLogMs > 30_000L) {
            AppLogger.e("VlessProxyManager", "Connection check failed: ${e.message}")
            lastCheckErrorLogMs = now
        }
        false
    }
}