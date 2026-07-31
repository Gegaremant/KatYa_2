package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

object RootCommandTool : Tool {
    override val schema = ToolSchema(
        name = "execute_root_command",
        description = "Выполняет команду оболочки (shell) от имени суперпользователя (root) с использованием 'su -c'. Можно использовать для управления пакетами (pm), активити (am), дампами системы (dumpsys) и т.д. ВНИМАНИЕ: Опасно! Убедитесь, что команда безопасна.",
        parameters = mapOf(
            "command" to ParameterSchema(
                type = "string",
                description = "Строка команды оболочки для выполнения. Например: 'pm disable-user --user 0 com.example.app'",
                required = true
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String ?: return "Error: No command provided"

        if (!RootCommandExecutor.hasRootAccess()) {
            return "Error: Root access (su) is not available on this device."
        }

        val result = RootCommandExecutor.executeCommand(command)

        return if (result.isSuccess) {
            "Success (Exit code 0):\n${result.output}"
        } else {
            "Failed with exit code ${result.exitCode}:\n${result.output}"
        }
    }
}
