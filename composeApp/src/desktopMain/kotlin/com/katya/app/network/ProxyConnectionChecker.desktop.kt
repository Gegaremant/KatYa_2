package com.katya.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверка локального прокси на десктопе — обычное TCP-соединение.
 *
 * На Android проверка шла отдельной командой с правами root, потому что процесс
 * приложения не видел сокеты, поднятые другим процессом. Здесь всё в одном
 * процессе, поэтому достаточно подключиться к порту.
 */
actual suspend fun checkLocalProxyConnection(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 10808), 1500)
            true
        }
    }.getOrDefault(false)
}
