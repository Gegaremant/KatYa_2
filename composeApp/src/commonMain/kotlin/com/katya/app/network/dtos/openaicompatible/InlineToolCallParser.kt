package com.katya.app.network.dtos.openaicompatible

import com.katya.app.network.tools.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class ParsedInlineToolCall(
    val name: String,
    val arguments: String,
)

internal data class InlineToolCallExtraction(
    val cleanedText: String,
    val calls: List<ParsedInlineToolCall>,
)

private const val OPEN_TAG = "<tool_call>"
private const val CLOSE_TAG = "</tool_call>"
private const val DSML_OPEN = "DSML"

private val functionTagRegex = Regex("<function=([\\w.\\-]+)>([\\s\\S]*?)</function>")
private val parameterTagRegex = Regex("<parameter=([\\w.\\-]+)>([\\s\\S]*?)</parameter>")

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Some OpenAI-compatible models (Qwen, Hermes-style fine-tunes, and a number of
 * self-hosted endpoints) occasionally emit tool calls as inline `<tool_call>` XML
 * inside the assistant content instead of populating the structured `tool_calls`
 * field. Detect those blocks, convert each one into a synthetic tool call, and
 * return the surrounding natural-language text with the blocks removed.
 *
 * Two block flavors are accepted:
 *  - Hermes / OpenHands XML: `<function=NAME><parameter=KEY>VALUE</parameter>…</function>`
 *  - JSON: `{ "name": "...", "arguments": { … } }`
 *
 * Parameter values are coerced to JSON primitive types using the tool's schema so
 * `timeout=180` becomes a number rather than the string "180".
 */
internal fun extractInlineToolCalls(
    content: String,
    tools: List<Tool>,
): InlineToolCallExtraction {
    if (!INLINE_MARKERS.any { content.contains(it) }) {
        return InlineToolCallExtraction(content, emptyList())
    }

    val calls = mutableListOf<ParsedInlineToolCall>()

    // Feedback #9/#13: FreeDeepseekAPI documents four fallback shapes for a tool call
    // that arrives as text instead of in the structured field. We only understood the
    // first one, so the other three were rendered to the user verbatim — "the UI shows
    // code with a tool call in it and nothing happens on the phone". Each pass below
    // handles one documented flavour and strips it out of the text it succeeds on.
    var text = content
    text = extractTaggedBlocks(text, tools, calls)
    text = extractDsmlBlocks(text, tools, calls)
    text = extractPrefixedCalls(text, tools, calls)
    text = extractFencedJsonCalls(text, tools, calls)

    return InlineToolCallExtraction(text.trim(), calls)
}

private val INLINE_MARKERS = listOf(
    OPEN_TAG,
    DSML_OPEN,
    "TOOL_CALL:",
    "```json",
    "```",
)

/** `<tool_call>…</tool_call>` — the original Hermes-style block. */
private fun extractTaggedBlocks(
    content: String,
    tools: List<Tool>,
    calls: MutableList<ParsedInlineToolCall>,
): String {
    if (!content.contains(OPEN_TAG)) return content
    val cleaned = StringBuilder()
    var pos = 0
    while (pos < content.length) {
        val openIdx = content.indexOf(OPEN_TAG, pos)
        if (openIdx < 0) {
            cleaned.append(content, pos, content.length)
            break
        }
        cleaned.append(content, pos, openIdx)
        val closeIdx = content.indexOf(CLOSE_TAG, openIdx + OPEN_TAG.length)
        val blockEnd = if (closeIdx >= 0) closeIdx + CLOSE_TAG.length else content.length
        val innerEnd = if (closeIdx >= 0) closeIdx else content.length
        val inner = content.substring(openIdx + OPEN_TAG.length, innerEnd).trim()

        val parsed = parseToolCallBlock(inner, tools)
        if (parsed != null) {
            calls.add(parsed)
        } else {
            // Couldn't make sense of this block — keep it visible rather than silently dropping it.
            cleaned.append(content, openIdx, blockEnd)
        }
        pos = blockEnd
    }
    return cleaned.toString()
}

/**
 * DeepSeek's own DSML envelope, in both the literal and the web-rendered spelling:
 * `<｜DSML｜tool_calls>[ … ]</｜DSML｜tool_calls>` and `<｜｜DSML｜｜ Tool Calls>…`.
 */
