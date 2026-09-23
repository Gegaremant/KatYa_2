package com.katya.app

/**
 * Arms the OS-level alarm for the next pending scheduled task.
 *
 * Scheduled tasks are persisted (TaskStore → AppSettings) and checked by the in-process
 * TaskScheduler loop every 60s — but that loop only exists while the process is alive.
 * When Android kills the app, tasks must still fire on time: this interface (implemented
 * on Android via AlarmManager) wakes the process for the next due task and re-arms itself.
 *
 * No-op on platforms without an alarm service.
 */
interface TaskAlarmScheduler {
    /** (Re)arm the alarm for the nearest pending task. Safe to call frequently. */
    fun scheduleNext()

    /** Cancel any pending task alarm. */
    fun cancel()
}

expect fun createTaskAlarmScheduler(): TaskAlarmScheduler
