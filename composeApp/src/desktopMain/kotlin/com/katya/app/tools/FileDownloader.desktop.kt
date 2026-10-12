package com.katya.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Скачивание файла на десктопе.
 *
 * Редиректы и код ответа обрабатываются так же, как на Android, — формат ошибок
 * («Error: HTTP …») читается моделью, и менять его значило бы ломать её
 * сценарии. Разница одна: `useRoot` игнорируется, потому что на десктопе нет
 * второго пользователя, от имени которого писать файл.
 */
actual class FileDownloader actual constructor() {
    actual suspend fun download(url: String, destinationPath: String, useRoot: Boolean): String = withContext(Dispatchers.IO) {
        var currentUrl = url
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (true) {
            val candidate = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val status = candidate.responseCode
            val isRedirect = status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            if (isRedirect) {
                val next = candidate.getHeaderField("Location")
                candidate.disconnect()
                if (next.isNullOrBlank()) return@withContext "Error: redirect without Location"
                currentUrl = next
                if (++redirects > 5) return@withContext "Error: Too many redirects"
            } else {
                connection = candidate
                break
            }
        }

        val final = connection ?: return@withContext "Error: no connection"
        if (final.responseCode !in 200..299) {
            return@withContext "Error: HTTP ${final.responseCode} ${final.responseMessage}"
        }
        val target = File(destinationPath)
        target.parentFile?.mkdirs()
        // Во временный файл с последующим переименованием: оборванная
        // загрузка не должна оставить файл, который выглядит готовым.
        val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            // Потоки разведены по именам: `it` в двух вложенных `use` перекрывается,
            // и `copyTo` молча уходил бы не туда.
            val source = final.getInputStream()
            source.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) {
                target.writeBytes(temp.readBytes())
                temp.delete()
            }
            "Downloaded to $destinationPath"
        } catch (e: Exception) {
            temp.delete()
            "Error: ${e.message}"
        } finally {
            final.disconnect()
        }
    }
}
