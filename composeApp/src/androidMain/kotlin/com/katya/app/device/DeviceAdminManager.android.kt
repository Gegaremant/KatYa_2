package com.katya.app.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

actual object DeviceAdminManager {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getContext(): Context? = appContext

    actual fun isDeviceAdminActive(): Boolean {
        val context = getContext() ?: return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val componentName = ComponentName(context, "com.katya.app.DeviceAdminReceiver")
        return dpm.isAdminActive(componentName)
    }

    actual fun requestDeviceAdmin() {
        val context = getContext() ?: return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, "com.katya.app.DeviceAdminReceiver"))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Для управления устройством")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    actual fun openDeviceAdminSettings() {
        val context = getContext() ?: return
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    actual fun isTrustAgentEnabled(): Boolean {
        // Точная проверка, является ли приложение агентом доверия, сложна.
        // Возвращаем false для простоты, но можно попытаться проверить через Settings.Secure.
        return false
    }

    actual fun openTrustAgentSettings() {
        val context = getContext() ?: return
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
