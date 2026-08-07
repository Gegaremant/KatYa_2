package com.katya.app.tools

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object RootHelper {
    private val commandExecutor = CommandExecutor()

    fun logAction(actionName: String, reason: String) {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dateStr = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val timeStr = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
        
        val logLine = "$timeStr | Рут-права: $actionName | $reason"
        val logDir = "/sdcard/Katya"
        val logFile = "$logDir/root_actions_$dateStr.log"

        commandExecutor.executeCommand("mkdir -p $logDir && echo \"$logLine\" >> $logFile", useRoot = true)
    }

    fun grantAllPermissions(packageName: String = "com.katya.app") {
        if (!commandExecutor.isRootAvailable()) return

        val permissions = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_CONTACTS"
        )
        for (perm in permissions) {
            commandExecutor.executeCommand("pm grant $packageName $perm", useRoot = true)
        }
        logAction("GrantPermissions", "Автоматическая выдача всех разрешений при активации God Mode")
    }
}
