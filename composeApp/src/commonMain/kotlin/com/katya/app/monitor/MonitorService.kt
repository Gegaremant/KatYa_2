package com.katya.app.monitor

import kotlinx.coroutines.flow.StateFlow

data class MonitorStats(
    val srvShort: String? = null,
    val locShort: String? = null,
    val srvFull: String? = null,
    val isRunning: Boolean = false,
    val error: String? = null,
)

interface MonitorService {
    val stats: StateFlow<MonitorStats>
    suspend fun startMonitoring(
        host: String,
        port: Int,
        user: String,
        pass: String,
        isFullMode: Boolean,
    )
    fun stopMonitoring()
}

expect fun createMonitorService(): MonitorService
