package com.katya.app.tools

/**
 * Root execution API used by the agent tools. Kept as a thin, stable facade
 * over [ShellExecutor] so callers get timeouts and safe stream draining without
 * re-implementing `su` handling.
 */
object RootCommandExecutor {

    /**
     * Checks if the device has root access (su binary is available and returns uid=0).
     */
    fun hasRootAccess(): Boolean = ShellExecutor.hasRootAccess()

    /**
     * Executes a command as root and returns the result (stdout and stderr combined).
     */
    fun executeCommand(command: String): RootCommandResult {
        val safeCommand = command.replace("\"", "\\\"")
        RootHelper.logAction("Выполнение команды", safeCommand)
        val result = ShellExecutor.execute(command, useRoot = true)
        return RootCommandResult(
            isSuccess = result.isSuccess,
            output = result.output,
            exitCode = result.exitCode,
        )
    }
}

data class RootCommandResult(
    val isSuccess: Boolean,
    val output: String,
    val exitCode: Int,
)
