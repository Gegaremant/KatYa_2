package com.katya.app

import com.katya.app.tools.AppLogger

/**
 * Демон на десктопе — это про сам процесс, а не про фоновую службу.
 *
 * На Android он держит wakelock и MediaSession, чтобы задачи и heartbeat
 * срабатывали при закрытом приложении. На десктопе экран не засыпает, а
 * планировщик живёт, пока открыто окно, — поднимать для этого ещё один
 * сервис было бы добавлением сложности без выигрыша.
 */
actual fun createDaemonController(): DaemonController = object : DaemonController {
    override fun start() {
        AppLogger.d("Демон", "запущен")
    }

    override fun stop() {
        AppLogger.d("Демон", "остановлен")
    }
}
