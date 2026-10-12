package com.katya.app.device

import com.katya.app.currentPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Состояние машины для строки статуса.
 *
 * Часть цифр на десктопе получить нельзя, и они не выдумываются: батареи у
 * стационарной машины нет, температура — свойство датчика телефона. Строка
 * статуса собирает только то, что есть, и молчит об остальном — выдуманная
 * цифра хуже отсутствующей.
 */
actual fun createDeviceInfoProvider(): DeviceInfoProvider? = DesktopDeviceInfoProvider()

actual fun createNetworkStatusProvider(): NetworkStatusProvider? = DesktopNetworkStatusProvider()

private class DesktopDeviceInfoProvider : DeviceInfoProvider {

    // Тот же двухзамерный подход, что и на Android: одна выборка `/proc/stat` даёт
    // среднее с момента загрузки, а не текущую нагрузку.
    private val cpu = LinuxCpuSampler()

    override suspend fun getDeviceStatus(): DeviceStatus? = withContext(Dispatchers.IO) {
        DeviceStatus(
            cpuUsage = cpu.sample(),
            gpuUsage = null,
            temperatureCelsius = null,
            batteryPercent = batteryPercent(),
            isCharging = isCharging(),
            ramUsedPercent = ramUsedPercent(),
        )
    }

    /**
     * Загрузка памяти JVM, а не всей машины.
     *
     * `MemoryMXBean` отдаёт то, что видит JVM: куча и metaspace. Сколько физической
     * памяти занято системой целиком, JVM не знает без нативного вызова, а он на
     * десктопе различался бы между Linux и Windows. Поэтому честно показываем
     * долю heap — это и есть память приложения, ради которой строка статуса и
     * существует.
     */
    private fun ramUsedPercent(): Float? = runCatching {
        val usage = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val total = usage.max
        if (total <= 0L) return@runCatching null
        (usage.used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }.getOrNull()

    /**
     * Батарея есть только у ноутбука, и JVM о ней не знает: `getPowerInfo` в
     * стандартной библиотеке отсутствует. Считаем через sysfs, где лежит
     * `BAT0`/`AC`, и молча возвращаем null, если папки нет.
     */
    private fun batteryPercent(): Int? = readSysfsPower("capacity")?.toIntOrNull()?.takeIf { it in 0..100 }

    private fun isCharging(): Boolean? {
        val online = readSysfsPower("status") ?: return null
        return when {
            online.contains("Charging", ignoreCase = true) -> true
            online.contains("Discharging", ignoreCase = true) -> false
            else -> null
        }
    }

    private fun readSysfsPower(file: String): String? = runCatching {
        val dir = File("/sys/class/power_supply")
        val source = dir.listFiles()
            ?.firstOrNull { it.name.startsWith("BAT") && File(it, file).isFile }
            ?: return@runCatching null
        File(source, file).readText().trim()
    }.getOrNull()
}

/**
 * Скорость сети — дельта счётчиков системного интерфейса между двумя опросами.
 *
 * Как и с CPU, абсолютное значение счётчика не является скоростью: показывать
 * «прочитано 40 ГБ» в строке статуса бессмысленно. Разница между двумя
 * чтениями, приведённая к секундам, — это и есть текущая скорость.
 */
private class DesktopNetworkStatusProvider : NetworkStatusProvider {

    private val rx = NetworkCounter()
    private val tx = NetworkCounter()
    private var lastPingMs: Long? = null

    override suspend fun getNetworkStatus(): NetworkStatus? = withContext(Dispatchers.IO) {
        val down = rx.kbps()
        val up = tx.kbps()
        val ping = pingMs()
        lastPingMs = ping
        NetworkStatus(
            pingMs = ping,
            downloadKbps = down,
            uploadKbps = up,
            isConnected = runCatching {
                java.net.InetAddress.getByName("1.1.1.1").isReachable(1000)
            }.getOrDefault(false),
            networkType = null,
        )
    }

    private fun pingMs(): Long? = runCatching {
        val start = System.nanoTime()
        java.net.InetAddress.getByName("1.1.1.1").isReachable(1500)
        (System.nanoTime() - start) / 1_000_000
    }.getOrNull()

    /**
     * Счётчик байт одного интерфейса. Скорость считается из разницы двух
     * чтений; первое возвращает null, потому что интервала ещё не прошло.
     */
    private class NetworkCounter {
        private var lastBytes: Long? = null
        private var lastAtMs: Long? = null

        fun kbps(): Float? {
            val now = File("/proc/net/dev").readLines()
                .drop(2)
                .filter { !it.startsWith("lo", ignoreCase = true) }
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 10) return@mapNotNull null
                    val name = parts[0].trimEnd(':')
                    // Loopback и виртуальные интерфейсы не считаем: они удваивали
                    // бы счётчик собственного же трафика приложения.
                    if (name.startsWith("lo") || name.contains("docker") || name.startsWith("veth")) return@mapNotNull null
                    val rx = parts[1].toLongOrNull()
                    val tx = parts[9].toLongOrNull()
                    if (rx == null || tx == null) return@mapNotNull null
                    rx to tx
                }
                .fold(0L to 0L) { acc, pair -> (acc.first + pair.first) to (acc.second + pair.second) }
            val total = now.first + now.second
            val at = System.currentTimeMillis()
            val prevTotal = lastBytes
            val prevAt = lastAtMs
            lastBytes = total
            lastAtMs = at
            if (prevTotal == null || prevAt == null) return null
            val seconds = (at - prevAt) / 1000.0
            if (seconds <= 0) return null
            val delta = (total - prevTotal).coerceAtLeast(0)
            return (delta / 1024.0 / seconds).toFloat()
        }
    }
}
