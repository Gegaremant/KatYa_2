package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SftpTool : Tool {
    private val sshClient = SshClient()

    override val schema = ToolSchema(
        name = "sftp_transfer",
        description = "Загружает или скачивает файлы по SFTP на удаленный сервер.",
        parameters = mapOf(
            "host" to ParameterSchema("string", "IP или домен сервера", true),
            "port" to ParameterSchema("integer", "Порт SSH (обычно 22)", true),
            "user" to ParameterSchema("string", "Имя пользователя", true),
            "password" to ParameterSchema("string", "Пароль", true),
            "action" to ParameterSchema("string", "Действие: 'upload' или 'download'", true),
            "local_path" to ParameterSchema("string", "Локальный путь (на Android)", true),
            "remote_path" to ParameterSchema("string", "Удаленный путь (на сервере)", true)
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
        val action = args["action"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'action' обязателен.")
        val localPath = args["local_path"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'local_path' обязателен.")
        val remotePath = args["remote_path"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'remote_path' обязателен.")

        return withContext(Dispatchers.IO) {
            val output = if (action == "upload") {
                sshClient.uploadFile(host, port, user, password, localPath, remotePath)
            } else if (action == "download") {
                sshClient.downloadFile(host, port, user, password, remotePath, localPath)
            } else {
                return@withContext mapOf("success" to false, "error" to "Неизвестное действие: $action")
            }
            
            mapOf(
                "success" to !output.startsWith("SFTP"),
                "output" to output
            )
        }
    }
}
