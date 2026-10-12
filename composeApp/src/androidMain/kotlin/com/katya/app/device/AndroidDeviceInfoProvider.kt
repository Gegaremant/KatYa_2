package com.katya.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeviceInfoProvider(
    private val context: Context,
) : DeviceInfoProvider {

    // Feedback 05.10 #5: чтение `/proc/stat` переехало в jvmShared, потому что
    // десктопу нужно ровно то же самое. Логика нагрузки по двум замерам и её
    // объяснение — в LinuxCpuSampler.
    private val cpuSampler = LinuxCpuSampler()

    override suspend fun getDeviceStatus(): DeviceStatus? = withContext(Dispatchers.IO) {
        try {
            val cpuUsage = cpuSampler.sample()
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
