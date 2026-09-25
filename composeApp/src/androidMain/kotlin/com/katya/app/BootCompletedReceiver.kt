package com.katya.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the alarms after a reboot.
 *
 * Android clears every pending alarm when the device boots, so without this the
 * task list looks perfectly healthy in the UI and simply never fires again —
 * the kind of bug that only shows up days later, after a reboot, and looks like
 * "the scheduler is broken".
 *
 * `QUICKBOOT_POWERON` and `LOCKED_BOOT_COMPLETED` are included because several
 * OEM skins send one or the other instead of a plain `BOOT_COMPLETED`.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        try {
            val taskScheduler: com.katya.app.data.TaskScheduler =
                org.koin.java.KoinJavaComponent.getKoin().get()
            val taskStore: com.katya.app.data.TaskStore =
                org.koin.java.KoinJavaComponent.getKoin().get()
            val heartbeatManager: com.katya.app.data.HeartbeatManager =
                org.koin.java.KoinJavaComponent.getKoin().get()

            ScheduledTaskAlarmReceiver.scheduleNext(context, taskStore)
            if (heartbeatManager.getConfig().enabled) {
                HeartbeatAlarmReceiver.scheduleNext(context, heartbeatManager)
            }
            // Restart the in-process loop too, so heartbeats keep running while the
            // daemon is up rather than only when an alarm happens to fire.
            taskScheduler.start()
            com.katya.app.tools.AppLogger.i("BootCompleted", "Re-armed alarms after $action")
        } catch (e: Exception) {
            com.katya.app.tools.AppLogger.e("BootCompleted", "Failed to re-arm alarms: ${e.message}")
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
