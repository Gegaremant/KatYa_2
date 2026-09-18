package com.katya.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.katya.app.data.TaskScheduler
import com.katya.app.data.TaskStatus
import com.katya.app.data.TaskStore
import com.katya.app.data.TaskTrigger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * Wakes the app process for the nearest pending scheduled task.
 *
 * Scheduled tasks normally run inside [TaskScheduler]'s in-process poll loop, which only
 * exists while the process is alive. This receiver is armed via [scheduleNext] to an
 * exact alarm at the nearest pending task time; when it fires it restarts the process
 * (fresh Application + Koin), runs the due-task pass, and re-arms for the next one.
 */
class ScheduledTaskAlarmReceiver : BroadcastReceiver() {

    private val taskScheduler: TaskScheduler by inject(TaskScheduler::class.java)
    private val taskStore: TaskStore by inject(TaskStore::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        // Make sure the polling loop is running (harmless if already started) so the
        // process stays alive for follow-up tasks / heartbeats after we finish here.
        taskScheduler.start()

        CoroutineScope(Dispatchers.IO + CoroutineName("ScheduledTaskReceiver")).launch {
            try {
                taskScheduler.runDueTasksNow()
            } catch (e: Exception) {
                // Failure is already recorded per-task in the scheduler; don't crash the receiver.
            }
        }

        // Re-arm for the next pending task regardless of whether this run had work.
        scheduleNext(context, taskStore)
    }

    companion object {
        /** Unique request code so this alarm doesn't collide with the heartbeat alarm (1999). */
        private const val REQUEST_CODE = 2000

        /** Small delay so a run that just produced a cron "next" time doesn't busy-loop. */
        private const val DUE_NOW_FIRE_DELAY_MS = 5_000L

        fun scheduleNext(context: Context, taskStore: TaskStore) {
            val nextRunMs = nextPendingAlarmTimeMs(taskStore)
            if (nextRunMs == null) {
                cancel(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduledTaskAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                    } else {
                        // Fallback if the exact-alarm permission was revoked.
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduledTaskAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
        }

        /**
         * Nearest future alarm instant across all pending non-heartbeat tasks.
         * Already-due tasks fire almost immediately (after [DUE_NOW_FIRE_DELAY_MS]).
         * Returns null when there is nothing to wake up for.
         */
        fun nextPendingAlarmTimeMs(taskStore: TaskStore): Long? {
            val now = System.currentTimeMillis()
            val nextDue = taskStore.getAllTasks()
                .filter { it.status == TaskStatus.PENDING && it.trigger != TaskTrigger.HEARTBEAT }
                .minOfOrNull { it.scheduledAtEpochMs }
                ?: return null
            return if (nextDue <= now) now + DUE_NOW_FIRE_DELAY_MS else nextDue
        }
    }
}