package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

class DownloadFileTool : Tool {
    private val downloader = FileDownloader()

    override val schema = ToolSchema(
        name = "download_file",
        description = "Скачивает файл по URL и сохраняет по указанному пути. Замена wget/curl.",
        parameters = mapOf(
            "url" to ParameterSchema("string", "Прямая ссылка на файл", true),
            "destination_path" to ParameterSchema("string", "Абсолютный путь, куда сохранить файл", true),
            "use_root" to ParameterSchema("boolean", "Использовать права root (su) для сохранения файла в системные папки (например, в Termux).", false)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val url = args["url"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'url' обязателен.")
        val dest = args["destination_path"]?.toString()
            ?: return mapOf("success" to false, "error" to "Параметр 'destination_path' обязателен.")
        val useRoot = args["use_root"] as? Boolean ?: false

        val result = downloader.download(url, dest, useRoot)
        
        return mapOf(
            "success" to !result.startsWith("Error:"),
            "message" to result
        )
    }
}