private val dsmlRegex = Regex("<｜[^>]*tool_calls[^>]*>([\\s\\S]*?)</｜[^>]*tool_calls*>", RegexOption.IGNORE_CASE)
private val dsmlOpenOnlyRegex = Regex("<｜[^>]*tool_calls[^>]*>([\\s\\S]*)$", RegexOption.IGNORE_CASE)

private fun extractDsmlBlocks(
    content: String,
    tools: List<Tool>,
    calls: MutableList<ParsedInlineToolCall>,
): String {
    if (!content.contains("DSML")) return content
    val byPair = dsmlRegex.replace(content) { match ->
        val parsed = parseCallArray(match.groupValues[1], tools)
        if (parsed.isEmpty()) {
            match.value
        } else {
            calls.addAll(parsed)
            ""
        }
    }
    if (!byPair.contains("DSML")) return byPair
    // Unterminated block: the model was cut off mid-envelope. Take what we can parse.
    return dsmlOpenOnlyRegex.replace(byPair) { match ->
        val parsed = parseCallArray(match.groupValues[1], tools)
        if (parsed.isEmpty()) {
            match.value
        } else {
            calls.addAll(parsed)
            ""
        }
    }
}

/** `TOOL_CALL: {…}` on its own line, possibly repeated. */
private val toolCallMarker = Regex("TOOL_CALL\\s*:", RegexOption.IGNORE_CASE)

private fun extractPrefixedCalls(
    content: String,
    tools: List<Tool>,
    calls: MutableList<ParsedInlineToolCall>,
): String {
    if (!content.contains("TOOL_CALL", ignoreCase = true)) return content
    val out = StringBuilder()
    var pos = 0
    while (pos < content.length) {
        val match = toolCallMarker.find(content, pos) ?: break
        val braceStart = content.indexOf('{', match.range.last + 1)
        if (braceStart < 0) break
        // A lazy regex stops at the first inner "}" and hands back truncated, unparseable
        // JSON, so walk the braces by hand instead.
        val braceEnd = matchingBrace(content, braceStart)
        if (braceEnd < 0) break
        val parsed = parseSingleCall(content.substring(braceStart, braceEnd + 1), tools)
        if (parsed != null) {
            calls.add(parsed)
            out.append(content, pos, match.range.first)
        } else {
            out.append(content, pos, braceEnd + 1)
        }
        pos = braceEnd + 1
    }
    out.append(content, pos, content.length)
    return out.toString()
}

/** Index of the `}` closing the `{` at [openIndex], ignoring braces inside strings. */
private fun matchingBrace(text: String, openIndex: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in openIndex until text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return -1
}

/**
 * A fenced ```json block whose payload is a tool-call envelope rather than prose.
 * This is the shape users kept seeing rendered in the chat as a code block.
 */
private val fencedJsonRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

private fun extractFencedJsonCalls(
    content: String,
    tools: List<Tool>,
    calls: MutableList<ParsedInlineToolCall>,
): String {
    if (!content.contains("```")) return content
    return fencedJsonRegex.replace(content) { match ->
        val parsed = parseEnvelope(match.groupValues[1].trim(), tools)
        if (parsed.isEmpty()) {
            match.value
        } else {
            calls.addAll(parsed)
            ""
        }
    }
}

private val ENVELOPE_KEYS = listOf("tool_call", "tool_calls", "function_call", "functionCall")

