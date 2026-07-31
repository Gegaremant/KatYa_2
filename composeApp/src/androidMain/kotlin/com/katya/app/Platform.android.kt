package com.katya.app

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.katya.app.data.AppSettings
import com.katya.app.data.EmailStore
import com.katya.app.data.MemoryStore
import com.katya.app.data.NotificationStore
import com.katya.app.data.SmsDraftStore
import com.katya.app.data.SmsStore
import com.katya.app.data.TaskStore
import com.katya.app.mcp.McpServerManager
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import com.katya.app.notifications.NotificationReader
import com.katya.app.notifications.declaresNotificationListener
import com.katya.app.sandbox.LinuxSandboxManager
import com.katya.app.sandbox.SandboxState
import com.katya.app.sms.SmsReader
import com.katya.app.sms.SmsSender
import com.katya.app.sms.declaresReadSms
import com.katya.app.tools.AndroidHostShellTool
import com.katya.app.tools.BluetoothTool
import com.katya.app.tools.CalendarPermissionController
import com.katya.app.tools.CalendarRepository
import com.katya.app.tools.CalendarResult
import com.katya.app.tools.CommonTools
import com.katya.app.tools.EmailTools
import com.katya.app.tools.FetchUrlTool
import com.katya.app.tools.HeartbeatTools
import com.katya.app.tools.InfraredTool
import com.katya.app.tools.NotificationHelper
import com.katya.app.tools.NotificationPermissionController
import com.katya.app.tools.NotificationResult
import com.katya.app.tools.NotificationTools
import com.katya.app.tools.OpenFileTool
import com.katya.app.tools.ProcessManagerTool
import com.katya.app.tools.RootCommandTool
import com.katya.app.tools.NotesTool
import com.katya.app.data.NotesStore
import com.katya.app.tools.SchedulingTools
import com.katya.app.tools.ShellCommandTool
import com.katya.app.tools.SmsTools
import com.katya.app.tools.SshConfigureHostTool
import com.katya.app.tools.WebSearchTool
import com.russhwolf.settings.BuildConfig
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.spght.encryptedprefs.EncryptedSharedPreferences
import dev.spght.encryptedprefs.MasterKey
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.tool_create_calendar_event_description
import katya.composeapp.generated.resources.tool_create_calendar_event_name
import katya.composeapp.generated.resources.tool_open_file_description
import katya.composeapp.generated.resources.tool_open_file_name
import katya.composeapp.generated.resources.tool_send_notification_description
import katya.composeapp.generated.resources.tool_send_notification_name
import katya.composeapp.generated.resources.tool_set_alarm_description
import katya.composeapp.generated.resources.tool_set_alarm_name
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = Platform.Mobile.Android

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = true

// Evaluated lazily because we need the Koin-injected Context. Whether READ_SMS
// is declared in the merged manifest is a build-time property (foss flavor adds
// it, playStore does not), so caching the first result is safe for the process
// lifetime. The try/catch guards screenshot / unit-test environments that may
// call `getPlatformToolDefinitions()` before Koin has been started.
actual val isSmsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresReadSms()
    } catch (_: Throwable) {
        false
    }
}

// Same lazy pattern as `isSmsSupported`: probe the merged manifest for the listener
// service. Foss flavor declares it, playStore does not.
actual val isNotificationsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresNotificationListener()
    } catch (_: Throwable) {
        false
    }
}

actual val isSplinterlandsSupported: Boolean = true

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            bitmap.scale(newWidth, newHeight)
        } else {
            bitmap
        }
        val outputStream = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val context: Context by inject(Context::class.java)
    return context.filesDir.absolutePath
}

// Uses dev.spght:encryptedprefs-ktx вЂ” a maintained community fork of the deprecated
// androidx.security:security-crypto. We keep application-level encryption because
// secure settings store API keys, email passwords, and conversation encryption keys.
actual fun createSecureSettings(): Settings {
    val context: Context by inject(Context::class.java)
    return try {
        SharedPreferencesSettings(createEncryptedPrefs(context))
    } catch (_: Exception) {
        // AEADBadTagException occurs when Android Auto Backup restores the encrypted
        // prefs file but the Keystore key is hardware-bound and doesn't transfer.
        // Delete the corrupted file and recreate fresh encrypted prefs.
        context.deleteSharedPreferences("kai_secure_prefs")
        SharedPreferencesSettings(createEncryptedPrefs(context))
    }
}

private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "kai_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

