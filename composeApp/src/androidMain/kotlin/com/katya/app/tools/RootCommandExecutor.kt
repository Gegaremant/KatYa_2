package com.katya.app.tools

import java.io.BufferedReader
import java.io.InputStreamReader

object RootCommandExecutor {

    /**
     * Checks if the device has root access (su binary is available).
     */
    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_test"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output == "root_test"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a command as root and returns the result (stdout and stderr combined).
     */
    fun executeCommand(command: String): RootCommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            RootCommandResult(
                isSuccess = exitCode == 0,
                output = output.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            RootCommandResult(
                isSuccess = false,
                output = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }
}

data class RootCommandResult(
    val isSuccess: Boolean,
    val output: String,
    val exitCode: Int
)
