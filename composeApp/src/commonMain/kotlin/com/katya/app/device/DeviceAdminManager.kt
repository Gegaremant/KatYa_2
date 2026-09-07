package com.katya.app.device

expect object DeviceAdminManager {
    fun isDeviceAdminActive(): Boolean
    fun requestDeviceAdmin()
    fun openDeviceAdminSettings()
    fun isTrustAgentEnabled(): Boolean
    fun openTrustAgentSettings()
}
