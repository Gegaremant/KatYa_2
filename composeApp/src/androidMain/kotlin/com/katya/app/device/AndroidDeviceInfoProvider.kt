package com.katya.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class AndroidDeviceInfoProvider(
    private val context: Context,
) : DeviceInfoProvider {

    override suspend fun getDeviceStatus(): DeviceStatus? = withContext(Dispatchers.IO) {
        try {
            val cpuUsage = getCpuUsage()
            val batteryPercent = getBatteryLevel()
            val isCharging = getIsCharging()
            val ramUsedPercent = getRamUsage()
            val temperature = getBatteryTemperature()

            DeviceStatus(
                cpuUsage = cpuUsage,
                gpuUsage = null,
                temperatureCelsius = temperature,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                ramUsedPercent = ramUsedPercent,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * CPU load over the interval between two reads.
     *
     * Feedback 05.10 #5: this returned `(total - idle) / total` from a single read of
     * `/proc/stat`. Those counters are cumulative **since boot**, so the result is the
     * average load since the phone started — a fixed number that never moves. The status
     * line showed a steady «CPU 3%» no matter what the phone was doing, and a phone
     * running a model and a proxy is not at 3%.
     *
     * Load is a rate, so it needs two samples. The first call after start-up has nothing
     * to compare against and honestly returns null instead of a made-up number; the
     * caller polls every 1.5 s, so the second sample arrives immediately.
     */
    private fun getCpuUsage(): Float? {
        val sample = readProcStatCpu() ?: return null
        val previous = lastCpuSample
        lastCpuSample = sample
        if (previous == null) return null

        val totalDelta = sample.total - previous.total
        val idleDelta = sample.idle - previous.idle
        // A reboot resets the counters; a negative delta means the sample is unusable.
        if (totalDelta <= 0L) return null
        val busy = (totalDelta - idleDelta).coerceIn(0L, totalDelta)
        return busy.toFloat() / totalDelta.toFloat()
    }

    private data class CpuSample(val total: Long, val idle: Long)

    private var lastCpuSample: CpuSample? = null

    private fun readProcStatCpu(): CpuSample? = try {
        val statFile = File("/proc/stat")
        if (!statFile.exists()) return null
        val line = RandomAccessFile(statFile, "r").use { it.readLine() }
        if (line?.startsWith("cpu ") != true) {
            null
        } else {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 5) {
                null
            } else {
                // Fields: user nice system idle iowait irq softirq steal guest guest_nice.
                // idle = idle + iowait: the CPU was not busy during iowait either.
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
                val busyPart = user + nice + system + irq + softirq + steal
                CpuSample(total = busyPart + idle + iowait, idle = idle + iowait)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun getBatteryLevel(): Int? = try {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            // Fallback for older devices via Intent
            null
        }
    } catch (e: Exception) {
        null
    }

    private fun getIsCharging(): Boolean? = try {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private fun getBatteryTemperature(): Float? = try {
        val batteryIntent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (temp != -1) temp.toFloat().div(10f) else null
    } catch (e: Exception) {
        null
    }

    private fun getRamUsage(): Float? = try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem > 0) {
            val usedRam = memoryInfo.totalMem - memoryInfo.availMem
            usedRam.toFloat() / memoryInfo.totalMem.toFloat()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
