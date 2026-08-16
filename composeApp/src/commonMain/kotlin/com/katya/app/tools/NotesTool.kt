package com.katya.app.tools

import com.katya.app.data.NotesStore
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema

class NotesTool(private val store: NotesStore) : Tool {
    override val schema = ToolSchema(
        name = "manage_internal_notes",
        description = "Позволяет Кате сохранять, искать, обновлять и удалять свои внутренние заметки.",
        parameters = mapOf(
            "action" to ParameterSchema(
                type = "string",
                description = "Действие: 'add', 'get_all', 'search', 'update', 'delete'",
                required = true
            ),
            "title" to ParameterSchema(
                type = "string",
                description = "Заголовок заметки (для add, update)",
                required = false
            ),
            "content" to ParameterSchema(
                type = "string",
                description = "Содержимое заметки (для add, update)",
                required = false
            ),
            "tags" to ParameterSchema(
                type = "string",
                description = "Теги через запятую (для add, update)",
                required = false
            ),
            "query" to ParameterSchema(
                type = "string",
                description = "Текст для поиска (для search)",
                required = false
            ),
            "id" to ParameterSchema(
                type = "number",
                description = "ID заметки (для update, delete)",
                required = false
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"] as? String ?: return "Error: No action provided"

        return when (action) {
            "add" -> {
                val title = args["title"] as? String ?: return "Error: No title provided"
                val content = args["content"] as? String ?: return "Error: No content provided"
                val tags = args["tags"] as? String
                store.addNote(title, content, tags)
                "Note added successfully."
            }
            "get_all" -> {
                val notes = store.getAllNotes()
                if (notes.isEmpty()) "No notes found." else notes.joinToString("\n---\n") { "ID: ${it.id}\nTitle: ${it.title}\nContent: ${it.content}\nTags: ${it.tags}" }
            }
            "search" -> {
                val query = args["query"] as? String ?: return "Error: No query provided"
                val notes = store.searchNotes(query)
                if (notes.isEmpty()) "No notes found for query: $query" else notes.joinToString("\n---\n") { "ID: ${it.id}\nTitle: ${it.title}\nContent: ${it.content}\nTags: ${it.tags}" }
            }
            "update" -> {
                val idNum = args["id"] as? Number ?: return "Error: No id provided"
                val title = args["title"] as? String ?: return "Error: No title provided"
                val content = args["content"] as? String ?: return "Error: No content provided"
                val tags = args["tags"] as? String
                store.updateNote(idNum.toLong(), title, content, tags)
                "Note updated successfully."
            }
            "delete" -> {
                val idNum = args["id"] as? Number ?: return "Error: No id provided"
                store.deleteNote(idNum.toLong())
                "Note deleted successfully."
            }
            else -> "Error: Unknown action '$action'"
        }
    }
}
