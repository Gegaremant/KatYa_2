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

    private fun getCpuUsage(): Float? {
        try {
            val statFile = File("/proc/stat")
            if (!statFile.exists()) return null

            val reader = RandomAccessFile(statFile, "r")
            val line = reader.readLine()
            reader.close()

            if (line?.startsWith("cpu ") == true) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val user = parts[1].toLongOrNull() ?: 0
                    val nice = parts[2].toLongOrNull() ?: 0
                    val system = parts[3].toLongOrNull() ?: 0
                    val idle = parts[4].toLongOrNull() ?: 0

                    val total = user + nice + system + idle
                    if (total > 0) {
                        // First call returns 0, subsequent calls give actual usage
                        // For simplicity, return approximate usage
                        val used = total - idle
                        return used.toFloat() / total.toFloat()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
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