actual fun createLegacySettings(): Settings? {
    val context: Context by inject(Context::class.java)
    val prefs = context.getSharedPreferences("com.katya.app_preferences", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}

// Tool definitions for Android platform
actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    addAll(CommonTools.commonToolDefinitions)
    add(
        ToolInfo(
            id = "send_notification",
            name = "Send Notification",
            description = "Send a push notification to the device",
            nameRes = Res.string.tool_send_notification_name,
            descriptionRes = Res.string.tool_send_notification_description,
        ),
    )
    add(
        ToolInfo(
            id = "create_calendar_event",
            name = "Create Calendar Event",
            description = "Create a calendar event on the user's device",
            nameRes = Res.string.tool_create_calendar_event_name,
            descriptionRes = Res.string.tool_create_calendar_event_description,
        ),
    )
    add(
        ToolInfo(
            id = "set_alarm",
            name = "Set Alarm",
            description = "Set an alarm or countdown timer on the device",
            nameRes = Res.string.tool_set_alarm_name,
            descriptionRes = Res.string.tool_set_alarm_description,
        ),
    )
    add(
        ToolInfo(
            id = "open_file",
            name = "Open File",
            description = "Open sandbox files in your default Android app",
            nameRes = Res.string.tool_open_file_name,
            descriptionRes = Res.string.tool_open_file_description,
        ),
    )
    add(AndroidHostShellTool.toolInfo)
    // SMS tools are intentionally absent here: availability is driven by the Agent-tab
    // master toggles (isSmsEnabled / isSmsSendEnabled) plus the FOSS-only `isSmsSupported`
    // check in `getAvailableTools()`. Listing per-tool toggles in the Tools tab was dead
    // UI вЂ” `getAvailableTools()` never consulted them.
}

