package com.katya.app.testutil

import com.katya.app.monitor.MonitorService
import com.katya.app.monitor.MonitorStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMonitorService : MonitorService {
    override val stats: StateFlow<MonitorStats> = MutableStateFlow(MonitorStats())
    override suspend fun startMonitoring(host: String, port: Int, user: String, pass: String, isFullMode: Boolean) {}
    override fun stopMonitoring() {}
}
