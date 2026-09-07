package com.katya.app

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlin.coroutines.CoroutineContext

/** Package name of the standalone RHVoice TTS engine app (F-Droid / GitHub releases). */
const val RHVOICE_PACKAGE = "com.github.olga_yakovleva.rhvoice.android"

expect fun httpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient

expect fun createSecureSettings(): Settings

expect fun createLegacySettings(): Settings?

expect fun getBackgroundDispatcher(): CoroutineContext

expect fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile?

expect val BackIcon: ImageVector

sealed class Platform(val displayName: String) {
    sealed class Mobile(displayName: String) : Platform(displayName) {
        data object Android : Mobile("Android")
        data object Ios : Mobile("iOS")
    }

    sealed class Desktop(displayName: String) : Platform(displayName) {
        data object Mac : Desktop("macOS")
        data object Windows : Desktop("Windows")
        data object Linux : Desktop("Linux")
    }

    data object Web : Platform("Web")
}

expect val currentPlatform: Platform

expect val defaultUiScale: Float

expect fun getAppFilesDirectory(): String

expect fun getAvailableTools(): List<Tool>

/**
 * Returns all raw tool definitions available on this platform.
 * The returned tools have no isEnabled state set - that's handled by RemoteDataRepository.
 * Unlike getAvailableTools(), this returns all tools regardless of enabled state.
 */
expect fun getPlatformToolDefinitions(): List<ToolInfo>

expect val isEmailSupported: Boolean

/**
 * True only on the FOSS Android build. Gated on `READ_SMS` being declared in the
 * merged manifest — the Play Store flavor doesn't declare it, so this returns
 * false there, and the SMS feature is invisible in that build.
 */
expect val isSmsSupported: Boolean

/**
 * True only on the FOSS Android build. Gated on `KatyaNotificationListenerService`
 * being declared in the merged manifest — the Play Store flavor doesn't declare
 * it, so this returns false there, and the notification-reading feature is
 * invisible in that build.
 */
expect val isNotificationsSupported: Boolean

expect val isSplinterlandsSupported: Boolean

expect suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray

expect fun openUrl(url: String): Boolean

expect fun openTtsSettings()

/**
 * Returns true when the app identified by [packageName] is installed on this device.
 * Used to warn the user when a voice/STT engine (e.g. RHVoice) is selected but not installed.
 */
expect fun isAppInstalled(packageName: String): Boolean

expect fun openAssistantSettings()

expect fun openAccessibilitySettings()

@androidx.compose.runtime.Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

expect fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap?

expect suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String): Boolean

/**
 * Streams a (potentially large) local file to a user-chosen destination via the
 * system "save as" dialog. Unlike [saveFileToDevice] it avoids loading the whole
 * content into memory, so it's suitable for multi-GB local AI models.
 */
expect suspend fun saveLargeFileToDevice(srcFilePath: String, baseName: String, extension: String): Boolean

expect fun showToast(message: String)

/**
 * Fires a background push notification for a heartbeat that produced a non-trivial
 * response. Android additionally wires a tap-to-open-heartbeat deep link via its
 * PendingIntent; iOS/desktop just surface the message in the OS notification center
 * without deep-linking back to the conversation. No-op on web.
 */
expect fun sendHeartbeatNotification(title: String, body: String)

/**
 * Zips the requested configuration components into a single ByteArray.
 */
expect suspend fun generateBackupZip(jsonConfig: String, includeDatabase: Boolean, includeModels: Boolean): ByteArray

/**
 * Extracts a backup zip and returns the settings JSON string if successful, restoring files.
 */
expect suspend fun extractBackupZip(zipBytes: ByteArray): String?

expect fun createLocalNote(title: String, content: String): String

expect fun getDirectoryPath(directory: Any?): String?

expect suspend fun writeSkillFile(skillId: String, fileName: String, content: String): Boolean

expect suspend fun deleteSkillDir(skillId: String): Boolean

expect suspend fun readSandboxSkillFiles(): Map<String, String>