actual fun getAvailableTools(): List<Tool> {
    val context: Context by inject(Context::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val calendarPermissionController: CalendarPermissionController by inject(CalendarPermissionController::class.java)
    val calendarRepository = CalendarRepository(context, calendarPermissionController)
    val emailStore: EmailStore by inject(EmailStore::class.java)

    return buildList {
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
            addAll(HeartbeatTools.getHeartbeatTools(memoryStore, appSettings))
        }
        if (appSettings.isToolEnabled(CommonTools.localTimeTool.schema.name)) {
            add(CommonTools.localTimeTool)
        }

        if (appSettings.isToolEnabled(CommonTools.ipLocationTool.schema.name)) {
            add(CommonTools.ipLocationTool)
        }

        if (appSettings.isToolEnabled(WebSearchTool.schema.name)) {
            add(WebSearchTool)
        }

        if (appSettings.isToolEnabled("send_notification")) {
            val notificationPermissionController: NotificationPermissionController by inject(NotificationPermissionController::class.java)
            val notificationHelper = NotificationHelper(context, notificationPermissionController)

            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "send_notification",
                        "Send a push notification to the device",
                        mapOf(
                            "title" to ParameterSchema("string", "Notification title", false),
                            "message" to ParameterSchema("string", "Notification content/body", true),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val title = args["title"] as? String ?: "Kai 9000"
                        val message = args["message"] as? String
                            ?: return mapOf("success" to false, "error" to "Message is required")

                        return when (val result = notificationHelper.sendNotification(title, message)) {
                            is NotificationResult.Success -> mapOf(
                                "success" to true,
                                "notification_id" to result.notificationId,
                                "message" to "Notification sent successfully",
                            )

                            is NotificationResult.Error -> mapOf(
                                "success" to false,
                                "error" to result.message,
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled("create_calendar_event")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "create_calendar_event",
                        "Create a calendar event on the user's device",
                        mapOf(
                            "title" to ParameterSchema("string", "Event title", true),
                            "start_time" to ParameterSchema("string", "Start time as ISO 8601, e.g. '2024-03-15T14:30:00+02:00'. Naive (no offset) is treated as user's local time.", true),
                            "end_time" to ParameterSchema("string", "End time, same format as start_time. Defaults to 1 hour after start.", false),
                            "description" to ParameterSchema("string", "Event notes or description", false),
                            "location" to ParameterSchema("string", "Event location", false),
                            "all_day" to ParameterSchema("boolean", "Whether this is an all-day event", false),
                            "reminder_minutes" to ParameterSchema("integer", "Minutes before event to send reminder (default: 15)", false),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val title = args["title"] as? String
                            ?: return mapOf("success" to false, "error" to "Title is required")
                        val startTime = args["start_time"] as? String
                            ?: return mapOf("success" to false, "error" to "Start time is required")
                        val endTime = args["end_time"] as? String
                        val description = args["description"] as? String
                        val location = args["location"] as? String
                        val allDay = (args["all_day"] as? Boolean) ?: false
                        val reminderMinutes = (args["reminder_minutes"] as? Number)?.toInt() ?: 15

                        return when (
                            val result = calendarRepository.createEvent(
                                title = title,
                                startTimeIso = startTime,
                                endTimeIso = endTime,
                                description = description,
                                location = location,
                                allDay = allDay,
                                reminderMinutes = reminderMinutes,
                            )
                        ) {
                            is CalendarResult.Success -> mapOf(
                                "success" to true,
                                "event_id" to result.eventId,
                                "title" to result.title,
                                "scheduled_for" to result.startTime,
                                "message" to "Event '${result.title}' created successfully for ${result.startTime}",
                            )

                            is CalendarResult.Error -> mapOf(
                                "success" to false,
                                "error" to result.message,
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled("set_alarm")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "set_alarm",
                        "Set an alarm or countdown timer on the device. For alarms provide hour and minutes. For countdown timers provide duration_seconds.",
                        mapOf(
                            "hour" to ParameterSchema("integer", "Hour of the alarm in 24-hour format (0-23)", false),
                            "minutes" to ParameterSchema("integer", "Minutes of the alarm (0-59)", false),
                            "label" to ParameterSchema("string", "Label for the alarm or timer", false),
                            "duration_seconds" to ParameterSchema("integer", "Duration in seconds for a countdown timer", false),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val hour = (args["hour"] as? Number)?.toInt()
                        val minutes = (args["minutes"] as? Number)?.toInt()
                        val label = args["label"] as? String
                        val durationSeconds = (args["duration_seconds"] as? Number)?.toInt()

                        val intent = if (durationSeconds != null) {
                            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            }
                        } else if (hour != null && minutes != null) {
                            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                putExtra(AlarmClock.EXTRA_HOUR, hour)
                                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            }
                        } else {
                            return mapOf(
                                "success" to false,
                                "error" to "Provide either hour+minutes for an alarm or duration_seconds for a timer",
                            )
                        }

                        return try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            if (durationSeconds != null) {
                                mapOf(
                                    "success" to true,
                                    "type" to "timer",
                                    "duration_seconds" to durationSeconds,
                                    "message" to "Timer set for $durationSeconds seconds",
                                )
                            } else {
                                mapOf(
                                    "success" to true,
                                    "type" to "alarm",
                                    "hour" to hour!!,
                                    "minutes" to minutes!!,
                                    "message" to "Alarm set for %02d:%02d".format(hour, minutes),
                                )
                            }
                        } catch (e: Exception) {
                            mapOf(
                                "success" to false,
                                "error" to (e.message ?: "Failed to set alarm"),
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled(CommonTools.openUrlTool.schema.name)) {
            add(CommonTools.openUrlTool)
        }

        if (appSettings.isToolEnabled(FetchUrlTool.schema.name)) {
            add(FetchUrlTool)
        }

        // Inject BrowserTabPool if BrowserUseTool is enabled (or we can just instantiate it once via Koin)
        try {
            val browserTabPool: com.katya.app.browser.BrowserTabPool = org.koin.java.KoinJavaComponent.getKoin().get<com.katya.app.browser.BrowserTabPool>()
            add(com.katya.app.tools.BrowserUseTool(browserTabPool))
        } catch (_: Throwable) {
            // BrowserTabPool not in Koin yet? Let's just create one or wait for it.
            val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
            com.katya.app.browser.BrowserTabPoolManager.getInstance(context).let { pool ->
                add(com.katya.app.tools.BrowserUseTool(pool))
            }
        }

        if (appSettings.isToolEnabled(OpenFileTool.schema.name)) {
            add(OpenFileTool)
        }

        if (appSettings.isSandboxEnabled()) {
            val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
            if (sandboxManager.state.value is SandboxState.Ready) {
                add(ShellCommandTool)
                add(ProcessManagerTool)
                add(RootCommandTool)
                add(SshConfigureHostTool)
            }
        }
        
        val notesStore: com.katya.app.data.NotesStore by org.koin.java.KoinJavaComponent.inject(com.katya.app.data.NotesStore::class.java)
        add(com.katya.app.tools.NotesTool(notesStore))
        add(com.katya.app.tools.LocalNoteTool)

        // Host Shell Tool (bypassing sandbox)
        add(AndroidHostShellTool)
        if (appSettings.isToolEnabled("manage_bluetooth")) {
            add(BluetoothTool)
        }
        if (appSettings.isToolEnabled("transmit_ir")) {
            add(InfraredTool)
        }

        if (appSettings.isEmailEnabled()) {
            addAll(EmailTools.getEmailTools(emailStore))
        }

        // SMS read tools: triple-gated. `isSmsSupported` is only true on FOSS builds
        // (READ_SMS declared in merged manifest). `isSmsEnabled()` is the user toggle.
        // `hasPermission()` catches runtime revocation.
        val smsReaderForTools: SmsReader? = if (isSmsSupported) {
            val smsReader: SmsReader by inject(SmsReader::class.java)
            smsReader
        } else {
            null
        }
        if (smsReaderForTools != null && appSettings.isSmsEnabled() && smsReaderForTools.hasPermission()) {
            val smsStore: SmsStore by inject(SmsStore::class.java)
            addAll(SmsTools.getSmsReadTools(smsStore, smsReaderForTools))
        }

        // SMS send tools: independently gated on the Send toggle + SEND_SMS permission.
        // These only *stage* drafts вЂ” actual sending is user-triggered via the review banner.
        if (smsReaderForTools != null && appSettings.isSmsSendEnabled()) {
            val smsSender: SmsSender by inject(SmsSender::class.java)
            if (smsSender.hasPermission()) {
                val smsDraftStore: SmsDraftStore by inject(SmsDraftStore::class.java)
                addAll(SmsTools.getSmsSendTools(smsDraftStore, smsReaderForTools, smsSender))
            }
        }

        // Notification tools: triple-gated. `isNotificationsSupported` is FOSS-only
        // (listener service declared in merged manifest). `isNotificationsEnabled()`
        // is the user toggle. `hasAccess()` catches system-level revocation.
        if (isNotificationsSupported && appSettings.isNotificationsEnabled()) {
            val notificationReader: NotificationReader by inject(NotificationReader::class.java)
            if (notificationReader.hasAccess()) {
                val notificationStore: NotificationStore by inject(NotificationStore::class.java)
                addAll(NotificationTools.getNotificationTools(notificationStore, notificationReader))
            }
        }

        val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
        addAll(mcpServerManager.getEnabledMcpTools())
    }
}

actual fun openUrl(url: String): Boolean = try {
    val context: Context by inject(Context::class.java)
    val parsedUri = url.toUri()
    val intent = if (parsedUri.scheme == "file") {
        val file = java.io.File(parsedUri.path!!)
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "*/*"
        Intent(Intent.ACTION_VIEW, contentUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, mimeType)
        }
    } else {
        Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(intent)
    true
} catch (_: Exception) {
    false
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(bytes)
}

actual fun openTtsSettings() {
    val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
    context.startActivity(intent)
}

actual fun openAssistantSettings() {
    val intent = android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
    context.startActivity(intent)
}

actual fun openAccessibilitySettings() {
    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
    context.startActivity(intent)
}

actual suspend fun generateBackupZip(jsonConfig: String, includeDatabase: Boolean, includeModels: Boolean): ByteArray {
    val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
    val baos = java.io.ByteArrayOutputStream()
    val zos = java.util.zip.ZipOutputStream(baos)

    zos.putNextEntry(java.util.zip.ZipEntry("config.json"))
    zos.write(jsonConfig.toByteArray(Charsets.UTF_8))
    zos.closeEntry()

    if (includeDatabase) {
        val dbFile = context.getDatabasePath("conversations.db")
        if (dbFile.exists()) {
            zos.putNextEntry(java.util.zip.ZipEntry("conversations.db"))
            dbFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    if (includeModels) {
        val voskDir = java.io.File(context.filesDir, "vosk")
        if (voskDir.exists() && voskDir.isDirectory) {
            voskDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(context.filesDir).path.replace('\\', '/')
                    zos.putNextEntry(java.util.zip.ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    zos.finish()
    zos.close()
    return baos.toByteArray()
}

actual suspend fun extractBackupZip(zipBytes: ByteArray): String? {
    val context: android.content.Context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
    val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
    var jsonConfig: String? = null
    var entry = zis.nextEntry
    while (entry != null) {
        if (!entry.isDirectory) {
            when (entry.name) {
                "config.json" -> {
                    jsonConfig = zis.readBytes().toString(Charsets.UTF_8)
                }
                "conversations.db" -> {
                    val dbFile = context.getDatabasePath("conversations.db")
                    dbFile.parentFile?.mkdirs()
                    dbFile.outputStream().use { zis.copyTo(it) }
                    java.io.File(dbFile.path + "-wal").delete()
                    java.io.File(dbFile.path + "-shm").delete()
                }
                else -> {
                    if (entry.name.startsWith("vosk/")) {
                        val file = java.io.File(context.filesDir, entry.name)
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zis.copyTo(it) }
                    }
                }
            }
        }
        zis.closeEntry()
        entry = zis.nextEntry
    }
    zis.close()
    return jsonConfig
}

actual fun createLocalNote(title: String, content: String): String {
    try {
        val context = org.koin.java.KoinJavaComponent.getKoin().get<android.content.Context>()
        val intent = android.content.Intent("com.google.android.gms.actions.CREATE_NOTE").apply {
            putExtra(android.content.Intent.EXTRA_TITLE, title)
            putExtra(android.content.Intent.EXTRA_TEXT, content)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return "Note creation intent sent to default notes app."
        }
        // Fallback to SEND
        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, content)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = android.content.Intent.createChooser(fallbackIntent, "Create Note").apply { 
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) 
        }
        context.startActivity(chooser)
        return "Share intent sent as fallback (please select notes app)."
    } catch (e: Exception) {
        return "Error: ${e.message}"
    }
}
