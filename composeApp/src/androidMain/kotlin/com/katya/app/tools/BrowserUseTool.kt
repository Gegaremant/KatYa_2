package com.katya.app.tools

import com.katya.app.browser.BrowserAction
import com.katya.app.browser.BrowserActionInput
import com.katya.app.browser.BrowserTabPool
import com.katya.app.browser.ScrollDirection
import com.katya.app.browser.UserAgentProfile
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BrowserUseTool(private val browserTabPool: BrowserTabPool) : Tool {

    override val schema = ToolSchema(
        name = "browser_use",
        description = """Control web browser with up to 3 tabs. Actions: navigate to URL, take screenshot, click elements, type text, get page text, scroll, get page info, execute JavaScript, find elements by selector, hover, get readable content, set user agent, get page backbone (DOM structure), fetch resource, manage tabs (new_tab, close_tab, list_tabs).""".trimIndent(),
        parameters = mapOf(
            "action" to ParameterSchema("string", "The browser action to perform", true, JsonObject(mapOf("enum" to JsonArray(BrowserAction.allValues.map { JsonPrimitive(it) })))),
            "url" to ParameterSchema("string", "URL to navigate to or fetch (for navigate/fetch/new_tab)", false),
            "selector" to ParameterSchema("string", "CSS selector for element interaction (click/type/hover/find_elements/get_text/scroll)", false),
            "text" to ParameterSchema("string", "Text to type into the selected element", false),
            "coordinate_x" to ParameterSchema("integer", "X coordinate for click-by-position", false),
            "coordinate_y" to ParameterSchema("integer", "Y coordinate for click-by-position", false),
            "direction" to ParameterSchema("string", "Scroll direction", false, JsonObject(mapOf("enum" to JsonArray(listOf("up", "down").map { JsonPrimitive(it) })))),
            "amount" to ParameterSchema("integer", "Scroll amount in pixels (default 500)", false),
            "script" to ParameterSchema("string", "JavaScript code to execute (for execute_js). The script runs inside an async function wrapper — await and top-level return are both supported.", false),
            "user_agent" to ParameterSchema("string", "User agent profile to switch to", false, JsonObject(mapOf("enum" to JsonArray(listOf("desktop_chrome", "mobile_chrome").map { JsonPrimitive(it) })))),
            "max_depth" to ParameterSchema("integer", "Maximum DOM tree depth for get_backbone (default 5)", false),
            "tab_id" to ParameterSchema("integer", "Tab ID for tab management actions", false),
            "full_page" to ParameterSchema("boolean", "When true on screenshot, capture the entire scrollable page by temporarily stretching the viewport to document.scrollHeight (height-capped at 32768 px). Default false captures only the current viewport.", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val actionStr = args["action"]?.toString() ?: return mapOf("success" to false, "error" to "action is required")
        val action = BrowserAction.fromString(actionStr) ?: return mapOf("success" to false, "error" to "invalid action")

        val input = BrowserActionInput(
            action = action,
            url = args["url"]?.toString(),
            selector = args["selector"]?.toString(),
            text = args["text"]?.toString(),
            coordinateX = (args["coordinate_x"] as? Number)?.toInt(),
            coordinateY = (args["coordinate_y"] as? Number)?.toInt(),
            direction = args["direction"]?.toString()?.let { ScrollDirection.fromString(it) },
            amount = (args["amount"] as? Number)?.toInt(),
            script = args["script"]?.toString(),
            userAgent = args["user_agent"]?.toString()?.let { UserAgentProfile.fromString(it) },
            maxDepth = (args["max_depth"] as? Number)?.toInt(),
            tabId = (args["tab_id"] as? Number)?.toInt(),
            fullPage = args["full_page"] as? Boolean ?: false,
        )

        val result = browserTabPool.execute(input, false)

        return mapOf(
            "success" to result.success,
            "text" to result.text,
            "tab_id" to (result.tabId ?: -1),
        )
    }
}
