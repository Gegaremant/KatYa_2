@file:OptIn(ExperimentalComposeUiApi::class)

package com.katya.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.katya.app.data.AppSettings
import com.katya.app.data.MemoryStore
import com.katya.app.data.TaskStore
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.tools.CommonTools
import com.katya.app.tools.DesktopCommandTool
import com.katya.app.tools.HeartbeatTools
import com.katya.app.tools.SchedulingTools
import com.katya.app.tools.SftpTool
import com.katya.app.tools.SshConfigureHostTool
import com.katya.app.tools.SshTool
import com.katya.app.tools.WebSearchTool
import com.katya.app.tools.desktopToolInfo
import com.katya.app.tools.manageProcessTool
import com.katya.app.tools.manageProcessToolInfo
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? {
    val data = event.dragData()
    if (data !is DragData.FilesList) return null
    val path = data.readFiles().firstOrNull() ?: return null
    return runCatching { File(URI(path)).takeIf { it.exists() }?.let { PlatformFile(it) } }.getOrNull()
}

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = run {
    val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    when {
        "win" in os -> Platform.Desktop.Windows
        "mac" in os || "darwin" in os -> Platform.Desktop.Mac
        else -> Platform.Desktop.Linux
    }
}

actual val defaultUiScale: Float = run {
    // На Linux/Wayland JVM часто не подхватывает GDK_SCALE, и окно рисуется в 1×
    // на hi-res мониторе. Пользователь масштабирует ползунком в настройках, но
    // стартовое значение берём из переменных окружения — иначе первый взгляд на
    // приложение выглядит сломанным.
    if (currentPlatform !is Platform.Desktop.Linux) return@run 1.0f
    val gdkScale = System.getenv("GDK_SCALE")?.toFloatOrNull() ?: 1f
    val gdkDpi = System.getenv("GDK_DPI_SCALE")?.toFloatOrNull() ?: 1f
    (gdkScale * gdkDpi).coerceIn(0.5f, 4f)
}

actual fun getAppFilesDirectory(): String {
    // `~/.katya` вместо Android-овского filesDir: на десктопе нет «внутреннего
    // хранилища приложения», и пользователю нужно место, куда он может заглянуть
    // сам — с конфигом, базой и логами.
    val dir = File(System.getProperty("user.home"), ".katya")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun createSecureSettings(): Settings = DesktopEncryptedFileSettings(
    File(getAppFilesDirectory(), "secure-prefs"),
)

actual fun createLegacySettings(): Settings? = null

actual fun getAvailableTools(): List<Tool> {
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    return buildList {
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
            addAll(HeartbeatTools.getHeartbeatTools(memoryStore, appSettings))
        }
        if (appSettings.isToolEnabled(CommonTools.localTimeTool.schema.name)) add(CommonTools.localTimeTool)
        if (appSettings.isToolEnabled(CommonTools.ipLocationTool.schema.name)) add(CommonTools.ipLocationTool)
        if (appSettings.isToolEnabled(WebSearchTool.schema.name)) add(WebSearchTool)
        // На десктопе команда идёт прямо в оболочку ОС — proot здесь нет и не нужен.
        if (appSettings.isToolEnabled(DesktopCommandTool.schema.name, defaultEnabled = true)) {
            add(DesktopCommandTool)
            add(manageProcessTool)
        }
        if (appSettings.isToolEnabled(SshTool().schema.name)) {
            add(SshTool())
            add(SshConfigureHostTool)
            add(SftpTool())
        }
    }
}

actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    addAll(CommonTools.commonToolDefinitions)
    add(desktopToolInfo)
    add(manageProcessToolInfo)
    add(SshConfigureHostTool.toolInfo)
}

actual val isEmailSupported: Boolean = true

actual val isSmsSupported: Boolean = false

actual val isNotificationsSupported: Boolean = false

actual val isSplinterlandsSupported: Boolean = true

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return runCatching {
        val source = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: return bytes
        val maxDim = 1024
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(source.width, source.height))
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.drawImage(source, 0, 0, w, h, null)
        g.dispose()
        java.io.ByteArrayOutputStream().use { out ->
            javax.imageio.ImageIO.write(scaled, "jpg", out)
            out.toByteArray()
        }
    }.getOrDefault(bytes)
}

actual fun openUrl(url: String): Boolean = runCatching {
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)

actual fun openTtsSettings() {
    // На десктопе системный синтезатор настраивается в настройках ОС, а не в
    // приложении: отдельного экрана для выбора движка тут нет.
}

actual fun isAppInstalled(packageName: String): Boolean = false

actual fun openAssistantSettings() {
    // Голосовые ассистенты — понятие Android; на десктопе аналога нет.
}

