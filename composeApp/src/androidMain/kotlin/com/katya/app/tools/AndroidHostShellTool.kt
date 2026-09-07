package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AndroidHostShellTool : Tool {
    override val schema = ToolSchema(
        name = "host_shell_command",
        description = "Execute a shell command directly on the Android host OS (outside the sandbox). Uses 'su' if available for root access, otherwise falls back to 'sh'. Use this for taking screenshots (screencap), tapping (input tap), or accessing host files.",
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute", true),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any = withContext(Dispatchers.IO) {
        val command = args["command"]?.toString()
            ?: return@withContext mapOf("success" to false, "error" to "Command is required")

        // Try root first; fall back to plain sh when the `su` binary is missing
        // (for example on unrooted devices, `su` does not exist at all).
        var result = ShellExecutor.execute(command, useRoot = true)
        if (!result.isSuccess && (
                result.output.contains("Cannot run program \"su\"") ||
                    result.output.contains("not found") || result.output.contains("Permission denied")
                )
        ) {
            result = ShellExecutor.execute(command, useRoot = false)
        }

        mapOf(
            "success" to result.isSuccess,
            "exit_code" to result.exitCode,
            "stdout" to result.output,
            "stderr" to "",
        )
    }
    val toolInfo = ToolInfo(
        id = "host_shell_command",
        name = "Android Shell Command",
        description = "Execute a shell command directly on the Android host OS",
        nameRes = null, // Or define strings in string.xml
        descriptionRes = null,
    )
}
