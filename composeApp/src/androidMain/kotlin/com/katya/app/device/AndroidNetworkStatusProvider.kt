package com.katya.app.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.system.measureTimeMillis

class AndroidNetworkStatusProvider(
    private val context: Context,
) : NetworkStatusProvider {

    // TrafficStats baselines for real-time throughput measurement. Sampling the
    // device-wide byte counters between two consecutive calls gives the current
    // transfer rate without consuming any extra data.
    private var lastRxBytes: Long = -1
    private var lastTxBytes: Long = -1
    private var lastSampleElapsed: Long = 0L

    override suspend fun getNetworkStatus(): NetworkStatus = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)

            val isConnected = capabilities != null &&
                (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    )

            val networkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> null
            }

            // Simple ping to google.com
            val pingMs = measurePing()

            // Live throughput computed from TrafficStats deltas (no test transfer)
            val (downloadKbps, uploadKbps) = measureCurrentSpeed()

            NetworkStatus(
                pingMs = pingMs,
                downloadKbps = downloadKbps,
                uploadKbps = uploadKbps,
                isConnected = isConnected,
                networkType = networkType,
            )
        } catch (e: Exception) {
            NetworkStatus(isConnected = false)
        }
    }

    private fun measureCurrentSpeed(): Pair<Float?, Float?> {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        var download: Float? = null
        var upload: Float? = null
        if (rx >= 0 && tx >= 0 && lastRxBytes >= 0 && lastTxBytes >= 0) {
            val elapsedSec = (now - lastSampleElapsed) / 1000f
            if (elapsedSec > 0.05f) {
                val dRx = (rx - lastRxBytes).coerceAtLeast(0)
                val dTx = (tx - lastTxBytes).coerceAtLeast(0)
                download = dRx / elapsedSec / 1024f
                upload = dTx / elapsedSec / 1024f
            }
        }
        lastRxBytes = rx
        lastTxBytes = tx
        lastSampleElapsed = now
        return download to upload
    }

    private fun measurePing(): Long? = try {
        val time = measureTimeMillis {
            val address = InetAddress.getByName("8.8.8.8")
            address.isReachable(3000) // timeout 3s
        }
        if (time < 3000) time else null // if timeout, return null
    } catch (e: Exception) {
        null
    }
}
