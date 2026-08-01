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
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 60000

                if (connection.responseCode !in 200..299) {
                    return@withContext "Error: HTTP ${connection.responseCode} ${connection.responseMessage}"
                }

                val tempFile = if (useRoot) {
                    File.createTempFile("download_${UUID.randomUUID()}", ".tmp")
                } else {
                    File(destinationPath).apply { parentFile?.mkdirs() }
                }

                connection.inputStream.use { input ->
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
