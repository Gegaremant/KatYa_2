package com.katya.app.tools

import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object RootHelper {
    private val commandExecutor = CommandExecutor()

    fun logAction(actionName: String, reason: String) {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dateStr = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val timeStr = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"

        val logLine = "$timeStr | Рут-права: $actionName | $reason"
        val publicLogDir = "/sdcard/katya"
        val rootLogDir = "/data/media/0/katya" // Bypasses FUSE mount namespace issues in su
        val logFile = "$rootLogDir/root_actions_$dateStr.log"

        commandExecutor.executeCommand("mkdir -p $rootLogDir && echo \"$logLine\" >> $logFile && chown -R media_rw:media_rw $rootLogDir && chmod -R 777 $rootLogDir", useRoot = true, isLogAction = true)

        // Try to create the public directory symlink/folder for visibility if possible
        commandExecutor.executeCommand("mkdir -p $publicLogDir && chmod 777 $publicLogDir", useRoot = false, isLogAction = true)
    }

    /**
     * Grants every dangerous permission and app-op for [packageName] via `pm`
     * and `appops`. Suspending so callers never run the ~24 shell commands on
     * the Main thread (which previously blocked the UI for minutes and crashed
     * the onboarding when God Mode was enabled).
     */
    suspend fun grantAllPermissions(packageName: String = "com.katya.app") {
        withContext(kotlinx.coroutines.Dispatchers.Default) {
            grantAllPermissionsBlocking(packageName)
        }
    }

    private fun grantAllPermissionsBlocking(packageName: String = "com.katya.app") {
        if (!commandExecutor.isRootAvailable()) return

        val permissions = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_PHONE_STATE",
        )
        for (perm in permissions) {
            commandExecutor.executeCommand("pm grant $packageName $perm", useRoot = true)
        }

        val appOps = listOf(
            "MANAGE_EXTERNAL_STORAGE",
            "SYSTEM_ALERT_WINDOW",
            "GET_USAGE_STATS",
            "WRITE_SETTINGS",
        )
        for (op in appOps) {
            commandExecutor.executeCommand("appops set $packageName $op allow", useRoot = true)
        }

        logAction("GrantPermissions", "Автоматическая выдача всех разрешений при активации God Mode")
    }
}
