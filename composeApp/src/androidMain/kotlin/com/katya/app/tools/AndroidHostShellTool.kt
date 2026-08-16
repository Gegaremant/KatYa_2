package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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

        try {
            // First try with root (su)
            val process = try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } catch (e: Exception) {
                // Fallback to standard sh if su is not found
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitCode = process.waitFor()

            mapOf(
                "success" to (exitCode == 0),
                "exit_code" to exitCode,
                "stdout" to output.toString().trim(),
                "stderr" to errorOutput.toString().trim(),
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Execution failed: ${e.message}")
        }
    }
    val toolInfo = ToolInfo(
        id = "host_shell_command",
        name = "Android Shell Command",
        description = "Execute a shell command directly on the Android host OS",
        nameRes = null, // Or define strings in string.xml
        descriptionRes = null,
    )
}
