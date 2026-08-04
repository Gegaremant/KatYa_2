package com.katya.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.katya.app.data.HeartbeatManager
import com.katya.app.data.TaskScheduler
import com.katya.app.sandbox.VlessProxyManager
import com.katya.app.shared.R
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DaemonService : Service() {

    companion object {
        private const val CHANNEL_ID = "kai_daemon_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private val taskScheduler: TaskScheduler by inject()
    private val heartbeatManager: HeartbeatManager by inject()
    private val vlessProxyManager: VlessProxyManager by inject()
    private val freeDeepSeekManager: com.katya.app.sandbox.FreeDeepSeekManager by inject()
    private val sttController: com.katya.app.stt.SttController by inject()

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var mediaSession: android.media.session.MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            stopSelf()
            return
        }
        
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Katya::DaemonWakeLock")
        wakeLock?.acquire()
        
        // Setup MediaSession for watch integration
        mediaSession = android.media.session.MediaSession(this, "KatyaWatchSession").apply {
            setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : android.media.session.MediaSession.Callback() {
                override fun onPlay() {
                    // Start STT when play is pressed
                    if (!sttController.isListening.value) {
                        sttController.startListening { result ->
                            if (result.isNotBlank()) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                                    val dataRepository: com.katya.app.data.DataRepository = org.koin.java.KoinJavaComponent.getKoin().get()
                                    dataRepository.ask(result, emptyList())
                                }
                            }
                        }
                    }
                }
                override fun onSkipToNext() {
                    // Fast submit (Next)
                    if (sttController.isListening.value) {
                        sttController.stopListening()
                    }
                }
                override fun onSkipToPrevious() {
                    // Cancel/Stop
                    sttController.stopListening()
                }
                override fun onPause() {
                    onSkipToPrevious()
                }
            })
            // Activate the session
            isActive = true
        }

        // The scheduler owns its own long-lived scope; this foreground service's job is
        // to keep the app process alive so that scope keeps running. START_STICKY (below)
        // asks the OS to re-create us if we're killed, which will re-trigger onCreate and
        // call start() again — idempotent no-op if the loop is already running.
        taskScheduler.start()
        HeartbeatAlarmReceiver.scheduleNext(this, heartbeatManager)
        vlessProxyManager.start()
        freeDeepSeekManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        HeartbeatAlarmReceiver.cancel(this)
        vlessProxyManager.stop()
        freeDeepSeekManager.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.daemon_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.daemon_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.daemon_notification_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
