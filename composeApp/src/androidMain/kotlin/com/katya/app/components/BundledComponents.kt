package com.katya.app.components

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
     * Публичная ссылка на папку Яндекс.Диска с компонентами.
     *
     * Пустая строка — звёздочка не вставлена. Сама ссылка не секрет: это общий доступ
     * на чтение. Но и вшивать её задолго до того, как папка существует, незачем —
     * поэтому код умеет работать и без неё, просто не получает лишнего адреса.
     *
     * Смысл в том, что GitHub у части людей не открывается вообще, а это не зеркало
     * GitHub, а отдельный хостинг: ссылки там живые годами и не зависят от того,
     * заблокирован ли github.com.
     */
    private const val YANDEX_DISK_PUBLIC_KEY = ""

    /**
     * Источники, с которых можно забрать архив компонента, в порядке предпочтения.
     *
     * Запасные адреса — не секрет и не персональные данные: это публичные релизы
     * proot-distro и самого проекта. Пользователь может подменить основной адрес
     * сам, но когда до GitHub не доходит вообще, список в коде — это всё, что остаётся.
     */
    fun mirrorCandidates(id: String, primary: String): List<MirrorSource> {
        val sources = mutableListOf<MirrorSource>()
        if (primary.isNotBlank()) sources += MirrorSource.Direct(primary)
        if (id.startsWith("debian_")) {
            val abi = id.removePrefix("debian_")
            val arch = when (abi) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x86_64"
                else -> "aarch64"
            }
            val file = "debian-trixie-$arch-pd-v4.29.0.tar.xz"
            sources += MirrorSource.Direct(
                "https://ghproxy.net/https://github.com/termux/proot-distro/releases/download/v4.29.0/$file",
            )
            sources += MirrorSource.Direct(
                "https://github.moeyy.xyz/https://github.com/termux/proot-distro/releases/download/v4.29.0/$file",
            )
            sources += yandexSource("debian_$abi.tar.xz")
        }
        if (id.startsWith("native_")) {
            val abi = id.removePrefix("native_")
            val file = "native-$abi.zip"
            sources += MirrorSource.Direct(
                "https://ghproxy.net/https://github.com/Gegaremant/KatYa_2/releases/download/v3.1.3/$file",
            )
            sources += MirrorSource.Direct(
                "https://github.moeyy.xyz/https://github.com/Gegaremant/KatYa_2/releases/download/v3.1.3/$file",
            )
            sources += yandexSource("native_$abi.zip")
        }
        return sources
    }

    private fun yandexSource(fileName: String): List<MirrorSource> = if (YANDEX_DISK_PUBLIC_KEY.isBlank()) {
        emptyList()
    } else {
        listOf(MirrorSource.YandexDisk(YANDEX_DISK_PUBLIC_KEY, fileName))
    }
}

/**
 * Откуда ещё можно взять архив компонента.
 *
 * Почему не просто список строк: у Яндекс.Диска нельзя скачать по публичной ссылке
 * напрямую. Его API выдаёт временный прямой адрес, который живёт около часа, — значит
 * резолвить надо в момент установки, иначе к следующей попытке ссылка уже протухла.
 * Поэтому источник умеет сам превратиться в адрес, а не просто им быть.
 */
sealed interface MirrorSource {
    /** Короткое имя источника для журнала — полный адрес там не нужен и только шумит. */
    val label: String

    /** Прямой адрес, готовый к загрузке прямо сейчас. */
    suspend fun resolve(http: HttpClient): String

    /** Обычный адрес — берём как есть. */
    data class Direct(val url: String) : MirrorSource {
        override val label: String get() = url.take(72)
        override suspend fun resolve(http: HttpClient): String = url
    }

    /** Файл из публично открытой папки Яндекс.Диска. */
    data class YandexDisk(val publicKey: String, val fileName: String) : MirrorSource {
        override val label: String get() = "Яндекс.Диск/$fileName"

        override suspend fun resolve(http: HttpClient): String {
            val body =
                http.get("https://cloud-api.yandex.net/v1/disk/public/resources/download") {
                    parameter("public_key", publicKey)
                    parameter("path", fileName)
                }.bodyAsText()
            return runCatching {
                Json.parseToJsonElement(body).jsonObject["href"]?.jsonPrimitive?.content
            }.getOrNull()
                ?: error("Яндекс.Диск не отдал ссылку на $fileName")
        }
    }
}
