package com.katya.app.device

import java.io.File
import java.io.RandomAccessFile

/**
 * Загрузка CPU по интервалу между двумя чтениями `/proc/stat`.
 *
 * Живёт в jvmShared, потому что одинаково нужен Android-версии (где телефон) и
 * десктопу (где Linux-машина) — а на Windows `/proc` нет, и там оба возвращают
 * null честно.
 *
 * Счётчики в `/proc/stat` накопительные **с момента загрузки**, поэтому
 * `(total - idle) / total` из одного чтения — это средняя нагрузка за всё время
 * работы машины, то есть константа, которая не двигается. Нагрузка — это скорость
 * изменения: ей нужны два замера. Первый вызов после старта не с чем сравнить и
 * честно отдаёт null вместо выдуманного числа — опрос идёт каждые полторы
 * секунды, второй замер приходит сразу.
 *
 * Feedback 05.10 #5: раньше здесь возвращалось именно это фиктивное среднее, и
 * строка статуса показывала ровные «CPU 3%» на телефоне, который вовсе не был
 * нагружен.
 */
internal class LinuxCpuSampler {

    private var previous: CpuSample? = null

    /** 0.0–1.0 либо null, если второго замера ещё не было. */
    fun sample(): Float? {
        val current = read() ?: return null
        val last = previous
        previous = current
        if (last == null) return null

        val totalDelta = current.total - last.total
        val idleDelta = current.idle - last.idle
        // Перезагрузка обнуляет счётчики, и отрицательная дельта означает, что
        // замер непригоден.
        if (totalDelta <= 0L) return null
        val busy = (totalDelta - idleDelta).coerceIn(0L, totalDelta)
        return busy.toFloat() / totalDelta.toFloat()
    }

    private data class CpuSample(val total: Long, val idle: Long)

    private fun read(): CpuSample? = try {
        val stat = File("/proc/stat")
        if (!stat.exists()) return null
        val line = RandomAccessFile(stat, "r").use { it.readLine() }
        if (line?.startsWith("cpu ") != true) {
            null
        } else {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 5) {
                null
            } else {
                // Поля: user nice system idle iowait irq softirq steal guest guest_nice.
                // В idle входит и iowait: пока ждали диск, CPU тоже не был занят.
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
                val busy = user + nice + system + irq + softirq + steal
                CpuSample(total = busy + idle + iowait, idle = idle + iowait)
            }
        }
    } catch (_: Exception) {
        null
    }
}
