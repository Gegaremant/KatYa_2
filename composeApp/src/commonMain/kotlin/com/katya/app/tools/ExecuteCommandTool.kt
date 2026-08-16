package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

class ExecuteCommandTool : Tool {
    private val executor = CommandExecutor()

    override val schema = ToolSchema(
        name = "execute_command",
        description = "Выполняет shell-команды на локальном Android-устройстве. Если use_root=true, команда выполнится с правами суперпользователя (su).",
        parameters = mapOf(
            "command" to ParameterSchema("string", "Команда для выполнения (например, 'ls -la /data/data/com.termux/files/home')", true),
            "use_root" to ParameterSchema("boolean", "Запустить команду от имени root (через su). Установите true для доступа к системным файлам или Termux.", false),
            "work_dir" to ParameterSchema("string", "Рабочая директория (опционально).", false)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'command' обязателен.")
        
        val useRoot = args["use_root"] as? Boolean ?: false
        val workDir = args["work_dir"]?.toString()

        if (useRoot && !executor.isRootAvailable()) {
            return mapOf("success" to false, "error" to "Root (su) недоступен на этом устройстве.")
        }

        val output = executor.executeCommand(command, workDir, useRoot)
        
        return mapOf(
            "success" to (!output.startsWith("Error:") && !output.startsWith("Execution error:")),
            "output" to output
        )
    }
}
