package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SshTool : Tool {
    private val sshClient = SshClient()

    override val schema = ToolSchema(
        name = "ssh_execute",
        description = "Выполняет команду на удаленном сервере по SSH.",
        parameters = mapOf(
            "host" to ParameterSchema("string", "IP или домен сервера", true),
            "port" to ParameterSchema("integer", "Порт SSH (обычно 22)", true),
            "user" to ParameterSchema("string", "Имя пользователя", true),
            "password" to ParameterSchema("string", "Пароль", true),
            "command" to ParameterSchema("string", "Команда для выполнения", true)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val host = args["host"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'host' обязателен.")
        val port = (args["port"] as? Number)?.toInt() ?: 22
        val user = args["user"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'user' обязателен.")
        val password = args["password"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'password' обязателен.")
        val command = args["command"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'command' обязателен.")

        return withContext(Dispatchers.IO) {
            val output = sshClient.executeCommand(host, port, user, password, command)
            mapOf(
                "success" to !output.startsWith("SSH Error:"),
                "output" to output
            )
        }
    }
}
