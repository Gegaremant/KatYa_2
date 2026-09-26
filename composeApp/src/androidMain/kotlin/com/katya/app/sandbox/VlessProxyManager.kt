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

        /**
         * Where xray should look for trusted CAs.
         *
         * Our xray binaries are statically linked linux/arm builds, and Go only adds
         * Android's certificate directory when it is compiled with GOOS=android. A
         * stock static build therefore finds no roots at all, and a VLESS server with
         * `security=tls` fails verification with an empty pool. Go honours the
         * OpenSSL-compatible variables, so we point it at the right place ourselves
         * instead of shipping a second NDK-built binary per ABI.
         */
        const val SSL_CERT_DIR_ENV = "SSL_CERT_DIR=/system/etc/security/cacerts"
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
            appSettings.setVlessStatusReason("")
            return
        }
        val uri = dataRepository.getVlessUri()
        if (uri.isBlank()) {
            AppLogger.e("VlessProxyManager", "VLESS URI is blank — not starting proxy")
            appSettings.setVlessStatusReason("Не задан VLESS-адрес")
            return
        }
        AppLogger.d("VlessProxyManager", "VLESS URI: ${AppLogger.secretFingerprint(uri)}")
        recoveryCount = 0
        appSettings.setVlessStatusReason("Подключаюсь…")

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
                            // Say *why* the tunnel is dead instead of leaving a green
                            // tick and a silent log — the sandbox gate is where every
                            // attempt used to die without the UI ever learning about it.
                            appSettings.setVlessStatusReason("Песочница не готова: ${s.message}")
                            break
                        }
                        appSettings.setVlessStatusReason("Готовлю песочницу…")
                        kotlinx.coroutines.delay(2000)
                    }
                    if (linuxSandboxManager.state.value !is SandboxState.Ready) {
                        AppLogger.e("VlessProxyManager", "Sandbox not ready, aborting proxy start")
                        if (linuxSandboxManager.state.value !is SandboxState.Error) {
                            appSettings.setVlessStatusReason("Песочница не готова")
                        }
                        return@launch
                    }
                }

                val directProxy = com.katya.app.network.ProxyResolver.resolveDirectProxy(uri)
                if (directProxy != null) {
                    AppLogger.d("VlessProxyManager", "Using direct proxy $directProxy, skipping xray launch")
                    appSettings.setSystemStatus("Использую прямой SOCKS/HTTP прокси")
                    appSettings.setVlessStatusReason("Прямой прокси")
                    launchConnectionLoop()
                    // Keep the job alive until cancelled; the connection loop owns liveness.
                    awaitCancellation()
                }

                val finalUri = resolveFinalUri(uri)
                appSettings.setSystemStatus("Настраиваю туннель VLESS...")
                appSettings.setVlessStatusReason("Запускаю xray…")
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
            AppLogger.d("VlessProxyManager", "Subscription URL had no scheme — prepending http://")
        }

        if (finalUri.startsWith("http://") || finalUri.startsWith("https://")) {
            finalUri = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    // Android's HttpURLConnection does not reliably follow cross-scheme
                    // (http -> https) 307 redirects, which our subscription endpoints emit.
                    // Walk the redirect chain manually instead.
                    val response = fetchWithRedirects(finalUri)
                    AppLogger.d("VlessProxyManager", "Fetched subscription response: ${AppLogger.secretFingerprint(response)}")

                    // 1) Plain-text vless:// lines — the common subscription format.
                    val lines = response.lines().map { it.trim().removePrefix("\uFEFF") }
                    var foundVless = lines.firstOrNull { it.startsWith("vless://") }
                    val linkCount = lines.count { it.startsWith("vless://") }

                    // 2) Some servers base64-encode the whole payload.
                    if (foundVless == null) {
                        try {
                            val decoded = String(android.util.Base64.decode(response.trim(), android.util.Base64.DEFAULT))
                            foundVless = decoded.lines().map { it.trim().removePrefix("\uFEFF") }
                                .firstOrNull { it.startsWith("vless://") }
                        } catch (e: Exception) {}
                    }

                    if (foundVless == null) {
                        AppLogger.e(
                            "VlessProxyManager",
                            "Could not find vless:// link in subscription response (lines=${lines.size}, sample=${response.take(120).replace('\n', ' ')})",
                        )
                    } else {
                        AppLogger.d("VlessProxyManager", "Subscription parsed OK: $linkCount vless links, first=${AppLogger.secretFingerprint(foundVless)}")
                    }
                    foundVless ?: uri
                } catch (e: Exception) {
                    AppLogger.e("VlessProxyManager", "Failed to fetch subscription: ${e.message}")
                    uri
                }
            }
        }
        AppLogger.d("VlessProxyManager", "Final URI after processing: ${AppLogger.secretFingerprint(finalUri)}")
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
            // Say so and stop *before* asking for root. Asking the user to grant
            // superuser for a tunnel that has no binary to launch is exactly the
            // confusing "root requested, nothing happened" the field reports showed.
            val reason = "Бинарник xray не найден для ${com.katya.app.components.currentAbi()} — " +
                "перекачай компонент заново или пользуйся обычным подключением"
            AppLogger.e("VlessProxyManager", reason)
            appSettings.setVlessConnected(false)
            appSettings.setVlessStatusReason(reason)
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
            val command =
                "$SSL_CERT_DIR_ENV ${xrayNativeBinary.absolutePath} -c ${configFilePath.absolutePath}"
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
                .apply { environment()[SSL_CERT_DIR_ENV.substringBefore('=')] = SSL_CERT_DIR_ENV.substringAfter('=') }
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
                if (ok) appSettings.setVlessStatusReason("")
                if (!ok) {
                    failureCount++
                    appSettings.setVlessStatusReason("Туннель не отвечает (попытка $failureCount)")
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

    /**
     * Fetches a URL body, manually following up to [maxRedirects] redirects (including
     * http -> https 307s that HttpURLConnection's built-in follower skips). Throws on
     * non-2xx status or when the redirect chain is too long.
     */
    private fun fetchWithRedirects(startUrl: String, maxRedirects: Int = 6): String {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36",
            )
            connection.setRequestProperty("Accept", "text/plain, application/octet-stream, */*")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")?.trim().orEmpty()
                    if (location.isEmpty()) throw RuntimeException("Redirect ($code) without Location header")
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        java.net.URI(currentUrl).resolve(location).toString()
                    }
                    AppLogger.d("VlessProxyManager", "Subscription redirect $code -> ${AppLogger.secretFingerprint(currentUrl)}")
                    return@repeat
                }
                if (code !in 200..299) {
                    // Drain the error stream so the connection can be reused/closed cleanly.
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw RuntimeException("Subscription HTTP $code")
                }
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        throw RuntimeException("Too many redirects for subscription URL")
    }

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
