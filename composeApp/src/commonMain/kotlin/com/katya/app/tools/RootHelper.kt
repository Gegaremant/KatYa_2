package com.katya.app.tools

import com.katya.app.tools.AppLogger
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object RootHelper {
    private val commandExecutor = CommandExecutor()

    /**
     * Real application id from androidApp/build.gradle.kts. The old default was
     * `com.katya.app`, which is only the Kotlin package name — `pm grant` against
     * it always fails with "Unknown package", so "Разрешить все!" silently granted
     * nothing while the UI reported success.
     */
    const val APP_PACKAGE = "com.inspiredandroid.katya"

    /** What actually came back from the `pm`/`appops` calls, so the UI can be honest. */
    data class GrantReport(
        val rootAvailable: Boolean,
        val granted: Int,
        val failed: Int,
        val failures: List<String>,
    ) {
        val ok: Boolean get() = rootAvailable && granted > 0
    }

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
    suspend fun grantAllPermissions(packageName: String = APP_PACKAGE): GrantReport = withContext(kotlinx.coroutines.Dispatchers.Default) {
        grantAllPermissionsBlocking(packageName)
    }

    private fun grantAllPermissionsBlocking(packageName: String = APP_PACKAGE): GrantReport {
        if (!commandExecutor.isRootAvailable()) {
            return GrantReport(rootAvailable = false, granted = 0, failed = 0, failures = emptyList())
        }

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
        val appOps = listOf(
            "MANAGE_EXTERNAL_STORAGE",
            "SYSTEM_ALERT_WINDOW",
            "GET_USAGE_STATS",
            "WRITE_SETTINGS",
        )

        var granted = 0
        val failures = mutableListOf<String>()

        fun run(target: String, command: String) {
            val output = runCatching { commandExecutor.executeCommand(command, useRoot = true) }.getOrNull()
            // `pm`/`appops` print nothing on success and a reason on failure.
            val rejected = output.isNullOrBlank() || FAILURE_MARKERS.any { it in output }
            if (rejected) {
                failures += "$target: ${output?.trim().orEmpty().ifEmpty { "пустой ответ" }}"
            } else {
                granted++
            }
        }

        for (perm in permissions) run(perm, "pm grant $packageName $perm")
        for (op in appOps) run(op, "appops set $packageName $op allow")

        AppLogger.i("RootHelper", "Выдано $granted, отказано ${failures.size} для $packageName")
        AppLogger.rootAction(
            "GrantPermissions",
            "Выдано $granted, отказано ${failures.size}" + if (failures.isEmpty()) "" else ": ${failures.take(3).joinToString("; ")}",
        )

        return GrantReport(
            rootAvailable = true,
            granted = granted,
            failed = failures.size,
            failures = failures,
        )
    }

    private val FAILURE_MARKERS =
        listOf("Error", "Exception", "not allowed", "Unknown", "denied", "SecurityException", "No permission")
}
