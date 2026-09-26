package com.katya.app

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import com.katya.app.data.AppSettings
import com.katya.app.sandbox.FreeDeepSeekManager
import com.katya.app.sandbox.VlessProxyManager
import org.koin.java.KoinJavaComponent.inject

actual fun createDaemonController(): DaemonController = AndroidDaemonController()

class AndroidDaemonController : DaemonController {

    private val context: Context by inject(Context::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)
    private val freeDeepSeekManager: FreeDeepSeekManager by inject(FreeDeepSeekManager::class.java)
    private val vlessProxyManager: VlessProxyManager by inject(VlessProxyManager::class.java)

    fun shouldAutoStart(): Boolean = appSettings.isDaemonEnabled()

    override fun start() {
        try {
            val intent = Intent(context, DaemonService::class.java)
            context.startForegroundService(intent)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // App is not in a foreground state — cannot start foreground service (Android 12+)
        }
    }

    override fun stop() {
        val intent = Intent(context, DaemonService::class.java)
        context.stopService(intent)
    }

    override fun switchFreeDeepSeekInstance(instanceId: String) {
        freeDeepSeekManager.start(force = true, instanceId = instanceId)
    }

    override fun reconcileVlessAfterImport() {
        // The imported URI/flag may be empty or different; "connected" describes the
        // old config, so it has to go before anything restarts.
        appSettings.setVlessConnected(false)
        vlessProxyManager.start(force = true)
    }

    override fun onFreeDeepSeekInstanceRemoved(instanceId: String) {
        freeDeepSeekManager.onInstanceRemoved(instanceId)
    }
}
