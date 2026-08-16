package com.katya.app.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.java.KoinJavaComponent.getKoin
import org.json.JSONObject

actual class IntentOps actual constructor() {
    private val context: Context = getKoin().get<Context>()

    actual fun sendIntent(action: String, dataUri: String?, packageName: String?, extrasJson: String?): String {
        return try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (!dataUri.isNullOrEmpty()) {
                intent.data = Uri.parse(dataUri)
            }

            if (!packageName.isNullOrEmpty()) {
                intent.setPackage(packageName)
            }

            if (!extrasJson.isNullOrEmpty()) {
                val json = JSONObject(extrasJson)
                for (key in json.keys()) {
                    val value = json.get(key)
                    when (value) {
                        is String -> intent.putExtra(key, value)
                        is Int -> intent.putExtra(key, value)
                        is Boolean -> intent.putExtra(key, value)
                        is Double -> intent.putExtra(key, value)
                        is Long -> intent.putExtra(key, value)
                    }
                }
            }

            // Check if there is an activity that can handle it
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "Интент '$action' успешно отправлен."
            } else {
                "Error: Нет приложения, которое может обработать этот интент."
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
