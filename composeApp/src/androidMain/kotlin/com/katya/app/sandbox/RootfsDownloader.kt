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
        "aarch64" to "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-aarch64-pd-v4.29.0.tar.xz",
        "armhf" to "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-arm-pd-v4.29.0.tar.xz",
        "x86_64" to "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-x86_64-pd-v4.29.0.tar.xz",
        "x86" to "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-i686-pd-v4.29.0.tar.xz",
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
                downloadDirect(url, targetFile, onProgress)
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

    /** Скачивание по явной ссылке (например, из ComponentsRepository — «Альтернативные ссылки»). */
    suspend fun downloadDirect(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
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
        // Published proot-distro tarballs (e.g. debian-trixie-aarch64-pd-v4.29.0.tar.xz)
        // wrap the whole rootfs in one top-level directory whose exact name varies
        // (distro-arch/version). Without stripping that single leading component the
        // rootfs lands one level too deep: /bin/bash, /usr/bin/sh etc. simply don't
        // exist at the expected paths and proot reports "/bin/sh not found" for every
        // command. Detect the prefix from the first entry and drop it for the rest.
        var topPrefix: String? = null
        input.use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                val stripped = entry.name.removePrefix("./")
                if (topPrefix == null && stripped.isNotEmpty() && !entry.isDirectory) {
                    // A tar stream can start with a "." entry; only look at real entries.
                    topPrefix = ""
                }
                if (topPrefix == null) {
                    val first = stripped.substringBefore('/')
                    if (first.isNotEmpty() && first != "." && stripped != first) {
                        topPrefix = first
                        entry = tis.nextEntry
                        continue
                    }
                    topPrefix = ""
                }
                // Guard against path traversal in an untrusted rootfs tarball.
                val strippedName = if (topPrefix.isNullOrEmpty()) {
                    stripped
                } else if (stripped.startsWith("$topPrefix/")) {
                    stripped.removePrefix("$topPrefix/")
                } else {
                    // Fall back to the raw name — a parallel top-level entry.
                    stripped
                }
                val name = strippedName.split("/").filter { it.isNotEmpty() && it != ".." && it != "." }.joinToString("/")
                if (name.isEmpty()) {
                    entry = tis.nextEntry
                    continue
                }
                val outFile = File(targetDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else if (entry.isSymbolicLink) {
                    // Debian rootfs uses /bin -> usr/bin symlinks (usrmerge); without
                    // creating them, `/bin/bash` (and friends) silently vanish and proot
                    // reports "/bin/bash not found" while the file is in /usr/bin.
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists()) outFile.delete()
                        val target = java.nio.file.Paths.get(entry.linkName)
                        java.nio.file.Files.createSymbolicLink(outFile.toPath(), target)
                    } catch (e: Exception) {
                        android.util.Log.w("RootfsDownloader", "Failed to create symlink $name -> ${entry.linkName}: ${e.message}")
                    }
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
