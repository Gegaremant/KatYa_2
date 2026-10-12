package com.katya.app.inference

import com.katya.app.getAppFilesDirectory
import java.io.File
import java.lang.management.ManagementFactory

actual fun getModelStorageDirectory(): String = File(getAppFilesDirectory(), "litert_models").absolutePath

actual fun getModelCacheDirectory(): String = File(getAppFilesDirectory(), "cache").absolutePath

/** Свободная куча: сколько JVM может выдать до `OutOfMemoryError`. */
actual fun getAvailableMemoryBytes(): Long = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max -
    ManagementFactory.getMemoryMXBean().heapMemoryUsage.used

actual fun getTotalMemoryBytes(): Long = Runtime.getRuntime().maxMemory()

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    val dir = File(path)
    dir.mkdirs()
    return dir.usableSpace
}

/**
 * Уведомления о скачивании модели — андроидная служба, на десктопе её нет.
 *
 * Пустые тела оставлены намеренно: интерфейс общего кода зовёт их при загрузке
 * модели, и «молча» здесь честнее, чем подделывать прогресс в виде уведомления,
 * которого пользователь не запрашивал.
 */
actual fun startDownloadNotificationService() {
    // На десктопе прогресс загрузки идёт в журнал приложения.
}

actual fun stopDownloadNotificationService() {
    // Службы нет — останавливать нечего.
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    com.katya.app.tools.AppLogger.d("Модель", "загружено $percent%")
}
