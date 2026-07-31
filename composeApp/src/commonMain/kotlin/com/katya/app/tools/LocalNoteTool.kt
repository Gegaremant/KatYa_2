package com.katya.app.tools

import com.katya.app.createLocalNote
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

object LocalNoteTool : Tool {
    override val schema = ToolSchema(
        name = "create_local_note",
        description = "Создает заметку в локальном приложении заметок на телефоне пользователя (через системный Intent).",
        parameters = mapOf(
            "title" to ParameterSchema(
                type = "string",
                description = "Заголовок заметки",
                required = true
            ),
            "content" to ParameterSchema(
                type = "string",
                description = "Содержимое (текст) заметки",
                required = true
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val title = args["title"] as? String ?: return "Error: No title provided"
        val content = args["content"] as? String ?: return "Error: No content provided"
        
        return createLocalNote(title, content)
    }
}
