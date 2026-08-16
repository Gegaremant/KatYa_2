package com.katya.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.katya.app.data.HeartbeatManager
import com.katya.app.data.TaskScheduler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class HeartbeatAlarmReceiver : BroadcastReceiver() {

    private val taskScheduler: TaskScheduler by inject(TaskScheduler::class.java)
    private val heartbeatManager: HeartbeatManager by inject(HeartbeatManager::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        if (!heartbeatManager.getConfig().enabled) return

        // 1. Run the heartbeat in background scope
        CoroutineScope(Dispatchers.IO + CoroutineName("HeartbeatReceiver")).launch {
            try {
                taskScheduler.triggerHeartbeatNow()
            } catch (e: Exception) {
                // Already recorded in manager, safe to ignore here
            }
        }

        // 2. Schedule the next one exactly using AlarmManager if interval is valid
        scheduleNext(context, heartbeatManager)
    }

    companion object {
        fun scheduleNext(context: Context, heartbeatManager: HeartbeatManager) {
            val config = heartbeatManager.getConfig()
            if (!config.enabled) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeartbeatAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1999, // Unique request code for heartbeat alarm
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate next time
            val nextRunMs = System.currentTimeMillis() + (config.intervalMinutes * 60_000L)
            
            try {
                // Must have exact alarm permission on Android 12+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                    } else {
                        // Fallback if permission revoked
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
                }
            } catch (e: SecurityException) {
                // Fallback to inexact
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunMs, pendingIntent)
            }
        }
        
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeartbeatAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
