package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

expect class IntentOps() {
    /**
     * @param action e.g. "android.intent.action.VIEW"
     * @param dataUri e.g. "https://google.com" or "package:com.example.app"
     * @param packageName e.g. "com.example.app" to launch specific app
     * @param extrasJson JSON string representing extras (e.g. {"key": "value"})
     */
    fun sendIntent(action: String, dataUri: String?, packageName: String?, extrasJson: String?): String
}

class IntentTool : Tool {
    private val intentOps = IntentOps()

    override val schema = ToolSchema(
        name = "send_android_intent",
        description = "Отправляет системный Intent. Позволяет Кате открывать другие приложения, например AppManager. Чтобы открыть AppManager для конкретного приложения, используйте action='android.intent.action.VIEW', package_name='io.github.muntashirakon.AppManager', dataUri='package:<имя_пакета>'.",
        parameters = mapOf(
            "action" to ParameterSchema("string", "Intent action (например, android.intent.action.VIEW, android.intent.action.MAIN, android.intent.action.SEND)", true),
            "data_uri" to ParameterSchema("string", "URI данных (например, package:com.whatsapp или https://...)", false),
            "package_name" to ParameterSchema("string", "Имя пакета (например, io.github.muntashirakon.AppManager), если нужно отправить интент конкретному приложению.", false),
            "extras" to ParameterSchema("string", "JSON строка с дополнительными параметрами (extras).", false)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'action' обязателен.")
            
        val dataUri = args["data_uri"]?.toString()
        val packageName = args["package_name"]?.toString()
        val extrasJson = args["extras"]?.toString()

        val result = intentOps.sendIntent(action, dataUri, packageName, extrasJson)
        return mapOf(
            "success" to !result.startsWith("Error:"),
            "result" to result
        )
    }
}
