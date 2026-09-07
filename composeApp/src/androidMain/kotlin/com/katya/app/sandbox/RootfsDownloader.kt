package com.katya.app.sandbox

import com.katya.app.data.Distro
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class RootfsDownloader(private val httpClient: HttpClient) {

    private val termuxBootstrapUrls = mapOf(
        "aarch64" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-aarch64.zip",
        "armhf" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-arm.zip",
        "x86_64" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-x86_64.zip",
        "x86" to "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-i686.zip",
    )

    // Debian rootfs tarballs published by the proot-distro project (GitHub Actions
    // artifacts). `.../latest/download/...` redirects to the newest matching asset.
    // The exact asset names depend on the project's CI; keep them here so the URL
    // can be maintained without touching the extraction logic.
    private val debianRootfsUrls = mapOf(
        "aarch64" to "https://github.com/termux/proot-distro/releases/latest/download/debian-aarch64.tar.xz",
        "armhf" to "https://github.com/termux/proot-distro/releases/latest/download/debian-arm.tar.xz",
        "x86_64" to "https://github.com/termux/proot-distro/releases/latest/download/debian-x86_64.tar.xz",
        "x86" to "https://github.com/termux/proot-distro/releases/latest/download/debian-i686.tar.xz",
    )

    fun getDownloadUrls(arch: String, distro: Distro): List<String> {
        val map = when (distro) {
            Distro.TERMUX -> termuxBootstrapUrls
            Distro.DEBIAN -> debianRootfsUrls
        }
        return listOf(map[arch] ?: map["aarch64"]!!)
    }

    suspend fun download(
        arch: String,
        distro: Distro,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val urls = getDownloadUrls(arch, distro)
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
        throw IOException("Failed to download rootfs", lastError)
    }

    fun extract(rootfsFile: File, targetDir: File, distro: Distro) {
        when (distro) {
            Distro.TERMUX -> extractZip(rootfsFile, targetDir)
            Distro.DEBIAN -> extractTar(rootfsFile, targetDir)
        }
        makeWritable(targetDir)
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
                                java.nio.file.Paths.get(target),
                            )
                        } catch (e: Exception) {
                            // ignore or log
                        }
                    }
                }
            }
        }
    }

    fun extractTar(archiveFile: File, targetDir: File) {
        targetDir.mkdirs()
        val buffered = BufferedInputStream(FileInputStream(archiveFile))
        val input = when {
            archiveFile.name.endsWith(".xz") -> TarArchiveInputStream(XZCompressorInputStream(buffered))

            archiveFile.name.endsWith(".gz") || archiveFile.name.endsWith(".tgz") ->
                TarArchiveInputStream(GzipCompressorInputStream(buffered))

            archiveFile.name.endsWith(".bz2") -> TarArchiveInputStream(BZip2CompressorInputStream(buffered))

            else -> TarArchiveInputStream(buffered)
        }
        input.use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                val rawName = entry.name.removePrefix("./")
                // Guard against path traversal in an untrusted rootfs tarball.
                val name = rawName.split("/").filter { it.isNotEmpty() && it != ".." && it != "." }.joinToString("/")
                if (name.isEmpty()) {
                    entry = tis.nextEntry
                    continue
                }
                val outFile = File(targetDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> tis.copyTo(output) }
                    outFile.setExecutable(true, false)
                }
                entry = tis.nextEntry
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
