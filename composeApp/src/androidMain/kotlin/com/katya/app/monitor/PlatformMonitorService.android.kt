package com.katya.app.monitor

import android.app.ActivityManager
import android.content.Context
import com.katya.app.tools.AppLogger
import com.katya.app.tools.SshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class AndroidMonitorService :
    MonitorService,
    KoinComponent {
    private val context: Context by inject()

    private val _stats = MutableStateFlow(MonitorStats())
    override val stats: StateFlow<MonitorStats> = _stats

    private var job: Job? = null

    override suspend fun startMonitoring(host: String, port: Int, user: String, pass: String, isFullMode: Boolean) {
        job?.cancel()
        _stats.value = MonitorStats(isRunning = true, locShort = getLocalStats())

        job = CoroutineScope(Dispatchers.IO).launch {
            val ssh = SshClient()
            while (isActive) {
                try {
                    val local = getLocalStats()

                    if (isFullMode) {
                        val fullCommand = "lspci && echo '---' && sensors && echo '---' && lsblk && echo '---' && df -h"
                        val fullOutput = ssh.executeCommand(host, port, user, pass, fullCommand)
                        _stats.value = MonitorStats(isRunning = true, locShort = local, srvFull = fullOutput)
                    } else {
                        // Short mode: top and nvidia-smi
                        val cpuRamCmd = "top -bn1 | grep -iE '^(%Cpu|KiB Mem|MiB Mem)'"
                        val gpuCmd = "nvidia-smi --query-gpu=utilization.gpu,temperature.gpu,memory.used,memory.total --format=csv,noheader,nounits"

                        val cpuRamOut = ssh.executeCommand(host, port, user, pass, cpuRamCmd)
                        val gpuOut = ssh.executeCommand(host, port, user, pass, gpuCmd)

                        val srvShort = parseShortStats(cpuRamOut, gpuOut)
                        _stats.value = MonitorStats(isRunning = true, locShort = local, srvShort = srvShort)
                    }
                } catch (e: Exception) {
                    AppLogger.e("MonitorService", "Error: ${e.message}")
                    _stats.value = _stats.value.copy(error = e.message)
                }

                delay(5000)
            }
        }
    }

    override fun stopMonitoring() {
        job?.cancel()
        job = null
        _stats.value = MonitorStats(isRunning = false)
    }

    private fun getLocalStats(): String {
        var cpuStr = "N/A"
        try {
            // Very naive CPU check, often blocked on newer Androids without root.
            val stat = File("/proc/stat").readLines().firstOrNull()
            if (stat != null) {
                val parts = stat.split("\\s+".toRegex()).drop(1)
                if (parts.size >= 4) {
                    val idle = parts[3].toLongOrNull() ?: 0L
                    val total = parts.take(7).mapNotNull { it.toLongOrNull() }.sum()
                    // Without tracking previous values, this is average since boot.
                    // For short stats, it's better than nothing, but let's just do N/A for CPU and show RAM.
                    cpuStr = "N/A"
                }
            }
        } catch (e: Exception) {}

        var ramStr = "N/A"
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024 * 1024)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            ramStr = "${availMb}M/${totalMb}M"
        } catch (e: Exception) {}

        return "Loc: RAM $ramStr"
    }

    private fun parseShortStats(cpuRam: String, gpu: String): String {
        // Very basic parsing
        // cpuRam typically:
        // %Cpu(s):  5.0 us,  2.0 sy, ...
        // MiB Mem :  16000 total,  8000 free, ...
        var cpu = "N/A"
        var ram = "N/A"

        cpuRam.lines().forEach { line ->
            if (line.contains("Cpu")) {
                // simple regex extract
                val match = Regex("([\\d.]+)\\s+us").find(line)
                if (match != null) cpu = "${match.groupValues[1]}%"
            } else if (line.contains("Mem")) {
                val match = Regex("([\\d.]+)\\s+free").find(line) ?: Regex("([\\d.]+)\\s+used").find(line)
                if (match != null) ram = "${match.groupValues[1]}M"
            }
        }

        val gpus = gpu.lines().filter { it.isNotBlank() && !it.contains("Error") }
        var gpuStr = ""
        gpus.forEachIndexed { index, g ->
            val parts = g.split(",")
            if (parts.size >= 4) {
                gpuStr += " GPU${index + 1} ${parts[0].trim()}% [T:${parts[1].trim()}C M:${parts[2].trim()}/${parts[3].trim()}M]"
            } else if (parts.size >= 2) {
                gpuStr += " GPU${index + 1} ${parts[0].trim()}% [T:${parts[1].trim()}C]"
            }
        }
        if (gpuStr.isBlank()) {
            gpuStr = " GPU N/A"
        }

        return "Srv: CPU $cpu RAM $ram$gpuStr"
    }
}

actual fun createMonitorService(): MonitorService = AndroidMonitorService()
