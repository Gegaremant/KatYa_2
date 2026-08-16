package com.katya.app.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class RootfsDownloader(private val httpClient: HttpClient) {

    private val TERMUX_BOOTSTRAP_URLS = mapOf(
        "aarch64" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-aarch64.zip",
        "armhf" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-arm.zip",
        "x86_64" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-x86_64.zip",
        "x86" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-i686.zip"
    )

    fun getDownloadUrls(arch: String): List<String> {
        return listOf(TERMUX_BOOTSTRAP_URLS[arch] ?: TERMUX_BOOTSTRAP_URLS["aarch64"]!!)
    }

    suspend fun download(
        arch: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val urls = getDownloadUrls(arch)
        var lastError: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                httpClient.prepareGet(url).execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("HTTP ${response.status.value} from $url")
                    }
                    val totalBytes = response.contentLength() ?: -1L
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L

                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { output ->
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (targetFile.exists()) targetFile.delete()
                if (index < urls.lastIndex) onProgress(0f)
            }
        }
        throw IOException("Failed to download Termux bootstrap", lastError)
    }

    fun extractZip(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        zis.copyTo(output)
                    }
                    outFile.setExecutable(true, false)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Process SYMLINKS.txt
        val symlinksFile = File(targetDir, "SYMLINKS.txt")
        if (symlinksFile.exists()) {
            symlinksFile.readLines().forEach { line ->
                val parts = line.split("←")
                if (parts.size == 2) {
                    val target = parts[0].trim()
                    val linkName = parts[1].trim()
                    
                    val linkFile = File(targetDir, linkName.removePrefix("./"))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            linkFile.parentFile?.mkdirs()
                            if (linkFile.exists()) linkFile.delete()
                            java.nio.file.Files.createSymbolicLink(
                                linkFile.toPath(),
                                java.nio.file.Paths.get(target)
                            )
                        } catch (e: Exception) {
                            // ignore or log
                        }
                    }
                }
            }
        }
    }

    fun makeWritable(rootfsDir: File) {
        rootfsDir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) {
                file.setWritable(true, true)
            }
        }
    }
}
