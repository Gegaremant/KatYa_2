package com.katya.app.tools

actual class CommandExecutor actual constructor() {
    actual fun executeCommand(command: String, workDir: String?, useRoot: Boolean, isLogAction: Boolean): String {
        if (useRoot && !isLogAction) {
            RootHelper.logAction("Выполнение команды", command.replace("\"", "\\\""))
        }
        val result = ShellExecutor.execute(
            command = command,
            useRoot = useRoot && ShellExecutor.hasRootAccess(),
            workDir = workDir,
            timeoutMs = ShellExecutor.DEFAULT_TIMEOUT_MS,
        )
        if (!result.isSuccess && result.exitCode != 124) {
            return result.output.ifEmpty { "Execution error (exit code ${result.exitCode})." }
        }
        return result.output.ifEmpty { "Command executed successfully (no output)." }
    }

    actual fun isRootAvailable(): Boolean = ShellExecutor.hasRootAccess()
}
