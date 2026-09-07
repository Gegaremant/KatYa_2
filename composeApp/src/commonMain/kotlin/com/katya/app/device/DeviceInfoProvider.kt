package com.katya.app.device

/**
 * Data class representing the current device status.
 */
data class DeviceStatus(
    val cpuUsage: Float? = null, // 0.0 to 1.0
    val gpuUsage: Float? = null, // 0.0 to 1.0
    val temperatureCelsius: Float? = null, // in °C
    val batteryPercent: Int? = null, // 0 to 100
    val isCharging: Boolean? = null,
    val ramUsedPercent: Float? = null, // 0.0 to 1.0
)

/**
 * Provides real-time device status information.
 */
interface DeviceInfoProvider {
    /**
     * Returns the current device status. May return null if data is unavailable.
     */
    suspend fun getDeviceStatus(): DeviceStatus?
}