/** Pulls a call out of `{ "name": …, "arguments": … }`, tolerating the envelope wrappers. */
private fun parseSingleCall(raw: String, tools: List<Tool>): ParsedInlineToolCall? {
    val obj = runCatching { lenientJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    // Unwrap one level of envelope if present.
    for (key in ENVELOPE_KEYS) {
        val inner = obj[key] ?: continue
        return unwrapCall(inner, tools)
    }
    return buildCall(obj, tools)
}

private fun unwrapCall(element: kotlinx.serialization.json.JsonElement, tools: List<Tool>): ParsedInlineToolCall? {
    val obj = element as? JsonObject ?: return null
    return buildCall(obj, tools)
}

private fun buildCall(obj: JsonObject, tools: List<Tool>): ParsedInlineToolCall? {
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: obj["function"]?.let { fn ->
            (fn as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        ?: return null
    val argsElement = obj["arguments"] ?: obj["parameters"]
        ?: (obj["function"] as? JsonObject)?.get("arguments")
    val argsJson = when (argsElement) {
        null -> "{}"
        is JsonObject -> argsElement.toString()
        is kotlinx.serialization.json.JsonArray -> argsElement.joinToString(prefix = "[", postfix = "]") {
            it.toString()
        }
        else -> argsElement.toString()
    }
    return ParsedInlineToolCall(name = name, arguments = argsJson)
}

/** A bare array of calls, as DSML emits them. */
private fun parseCallArray(raw: String, tools: List<Tool>): List<ParsedInlineToolCall> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    val array = runCatching {
        lenientJson.parseToJsonElement(trimmed) as? kotlinx.serialization.json.JsonArray
    }.getOrNull() ?: return emptyList()
    return array.mapNotNull { buildCall(it as? JsonObject ?: return@mapNotNull null, tools) }
}

/** An object carrying one of the envelope keys, at top level or one level down. */
private fun parseEnvelope(raw: String, tools: List<Tool>): List<ParsedInlineToolCall> {
    val root = runCatching { lenientJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    return when (root) {
        is JsonObject -> {
            val results = mutableListOf<ParsedInlineToolCall>()
            for (key in ENVELOPE_KEYS) {
                val value = root[key] ?: continue
                when (value) {
                    is JsonObject -> buildCall(value, tools)?.let { results.add(it) }
                    is kotlinx.serialization.json.JsonArray -> results.addAll(
                        value.mapNotNull { buildCall(it as? JsonObject ?: return@mapNotNull null, tools) },
                    )
                    is JsonPrimitive -> {}
                    else -> {}
                }
            }
            if (results.isNotEmpty()) {
                results
            } else {
                // A bare {name, arguments} object is a call too.
                buildCall(root, tools)?.let { listOf(it) } ?: emptyList()
            }
        }
        is kotlinx.serialization.json.JsonArray -> parseCallArray(raw, tools)
        else -> emptyList()
    }
}

private fun parseToolCallBlock(inner: String, tools: List<Tool>): ParsedInlineToolCall? {
    if (inner.isEmpty()) return null
    return when {
        inner.startsWith("{") -> parseJsonFlavor(inner)
        inner.contains("<function=") -> parseXmlFlavor(inner, tools)
        else -> null
    }
}

private fun parseJsonFlavor(inner: String): ParsedInlineToolCall? = try {
    val obj = lenientJson.parseToJsonElement(inner).jsonObject
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return null
    val argsElement = obj["arguments"] ?: obj["parameters"]
    val argsJson = when {
        argsElement == null -> "{}"
        argsElement is JsonObject -> argsElement.toString()
        else -> argsElement.toString()
    }
    ParsedInlineToolCall(name = name, arguments = argsJson)
} catch (_: Throwable) {
    null
}

private fun parseXmlFlavor(inner: String, tools: List<Tool>): ParsedInlineToolCall? {
    val funcMatch = functionTagRegex.find(inner) ?: return null
    val name = funcMatch.groupValues[1]
    val body = funcMatch.groupValues[2]
    val schema = tools.firstOrNull { it.schema.name == name }?.schema

    val json = buildJsonObject {
        for (match in parameterTagRegex.findAll(body)) {
            val key = match.groupValues[1]
            val raw = match.groupValues[2].trim()
            val type = schema?.parameters?.get(key)?.type
            put(key, coerceParameterValue(raw, type))
        }
    }
    return ParsedInlineToolCall(name = name, arguments = json.toString())
}

private fun coerceParameterValue(
    raw: String,
    declaredType: String?,
): kotlinx.serialization.json.JsonElement = when (declaredType) {
    "integer" -> raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "number" -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "boolean" -> raw.toBooleanStrictOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "array", "object" -> parseJsonOrNull(raw) ?: JsonPrimitive(raw)

    // No schema hint — keep as string. Numeric-looking values stay strings to match
    // the model's literal output unless the schema explicitly asked for a number.
    else -> JsonPrimitive(raw)
}

private fun parseJsonOrNull(raw: String): kotlinx.serialization.json.JsonElement? = try {
    lenientJson.parseToJsonElement(raw)
} catch (_: Throwable) {
    null
}
