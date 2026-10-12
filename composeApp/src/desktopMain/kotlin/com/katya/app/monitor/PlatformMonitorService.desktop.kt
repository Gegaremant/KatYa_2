package com.katya.app.monitor

import com.katya.app.tools.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Наблюдение за сервером по SSH — работает и на десктопе.
 *
 * Инструмент пользовался этим сервисом и на Android, где соединение шло через
 * песочницу. Здесь оно идёт напрямую: `jsch` — обычная JVM-библиотека, и оборачивать
 * её в proot незачем.
 */
actual fun createMonitorService(): MonitorService = DesktopMonitorService()

private class DesktopMonitorService : MonitorService {
    private val _stats = MutableStateFlow(MonitorStats())
    override val stats: StateFlow<MonitorStats> = _stats

    override suspend fun startMonitoring(
        host: String,
        port: Int,
        user: String,
        pass: String,
        isFullMode: Boolean,
    ) {
        _stats.value = MonitorStats(isRunning = true)
        withContext(Dispatchers.IO) {
            runCatching {
                val command = if (isFullMode) "uptime; free -h; df -h /" else "uptime"
                val out = SshClient().executeCommand(host, port, user, pass, command)
                _stats.value = MonitorStats(
                    srvShort = out.lineSequence().firstOrNull { it.isNotBlank() },
                    srvFull = out.take(4000),
                    isRunning = true,
                    error = null,
                )
            }.onFailure {
                _stats.value = MonitorStats(isRunning = false, error = it.message)
            }
        }
    }

    override fun stopMonitoring() {
        _stats.value = MonitorStats()
    }
}
