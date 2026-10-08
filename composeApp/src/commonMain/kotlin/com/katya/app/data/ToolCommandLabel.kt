package com.katya.app.data

import com.katya.app.tools.SecretRedactor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Показывает в «видимости работы» не только название инструмента, но и саму команду.
 *
 * Feedback 08.10 п.3: пользователь смотрел на строку «Execute Shell Command» (плюс
 * ещё 31 английская подпись) и не мог понять, что Катя вообще делает. Подпись без
 * команды бесполезна — командовых вызовов подряд бывает много, и все они выглядели
 * одинаково.
 *
 * Команда попадает в подпись из аргументов вызова, которые модель уже передала, и
 * проходит через [SecretRedactor]: в команду может попасть токен (например
 * `curl -H "Authorization: Bearer …"`), а подпись уходит в интерфейс и в журнал.
 */
internal fun String.withCommandFrom(toolName: String, argumentsJson: String): String {
    val detail = commandDetailFor(toolName, argumentsJson) ?: return this
    return "$this · $detail"
}

/**
 * Ключи, по которым команда лежит в аргументах, в порядке приоритета.
 *
 * Первое совпадение и берётся: у командовых инструментов оно одно, но у
 * `process_manager`-подобных могут быть варианты, и «первое осмысленное» —
 * честнее, чем «последнее».
 */
private val COMMAND_KEYS = listOf("command", "cmd", "script", "query", "path", "url", "pattern")

/** Инструменты, для которых показывать деталь осмысленно. */
private val DETAILED_TOOLS = setOf(
    "execute_shell_command",
    "host_shell_command",
    "run_command",
    "ssh_command",
    "read_file",
    "view_file",
    "write_file",
    "replace_file_content",
    "multi_replace_file_content",
    "grep_search",
    "list_dir",
    "read_url_content",
    "fetch_url",
    "open_url",
    "search_web",
    "web_search",
)

/** Длиннее этого — на экране всё равно обрежется многоточием, а журнал распухнет. */
private const val MAX_DETAIL_CHARS = 120

internal fun commandDetailFor(toolName: String, argumentsJson: String): String? {
    if (toolName !in DETAILED_TOOLS) return null
    if (argumentsJson.isBlank()) return null
    val obj = runCatching {
        Json.parseToJsonElement(argumentsJson) as? JsonObject
    }.getOrNull() ?: return null
    val detail = COMMAND_KEYS.firstNotNullOfOrNull { key ->
        (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    } ?: return null
    val collapsed = detail.replace(Regex("\\s+"), " ").trim()
    if (collapsed.isEmpty()) return null
    return SecretRedactor.redact(collapsed).let {
        if (it.length <= MAX_DETAIL_CHARS) it else it.take(MAX_DETAIL_CHARS - 1) + "…"
    }
}
