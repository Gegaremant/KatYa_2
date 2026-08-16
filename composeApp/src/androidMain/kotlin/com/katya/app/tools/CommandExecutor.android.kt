package com.katya.app.tools

import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

actual class CommandExecutor actual constructor() {
    actual fun executeCommand(command: String, workDir: String?, useRoot: Boolean): String {
        return try {
            val processBuilder = if (useRoot && isRootAvailable()) {
                ProcessBuilder("su", "-c", command)
            } else {
                ProcessBuilder("sh", "-c", command)
            }

            if (workDir != null) {
                val dir = File(workDir)
                if (dir.exists() && dir.isDirectory) {
                    processBuilder.directory(dir)
                }
            }

            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            
            // Limit execution to 15 seconds to prevent hanging the AI
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return "Error: Command timed out after 15 seconds."
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }
            output.trim().ifEmpty { "Command executed successfully (no output)." }
        } catch (e: Exception) {
            "Execution error: ${e.message}"
        }
    }

    actual fun isRootAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
