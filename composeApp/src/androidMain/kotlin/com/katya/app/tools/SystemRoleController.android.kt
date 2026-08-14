package com.katya.app.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.koin.java.KoinJavaComponent.inject

actual class SystemRoleController actual constructor() {
    private val context: Context by inject(Context::class.java)

    actual fun openDeviceAdminSettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun openDefaultAssistantSettings() {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun openTrustAgentSettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun isDeviceAdmin(): Boolean = false

    actual fun isDefaultAssistant(): Boolean {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        return roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
    }

    actual fun isTrustAgent(): Boolean = false
}
