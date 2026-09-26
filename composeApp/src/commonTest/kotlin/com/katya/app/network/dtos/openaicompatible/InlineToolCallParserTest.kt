package com.katya.app.network.dtos.openaicompatible

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Feedback #9/#13: a tool call that arrives as text was rendered to the user as a code
 * block ("the UI shows code with a tool call in it and nothing happens on the phone").
 * FreeDeepseekAPI documents four fallback shapes; every one of them has to turn into a
 * real call, and none of them may be left behind in the visible text.
 */
class InlineToolCallParserTest {
    private val tools = listOf(
        object : Tool {
            override val schema = ToolSchema(
                name = "echo",
                description = "echo back",
                parameters = mapOf("a" to ParameterSchema("integer", "a", false)),
            )

            override suspend fun execute(args: Map<String, Any>): Any = args
        },
    )

    @Test
    fun `keeps plain prose untouched`() {
        val text = "Просто ответ без инструментов."
        val result = extractInlineToolCalls(text, tools)
        assertTrue(result.calls.isEmpty())
        assertEquals(text, result.cleanedText)
    }

    @Test
    fun `parses the hermes xml block`() {
        val text = "Сейчас посмотрю.\n<tool_call>{\"name\":\"echo\",\"arguments\":{\"a\":1}}</tool_call>"
        val result = extractInlineToolCalls(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("echo", result.calls.first().name)
        assertTrue(result.cleanedText.contains("Сейчас посмотрю."))
        assertTrue(!result.cleanedText.contains("tool_call"))
    }

    @Test
    fun `parses a TOOL_CALL prefixed json`() {
        val text = "Готово.\nTOOL_CALL: {\"name\":\"echo\",\"arguments\":{\"q\":\"hi\"}}"
        val result = extractInlineToolCalls(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("echo", result.calls.first().name)
        assertTrue(result.cleanedText.contains("Готово."))
        assertTrue(!result.cleanedText.contains("TOOL_CALL"))
    }

    @Test
    fun `parses a fenced json block with a tool_call envelope`() {
        // This is the exact shape users reported seeing rendered in the chat.
        val text = "Вот ответ:\n```json\n{\"tool_call\":{\"name\":\"echo\",\"arguments\":{\"a\":2}}}\n```\nСделано."
        val result = extractInlineToolCalls(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("echo", result.calls.first().name)
        assertTrue(result.cleanedText.contains("Вот ответ:"))
        assertTrue(result.cleanedText.contains("Сделано."))
        assertTrue(!result.cleanedText.contains("```"), "the code block must not reach the UI")
    }

    @Test
    fun `parses a fenced json block with a tool_calls array envelope`() {
        val text = "```json\n{\"tool_calls\":[{\"name\":\"echo\",\"arguments\":{}}]}\n```"
        val result = extractInlineToolCalls(text, tools)
        assertEquals(1, result.calls.size)
        assertTrue(!result.cleanedText.contains("```"))
    }

    @Test
    fun `parses a dsml tool_calls envelope`() {
        val text = "Работаю.\n<｜DSML｜tool_calls>[{\"name\":\"echo\",\"arguments\":{\"x\":1}}]</｜DSML｜tool_calls>"
        val result = extractInlineToolCalls(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("echo", result.calls.first().name)
        assertTrue(result.cleanedText.contains("Работаю."))
        assertTrue(!result.cleanedText.contains("DSML"))
    }

    @Test
    fun `leaves an ordinary fenced code block alone`() {
        val text = "Вот пример:\n```json\n{\"a\":1}\n```"
        val result = extractInlineToolCalls(text, tools)
        assertTrue(result.calls.isEmpty(), "a plain json snippet is not a tool call")
        assertTrue(result.cleanedText.contains("```"), "and it must stay visible")
    }

    @Test
    fun `leaves an unparseable tool_call block visible rather than swallowing it`() {
        val text = "<tool_call>какая-то ерунда</tool_call>"
        val result = extractInlineToolCalls(text, tools)
        assertTrue(result.calls.isEmpty())
        assertTrue(result.cleanedText.contains("какая-то ерунда"))
    }
}
