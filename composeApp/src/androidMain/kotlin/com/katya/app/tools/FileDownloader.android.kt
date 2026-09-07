package com.katya.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

actual class FileDownloader actual constructor() {
    actual suspend fun download(url: String, destinationPath: String, useRoot: Boolean): String {
        return withContext(Dispatchers.IO) {
            try {
                var currentUrl = url
                var connection: HttpURLConnection? = null
                var redirectCount = 0

                while (true) {
                    connection = URL(currentUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 60000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK && (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308)) {
                        val newUrl = connection.getHeaderField("Location")
                        currentUrl = newUrl
                        redirectCount++
                        if (redirectCount > 5) {
                            return@withContext "Error: Too many redirects"
                        }
                    } else {
                        break
                    }
                }

                val finalConnection = connection!!

                if (finalConnection.responseCode !in 200..299) {
                    return@withContext "Error: HTTP ${finalConnection.responseCode} ${finalConnection.responseMessage}"
                }

                val tempFile = if (useRoot) {
                    File.createTempFile("download_${UUID.randomUUID()}", ".tmp")
                } else {
                    File(destinationPath).apply { parentFile?.mkdirs() }
                }

                finalConnection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (useRoot) {
                    val executor = CommandExecutor()
                    if (!executor.isRootAvailable()) {
                        tempFile.delete()
                        return@withContext "Error: Root access is not available."
                    }

                    val destDir = File(destinationPath).parent
                    if (destDir != null) {
                        executor.executeCommand("mkdir -p \"$destDir\"", null, true)
                    }

                    val mvResult = executor.executeCommand("mv \"${tempFile.absolutePath}\" \"$destinationPath\"", null, true)
                    val chmodResult = executor.executeCommand("chmod 644 \"$destinationPath\"", null, true)

                    tempFile.delete() // Just in case mv failed

                    if (mvResult.startsWith("Error:") || mvResult.startsWith("Execution error:")) {
                        return@withContext "Error moving file with root: $mvResult"
                    }
                }

                "File successfully downloaded to $destinationPath (${File(destinationPath).length()} bytes)"
            } catch (e: Exception) {
                "Error downloading file: ${e.message}"
            }
        }
    }
}
