package com.katya.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import android.util.Log

actual suspend fun checkLocalProxyConnection(): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.d("ProxyCheck", "Attempting to check local proxy connection on 127.0.0.1:10809")
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
        val connection = URL("https://www.google.com").openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        Log.d("ProxyCheck", "Connecting to https://www.google.com via proxy...")
        connection.connect()
        val code = connection.responseCode
        Log.d("ProxyCheck", "Connection successful, response code: $code")
        connection.disconnect()
        code in 200..399
    } catch (e: Exception) {
        Log.e("ProxyCheck", "Failed to connect to local proxy. Exception: ${e.message}", e)
        false
    }
}