actual fun openAccessibilitySettings() {
    // Специальных доступов Android на десктопе нет.
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Системного «назад» на десктопе нет; Alt+Left обрабатывает окно.
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String): Boolean = runCatching {
    FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
        ?.write(bytes) != null
}.getOrDefault(false)

actual suspend fun saveLargeFileToDevice(srcFilePath: String, baseName: String, extension: String): Boolean = runCatching {
    val src = File(srcFilePath)
    if (!src.isFile) return@runCatching false
    // Файл не читается в память целиком: локальные модели весят гигабайты, и
    // ByteArray на несколько гигов уронил бы JVM.
    val destination = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
        ?: return@runCatching false
    // Через write(PlatformFile): у файла filekit нет outputStream, и открывать
    // `Path` вручную значило бы обходить его файловую систему.
    destination.write(PlatformFile(src))
    true
}.getOrDefault(false)

actual fun showToast(message: String) {
    // Отдельного тоста на десктопе нет; сообщение уже видно в логе приложения.
    com.katya.app.tools.AppLogger.d("Toast", message)
}

/**
 * Уведомление ОС. У каждой системы своя поверхность, и все три пути глотают
 * ошибки: если `notify-send` не установлен или AWT headless, внутри приложения
 * всё равно есть баннер напоминания.
 */
actual fun sendHeartbeatNotification(title: String, body: String) {
    runCatching {
        when (currentPlatform) {
            is Platform.Desktop.Linux -> {
                // `--` не даёт заголовку, начинающемуся с `-`, стать флагом.
                ProcessBuilder("notify-send", "--", title, body).start()
            }

            is Platform.Desktop.Mac -> {
                val t = title.replace("\\", "\\\\").replace("\"", "\\\"")
                val b = body.replace("\\", "\\\\").replace("\"", "\\\"")
                ProcessBuilder("osascript", "-e", "display notification \"$b\" with title \"$t\"").start()
            }

            is Platform.Desktop.Windows -> {
                if (!java.awt.SystemTray.isSupported()) return
                val tray = java.awt.SystemTray.getSystemTray()
                val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val icon = java.awt.TrayIcon(image, "KatYa")
                icon.isImageAutoSize = true
                tray.add(icon)
                icon.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
                // Иконку снимаем: постоянный трей пользователю не нужен, а на
                // Windows он остался бы висеть до конца сессии.
                java.util.Timer(true).schedule(
                    object : java.util.TimerTask() {
                        override fun run() = tray.remove(icon)
                    },
                    5_000,
                )
            }

            else -> Unit
        }
    }
}

actual suspend fun generateBackupZip(jsonConfig: String, includeDatabase: Boolean, includeModels: Boolean): ByteArray = DesktopBackups.generate(jsonConfig, includeDatabase, includeModels)

actual fun scheduleHeartbeatAlarm() {
    // Планировщик крутится в процессе, пока приложение открыто. На десктопе
    // «приложение закрыто, а задача всё равно сработала» не воспроизвести без
    // системного сервиса — и врать пользователю об этом нельзя, поэтому звонок
    // остаётся пустым, а карточка задачи на десктопе помечена как «пока открыто».
}

actual suspend fun readBackupZip(zipBytes: ByteArray): BackupPayload? = DesktopBackups.read(zipBytes)

actual suspend fun applyBackupPayload(payload: BackupPayload) = DesktopBackups.apply(payload)

actual fun createLocalNote(title: String, content: String): String = runCatching {
    val file = File(getAppFilesDirectory(), "notes/${title.ifBlank { "note" }}.md")
    file.parentFile?.mkdirs()
    file.writeText("# $title\n\n$content")
    "Заметка сохранена: ${file.absolutePath}"
}.getOrElse { "Error: ${it.message}" }

actual fun getDirectoryPath(directory: Any?): String? = directory?.toString()?.takeIf { it.isNotBlank() }

actual suspend fun writeSkillFile(skillId: String, fileName: String, content: String): Boolean = runCatching {
    val dir = File(getAppFilesDirectory(), "skills/$skillId")
    dir.mkdirs()
    File(dir, fileName).apply { parentFile?.mkdirs() }.writeText(content)
    true
}.getOrDefault(false)

actual suspend fun deleteSkillDir(skillId: String): Boolean = runCatching {
    File(getAppFilesDirectory(), "skills/$skillId").deleteRecursively()
}.getOrDefault(false)

actual suspend fun readSandboxSkillFiles(): Map<String, String> = runCatching {
    val root = File(getAppFilesDirectory(), "skills")
    if (!root.isDirectory) {
        emptyMap()
    } else {
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
            File(dir, "SKILL.md").takeIf { it.isFile }?.let { dir.name to it.readText() }
        }.toMap()
    }
}.getOrDefault(emptyMap())
