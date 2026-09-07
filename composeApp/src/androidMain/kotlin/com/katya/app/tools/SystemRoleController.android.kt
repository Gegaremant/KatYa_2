package com.katya.app.tools

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.koin.java.KoinJavaComponent.inject

actual class SystemRoleController actual constructor() {
    private val context: Context by inject(Context::class.java)

    actual fun openDeviceAdminSettings() {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(context, "com.katya.app.receivers.KatyaDeviceAdminReceiver"),
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: открыть настройки безопасности
            val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
        }
    }

    actual fun openDefaultAssistantSettings() {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun openTrustAgentSettings() {
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: открыть настройки безопасности
            val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
        }
    }

    actual fun isDeviceAdmin(): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, "com.katya.app.receivers.KatyaDeviceAdminReceiver")
        dpm.isAdminActive(componentName)
    } catch (_: Exception) {
        false
    }

    actual fun isDefaultAssistant(): Boolean = try {
        val current = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "assistant",
        ).orEmpty()
        if (current.isEmpty()) {
            false
        } else {
            try {
                val component = ComponentName.unflattenFromString(current)
                component?.packageName == context.packageName
            } catch (_: Exception) {
                false
            }
        }
    } catch (_: Exception) {
        false
    }
}
