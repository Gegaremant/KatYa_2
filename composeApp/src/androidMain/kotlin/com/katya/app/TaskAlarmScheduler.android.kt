package com.katya.app

import android.content.Context
import com.katya.app.data.TaskStore
import org.koin.java.KoinJavaComponent.inject

actual fun createTaskAlarmScheduler(): TaskAlarmScheduler = AndroidTaskAlarmScheduler()

/**
 * Android bridge between the common [TaskAlarmScheduler] interface (called by [TaskStore]
 * on every mutation and by [TaskScheduler] on start) and the AlarmManager-backed
 * [ScheduledTaskAlarmReceiver]. Silently ignores failures so task CRUD never breaks
 * because arming an alarm threw.
 */
class AndroidTaskAlarmScheduler : TaskAlarmScheduler {

    private val context: Context by inject(Context::class.java)
    private val taskStore: TaskStore by inject(TaskStore::class.java)

    override fun scheduleNext() {
        try {
            ScheduledTaskAlarmReceiver.scheduleNext(context, taskStore)
        } catch (e: Exception) {
            // Alarm arming must never break task creation/editing.
            com.katya.app.tools.AppLogger.e("TaskAlarm", "scheduleNext failed: ${e.message}")
        }
    }

    override fun cancel() {
        try {
            ScheduledTaskAlarmReceiver.cancel(context)
        } catch (_: Exception) {
            // Best-effort; nothing left to do if cancel fails.
        }
    }
}
