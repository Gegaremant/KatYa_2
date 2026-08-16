package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

class AppGutsTool : Tool {
    private val executor = CommandExecutor()

    override val schema = ToolSchema(
        name = "analyze_app_guts",
        description = "Получает всю внутреннюю информацию ('кишки') об установленном приложении (permissions, activities, services, receivers, version) через root. Используйте для глубокого анализа трекеров или функций.",
        parameters = mapOf(
            "package_name" to ParameterSchema("string", "Имя пакета приложения (например, com.whatsapp)", true)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val packageName = args["package_name"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'package_name' обязателен.")

        if (!executor.isRootAvailable()) {
            return mapOf("success" to false, "error" to "Для этого инструмента требуются root-права.")
        }

        val output = executor.executeCommand("dumpsys package $packageName", null, true)
        
        if (output.isBlank() || output.contains("Unable to find package")) {
            return mapOf("success" to false, "error" to "Пакет $packageName не найден.")
        }

        // Ограничиваем размер ответа, так как dumpsys может быть огромным.
        // Оставим только самое важное (версия, permissions, activities, services).
        val lines = output.lines()
        val filteredLines = mutableListOf<String>()
        var inRelevantSection = false
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Packages:")) break // Конец информации о пакете
            
            if (trimmed.startsWith("versionCode=") || trimmed.startsWith("versionName=")) {
                filteredLines.add(line)
            } else if (trimmed.startsWith("declared permissions:") || trimmed.startsWith("requested permissions:")) {
                inRelevantSection = true
                filteredLines.add(line)
            } else if (trimmed.startsWith("Activity Resolver Table:") || trimmed.startsWith("Service Resolver Table:") || trimmed.startsWith("Receiver Resolver Table:")) {
                inRelevantSection = true
                filteredLines.add(line)
            } else if (trimmed.startsWith("Key Set Manager:") || trimmed.startsWith("Compiler stats:")) {
                inRelevantSection = false
            } else if (inRelevantSection) {
                // Ограничиваем слишком длинные списки
                if (filteredLines.size < 500) {
                    filteredLines.add(line)
                }
            }
        }

        val finalOutput = if (filteredLines.isNotEmpty()) {
            filteredLines.joinToString("\n")
        } else {
            // Фолбэк, если эвристика не сработала
            output.take(4000)
        }

        return mapOf(
            "success" to true,
            "output" to finalOutput
        )
    }
}
