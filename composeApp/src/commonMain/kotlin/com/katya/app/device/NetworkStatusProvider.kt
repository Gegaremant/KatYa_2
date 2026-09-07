package com.katya.app.device

/**
 * Data class representing network status.
 */
data class NetworkStatus(
    val pingMs: Long? = null, // latency in milliseconds
    val downloadKbps: Float? = null, // KB/s
    val uploadKbps: Float? = null, // KB/s
    val isConnected: Boolean = false,
    val networkType: String? = null, // e.g., "WiFi", "Cellular", "Ethernet"
)

/**
 * Provides real-time network status information.
 */
interface NetworkStatusProvider {
    /**
     * Returns the current network status. May return null if data is unavailable.
     */
    suspend fun getNetworkStatus(): NetworkStatus?
}
