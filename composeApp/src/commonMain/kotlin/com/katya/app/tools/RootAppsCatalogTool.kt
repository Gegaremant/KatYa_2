package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class RootAppsCatalogTool(private val httpClient: HttpClient) : Tool {

    override val schema = ToolSchema(
        name = "search_root_apps_catalog",
        description = "Ищет полезные root-приложения, модули Magisk и утилиты в репозитории awesome-android-root. Используйте, если пользователь просит посоветовать системные утилиты или модификации.",
        parameters = mapOf(
            "query" to ParameterSchema("string", "Поисковый запрос (категория или название), либо оставьте пустым для получения всего списка.", false)
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val query = args["query"]?.toString()?.lowercase() ?: ""
        
        return try {
            val response = httpClient.get("https://raw.githubusercontent.com/awesome-android-root/awesome-android-root/main/README.md")
            val text = response.bodyAsText()
            
            if (query.isEmpty()) {
                // Если запрос пустой, возвращаем только оглавление (строки, начинающиеся с - [)
                val toc = text.lines().filter { it.trim().startsWith("- [") }.joinToString("\n")
                mapOf(
                    "success" to true,
                    "content" to "Оглавление каталога (запросите конкретную категорию или ключевое слово для деталей):\n$toc"
                )
            } else {
                // Если есть запрос, пытаемся найти секции или строки, где он упоминается
                val lines = text.lines()
                val results = mutableListOf<String>()
                
                var inRelevantSection = false
                for (line in lines) {
                    if (line.startsWith("## ")) {
                        inRelevantSection = line.lowercase().contains(query)
                    }
                    
                    if (inRelevantSection || line.lowercase().contains(query)) {
                        results.add(line)
                    }
                }
                
                if (results.isEmpty()) {
                    mapOf("success" to true, "content" to "Ничего не найдено по запросу: $query")
                } else {
                    // Ограничиваем размер ответа, чтобы не перегружать контекст
                    val output = results.joinToString("\n").take(3000)
                    mapOf("success" to true, "content" to output)
                }
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Ошибка при получении каталога: ${e.message}")
        }
    }
}
