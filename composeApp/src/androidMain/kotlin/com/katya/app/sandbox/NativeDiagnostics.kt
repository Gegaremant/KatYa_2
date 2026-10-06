package com.katya.app.sandbox

import android.os.Build
import android.system.Os
import com.katya.app.tools.AppLogger
import java.io.File

/**
 * Диагностика нативной части песочницы — только для журнала.
 *
 * Зачем: полевая проверка принесла «libproot.so падает с Permission denied
 * (error=13) на уровне запуска». Этого описания недостаточно, потому что `error=13`
 * означает сразу четыре разных вещи:
 *
 * 1. на файле нет бита исполнения (или сломан владелец);
 * 2. SELinux запретил запуск из этого каталога для этого домена;
 * 3. каталог смонтирован с `noexec`;
 * 4. ELF-интерпретатор бинарника недоступен (у proot это `/system/bin/linker64`).
 *
 * Лог писал одну строку «proot не отвечает (код -1): …» — по ней эти четыре случая
 * неразличимы. Здесь они разводятся по отдельным строкам, плюс argv запуска, чтобы
 * команду можно было повторить руками в Termux.
 *
 * `android.os.SELinux` — класс `@hide`, его нет в публичном SDK, поэтому контекст
 * читается рефлексией. Для диагностики это честнее, чем не писать его вовсе: без него
 * гипотеза «виноват SELinux» останется гипотезой.
 */
object NativeDiagnostics {

    private const val TAG = "LinuxSandbox"

    /** Пишет полную картину по нативной части. Вызывается при неудачном запуске. */
    fun dump(context: android.content.Context, reason: String) {
        AppLogger.action(TAG, "диагностика нативной части ($reason)")
        val abi = com.katya.app.components.currentAbi()
        val dir = File(context.filesDir, "katya-native/$abi")
        AppLogger.d(TAG, "ABI: $abi, каталог: $dir")

        if (!dir.isDirectory) {
            AppLogger.e(TAG, "каталога нативных библиотек нет — песочница не запустится")
            return
        }
        AppLogger.d(TAG, "домен SELinux: ${procAttr("current")}")
        AppLogger.d(TAG, "контекст каталога: ${fileContext(dir)}")

        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) AppLogger.e(TAG, "каталог пуст — ни одного нативного файла")
        files.forEach { f ->
            AppLogger.d(
                TAG,
                "файл ${f.name}: ${f.length()} байт, canExecute=${f.canExecute()}, ${statMode(f)}",
            )
        }

        val proot = File(dir, "libproot.so")
        if (proot.exists()) {
            AppLogger.d(TAG, "контекст libproot.so: ${fileContext(proot)}")
            AppLogger.d(TAG, "ELF-интерпретатор /system/bin/linker64 доступен: ${File("/system/bin/linker64").canRead()}")
        }

        // noexec на пути к файлу — причина №3, и её видно только тут.
        AppLogger.d(TAG, "сегмент монтирования: ${mountSegmentFor(dir.absolutePath) ?: "не нашлём"}")
    }

    /** Строчный вывод того, что реально видит ядро: биты прав и владелец. */
    private fun statMode(file: File): String = runCatching {
        val st = Os.lstat(file.absolutePath)
        "uid=${st.st_uid} gid=${st.st_gid} mode=${Integer.toOctalString(st.st_mode)}"
    }.getOrElse { "stat не дался: ${it.message}" }

    /** Контекст SELinux файла. `android.os.SELinux` скрыт, поэтому рефлексией. */
    private fun fileContext(file: File): String = runCatching {
        val cls = Class.forName("android.os.SELinux")
        val m = cls.getMethod("getFileContext", String::class.java)
        m.invoke(null, file.absolutePath) as? String ?: "не прочитан"
    }.getOrElse { "не прочитан: ${it.message}" }

    /** Текущий SELinux-домен из procfs — без рефлексии и без скрытых API. */
    private fun procAttr(name: String): String = runCatching {
        File("/proc/self/attr/$name").readText().trim()
    }.getOrElse { "не прочитан: ${it.message}" }

    /**
     * Ищет в `/proc/self/mountinfo` сегмент, в который попадает путь, и возвращает его
     * флаги. Именно там видно `noexec`, которого нет ни в правах файла, ни в SELinux.
     */
    private fun mountSegmentFor(path: String): String? = runCatching {
        val parsed = mutableListOf<Pair<String, String>>()
        for (line in runCatching { File("/proc/self/mountinfo").readLines() }.getOrDefault(emptyList())) {
            val parts = line.split(" ")
            // формат: id parent major:minor root mountpoint options…
            if (parts.size >= 6) parsed += parts[4] to parts[5]
        }
        parsed.filter { (mountPoint, _) -> path.startsWith(mountPoint) }
            .maxByOrNull { (mountPoint, _) -> mountPoint.length }
            ?.let { (mountPoint, options) -> "$mountPoint [$options]" }
    }.getOrNull()

    /** Тот самый argv, которым запускается proot, — чтобы его можно было повторить руками. */
    fun logLaunch(argv: Array<String>, env: Array<String>, cwd: File?) {
        AppLogger.d(TAG, "запуск: ${argv.joinToString(" ")}")
        AppLogger.d(TAG, "cwd: ${cwd?.absolutePath}")
        AppLogger.d(
            TAG,
            "окружение: " +
                env.filter { it.startsWith("PROOT_") || it.startsWith("LD_") || it.startsWith("PATH") }
                    .joinToString(" ") { it.substringBefore('=') + "=…".take(1) + it.substringAfter('=').take(40) },
        )
    }

    /** Расхождение между ABI устройства и тем, что реально установлено. */
    fun logAbiMismatch(context: android.content.Context) {
        val supported = Build.SUPPORTED_ABIS.joinToString(",")
        val current = com.katya.app.components.currentAbi()
        val dir = File(context.filesDir, "katya-native/$current")
        if (dir.isDirectory) {
            AppLogger.d(TAG, "ABI устройства: $supported (выбрана $current) — библиотеки на месте")
        } else {
            AppLogger.e(TAG, "ABI устройства: $supported (выбрана $current), но нативных библиотек для неё нет")
        }
    }
}
