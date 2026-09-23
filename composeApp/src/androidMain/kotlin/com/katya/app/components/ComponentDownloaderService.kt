package com.katya.app.components

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.katya.app.data.Distro
import com.katya.app.db.DownloadableComponent
import com.katya.app.sandbox.LinuxSandboxManager
import com.katya.app.sandbox.RootfsDownloader
import com.katya.app.tools.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipInputStream

/**
 * Foreground-сервис, который качает недостающие компоненты в фоне и показывает
 * ход в системном уведомлении (полоса прогресса). Очередь обрабатывается
 * последовательно; прогресс пишется в БД (components.sq) и сразу виден
 * на вкладке «Сервер» → «Альтернативные ссылки».
 */
class ComponentDownloaderService : Service() {

    companion object {
        const val ACTION_DOWNLOAD = "com.katya.app.components.DOWNLOAD"
        const val EXTRA_COMPONENT_ID = "component_id"
        private const val CHANNEL_ID = "kai_component_download_channel"
        private const val NOTIFICATION_ID = 9101
    }

    private val repository: ComponentsRepository by inject()
    private val linuxSandboxManager: LinuxSandboxManager by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<String>()
    private var running = false
    private var activeId: String? = null

    private val http: HttpClient by lazy { HttpClient(OkHttp) }
    private val downloader: RootfsDownloader by lazy { RootfsDownloader(http) }
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        AppLogger.action("Фоновая загрузка компонентов", "сервис запущен")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_COMPONENT_ID)
                if (id.isNullOrBlank()) return START_STICKY
                if (activeId != id && queue.none { it == id }) queue.add(id)
                if (!running) processQueue()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        AppLogger.action("Фоновая загрузка компонентов", "сервис остановлен")
        super.onDestroy()
    }

    private fun processQueue() {
        running = true
        startForeground(NOTIFICATION_ID, buildNotification(null, 0, 0L, -1L))
        scope.launch {
            try {
                while (queue.isNotEmpty()) {
                    val id = queue.poll() ?: break
                    activeId = id
                    try {
                        downloadComponent(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        runCatching { repository.markFailed(id) }
                        AppLogger.e("ComponentDownloader", "Сбой установки «$id»: ${e.message}")
                        AppLogger.action("Установка компонента «$id»", "ОШИБКА: ${e.message}")
                    } finally {
                        activeId = null
                    }
                }
            } finally {
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun downloadComponent(id: String) {
        val component = repository.component(id) ?: return
        val tempFile = File(filesDir, "components/tmp/$id.part")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        repository.updateProgress(id, "downloading", 0L, 0L)
        AppLogger.action("Скачиваю «${component.name}»", "начато")

        val totalBytes = try {
            http.prepareGet(component.url).execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP ${response.status.value} от ${component.url}")
                }
                val total = response.contentLength() ?: -1L
                repository.updateProgress(id, "downloading", 0L, total.coerceAtLeast(0L))
                val channel = response.bodyAsChannel()
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastNotify = 0L
                FileOutputStream(tempFile).use { out ->
                    while (!channel.isClosedForRead) {
                        val n = channel.readAvailable(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 250) {
                            lastNotify = now
                            repository.updateProgress(id, "downloading", downloaded, total)
                            notifyProgress(component, downloaded, total)
                        }
                    }
                }
                downloaded
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }

        AppLogger.action("Скачивание «${component.name}»", "OK (${formatBytes(totalBytes)})")
        installComponent(component, tempFile)
        tempFile.delete()
        repository.markInstalled(id)
        AppLogger.action("Установка компонента «${component.name}»", "OK — установлен")
    }

    private fun installComponent(component: DownloadableComponent, archive: File) {
        when (ComponentType.from(component.componentType)) {
            ComponentType.ROOTFS -> {
                val rootfsDir = File(filesDir, "linux-sandbox/rootfs")
                rootfsDir.deleteRecursively()
                rootfsDir.mkdirs()
                AppLogger.action("Распаковка rootfs «${component.name}»", "начата")
                // Debian tarball: strips the top-level dir, creates /bin -> usr/bin links.
                downloader.extract(archive, rootfsDir, Distro.DEBIAN)
                linuxSandboxManager.recheckInstallation()
                AppLogger.action("Распаковка rootfs «${component.name}»", "OK")
            }

            ComponentType.NATIVE -> {
                val abi = component.abi ?: currentAbi()
                val dest = File(filesDir, "katya-native/$abi")
                dest.mkdirs()
                AppLogger.action("Установка нативных библиотек «${component.name}»", "начата")
                extractZipFlat(archive, dest)
                setExecutableRecursive(dest)
                AppLogger.action("Установка нативных библиотек «${component.name}»", "OK")
            }

            ComponentType.MODEL -> {
                throw IOException("Модели пока не поддерживаются ComponentDownloaderService")
            }
        }
    }

    private fun extractZipFlat(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (name.isNotEmpty()) {
                    File(destDir, name).apply {
                        if (entry.isDirectory) {
                            mkdirs()
                        } else {
                            parentFile?.mkdirs()
                            FileOutputStream(this).use { out -> zis.copyTo(out) }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun setExecutableRecursive(dir: File) {
        dir.walkTopDown().forEach { f ->
            if (f.isFile && !f.canExecute()) f.setExecutable(true, false)
        }
    }

    private fun notifyProgress(component: DownloadableComponent, downloaded: Long, total: Long) {
        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(component.name, pct, downloaded, total),
        )
    }

    private fun buildNotification(name: String?, pct: Int, downloaded: Long, total: Long): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return builder
            .setContentTitle("KatYa — загрузка компонента")
            .setContentText(name?.let { "$it · $pct%" } ?: "Подготовка…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, total <= 0)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Загрузка компонентов",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Прогресс скачивания Debian, Proot, Xray и моделей" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "%.1f КБ".format(kb)
        val mb = kb / 1024.0
        return "%.1f МБ".format(mb)
    }
}