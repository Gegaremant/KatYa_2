package com.katya.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

actual suspend fun checkLocalProxyConnection(): Boolean = withContext(Dispatchers.IO) {
    try {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
        val connection = URL("https://www.google.com").openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.connect()
        val code = connection.responseCode
        connection.disconnect()
        code in 200..399
    } catch (e: Exception) {
        false
    }
}
