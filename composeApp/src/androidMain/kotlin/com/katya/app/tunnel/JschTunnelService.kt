package com.katya.app.tunnel

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.katya.app.tools.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

class JschTunnelService : SshTunnelService {
    private val _tunnelState = MutableStateFlow(TunnelState())
    override val tunnelState: StateFlow<TunnelState> = _tunnelState

    private var tunnelJob: Job? = null
    private var session: Session? = null

    override suspend fun startTunnel(
        localPort: Int,
        remotePort: Int,
        sshIp: String,
        sshPort: Int,
        sshUser: String,
        sshPass: String,
        persistentReconnect: Boolean,
    ) {
        // Cancel any existing tunnel job
        tunnelJob?.cancel()

        tunnelJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                var retryCount = 0
                val maxRetries = if (persistentReconnect) Int.MAX_VALUE else 5

                while (retryCount <= maxRetries && isActive) {
                    try {
                        if (session != null && session!!.isConnected) {
                            session?.disconnect()
                            session = null
                        }

                        AppLogger.i("SshTunnel", "Connecting to $sshIp:$sshPort as $sshUser...")
                        _tunnelState.value = TunnelState(isRunning = true, message = "Connecting to $sshIp...")

                        val jsch = JSch()
                        session = jsch.getSession(sshUser, sshIp, sshPort)
                        session?.setPassword(sshPass)

                        val config = Properties()
                        config["StrictHostKeyChecking"] = "no"
                        session?.setConfig(config)

                        AppLogger.i("SshTunnel", "Session configured, attempting to connect...")
                        session?.connect(10000)

                        AppLogger.i("SshTunnel", "Session connected, setting port forwarding L:$localPort -> 127.0.0.1:$remotePort")
                        session?.setPortForwardingL(localPort, "127.0.0.1", remotePort)

                        AppLogger.i("SshTunnel", "Tunnel established successfully")
                        _tunnelState.value = TunnelState(isRunning = true, message = "Tunnel established: localhost:$localPort -> $sshIp:$remotePort")

                        // Keep connection alive
                        while (isActive && session?.isConnected == true) {
                            delay(5000)
                        }

                        if (isActive) {
                            retryCount++
                            if (retryCount <= maxRetries) {
                                AppLogger.w("SshTunnel", "Connection lost. Attempting reconnect ($retryCount/$maxRetries)")
                                _tunnelState.value = TunnelState(isRunning = true, message = "Reconnecting ($retryCount/$maxRetries)...")
                                val sleepTime = minOf(2000L * retryCount, 10000L)
                                delay(sleepTime)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        retryCount++
                        if (retryCount <= maxRetries && isActive) {
                            AppLogger.e("SshTunnel", "SSH Error: ${e.message}. Attempting reconnect ($retryCount/$maxRetries)")
                            _tunnelState.value = TunnelState(isRunning = true, error = "Connection failed, retrying ($retryCount/$maxRetries)...")
                            val sleepTime = minOf(2000L * retryCount, 10000L)
                            delay(sleepTime)
                        } else {
                            throw e
                        }
                    }
                }

                if (retryCount > maxRetries) {
                    AppLogger.e("SshTunnel", "SSH Error: Max retries reached")
                    _tunnelState.value = TunnelState(isRunning = false, error = "Failed to establish tunnel after $maxRetries attempts")
                    stopTunnel()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                AppLogger.e("SshTunnel", "SSH Critical Error: ${e.message}")
                _tunnelState.value = TunnelState(isRunning = false, error = e.message ?: "Failed to establish tunnel")
                stopTunnel()
            }
        }
    }

    override suspend fun stopTunnel() {
        withContext(Dispatchers.IO) {
            try {
                session?.disconnect()
                session = null
                _tunnelState.value = TunnelState(isRunning = false, message = "Tunnel stopped")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

actual fun createTunnelService(): SshTunnelService = JschTunnelService()
