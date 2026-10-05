package com.katya.app.components

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Компоненты, зашитые прямо в сборку.
 *
 * Полевая проверка показала, что самое частое место поломки песочницы — не код, а сеть:
 * и `github.com`, и `release-assets.githubusercontent.com` отдавали `Socket timeout`, и
 * установка компонентов не доезжала до конца. Скачивание с зеркала спасает не всегда —
 * если до сети нет вообще, помогает только то, что уже внутри APK.
 *
 * Поэтому дистрибутивов два:
 * - `full` — несёт rootfs и proot внутри (`assets/katya-components/`), ставит их без сети;
 * - `lite` — не несёт ничего и качает всё по требованию.
 *
 * Файл называется ровно как идентификатор компонента плюс расширение архива:
 * `debian_arm64-v8a.tar.xz`, `native_arm64-v8a.zip`. Логика установки та же самая —
 * просто источник байтов другой, поэтому код распаковки не знает разницы.
 */
object BundledComponents {

    private const val ASSET_DIR = "katya-components"
    private const val TAG = "BundledComponents"

    /** Расширение архива для типа компонента — то же, что приходит по сети. */
    fun archiveSuffix(type: ComponentType): String = when (type) {
        ComponentType.ROOTFS -> ".tar.xz"
        else -> ".zip"
    }

    /** Имя файла, которым компонент лежит в assets. */
    fun assetName(id: String, type: ComponentType): String = id + archiveSuffix(type)

    /** Есть ли этот компонент внутри сборки. */
    fun has(context: Context, id: String, type: ComponentType): Boolean = try {
        context.assets.open("$ASSET_DIR/${assetName(id, type)}").close()
        true
    } catch (_: Exception) {
        false
    }

    /** Размер встроенного архива — чтобы полоса прогресса знала, к чему идти. */
    fun size(context: Context, id: String, type: ComponentType): Long = try {
        context.assets.openFd("$ASSET_DIR/${assetName(id, type)}").use { it.length }
    } catch (_: Exception) {
        // Сжатый поток не всегда отдаёт размер через openFd — тогда считаем, разбиваясь.
        var counted = 0L
        context.assets.open("$ASSET_DIR/${assetName(id, type)}").use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                counted += n
            }
        }
        counted
    }

    /**
     * Копирует встроенный архив в [target]. Возвращает размер — столько же, сколько весит
     * скачанный файл, чтобы прогресс и отчёт одинаково читались в обоих путях.
     *
     * Ресурс в APK лежит сжатым, поэтому поток идёт через буфер, а не через `File`.
     * Распаковка атомарная: [target] появляется только целиком — оборванная установка
     * хуже, чем её отсутствие, потому что следом распаковщик получает битый архив.
     *
     * [onProgress] получает уже скопированные байты: полоса в уведомлении должна ползти и
     * здесь, а не стоять на нуле все десять секунд распаковки 33 МБ.
     */
    fun copyOut(
        context: Context,
        id: String,
        type: ComponentType,
        target: File,
        onProgress: (Long) -> Unit = {},
    ): Long {
        target.parentFile?.mkdirs()
        val staging = File(target.absolutePath + ".unpack")
        staging.delete()
        var copied = 0L
        context.assets.open("$ASSET_DIR/${assetName(id, type)}").use { input ->
            staging.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                var lastNotify = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    copied += n
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 250) {
                        lastNotify = now
                        onProgress(copied)
                    }
                }
                out.flush()
            }
        }
        onProgress(copied)
        if (target.exists() && !target.delete()) {
            staging.delete()
            error("Не удалось заменить $target")
        }
        if (!staging.renameTo(target)) {
            staging.delete()
            error("Не удалось переместить встроенный архив на место $target")
        }
        Log.i(TAG, "bundled $id: $copied bytes")
        return copied
    }

    /**
     * Зеркала для компонента: сперва адрес из настроек, затем известные запасные.
     *
     * Запасные адреса — не секрет и не персональные данные: это публичные релизы
     * proot-distro и самого проекта. Пользователь может подменить основной адрес
     * сам, но когда до GitHub не доходит вообще, список в коде — это всё, что остаётся.
     */
    fun mirrorCandidates(id: String, primary: String): List<String> {
        if (id.startsWith("debian_")) {
            val abi = id.removePrefix("debian_")
            val arch = when (abi) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x86_64"
                else -> "aarch64"
            }
            val file = "debian-trixie-$arch-pd-v4.29.0.tar.xz"
            return listOfNotNull(
                primary,
                "https://ghproxy.net/https://github.com/termux/proot-distro/releases/download/v4.29.0/$file",
                "https://github.moeyy.xyz/https://github.com/termux/proot-distro/releases/download/v4.29.0/$file",
            )
        }
        if (id.startsWith("native_")) {
            val abi = id.removePrefix("native_")
            val file = "native-$abi.zip"
            return listOfNotNull(
                primary,
                "https://ghproxy.net/https://github.com/Gegaremant/KatYa_2/releases/download/v3.1.3/$file",
                "https://github.moeyy.xyz/https://github.com/Gegaremant/KatYa_2/releases/download/v3.1.3/$file",
            )
        }
        return listOf(primary)
    }
}
