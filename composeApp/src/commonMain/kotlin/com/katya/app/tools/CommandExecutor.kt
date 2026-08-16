package com.katya.app.tools

expect class CommandExecutor() {
    /**
     * Executes a local command on the device.
     * @param command The command to execute (e.g., "ls -la")
     * @param workDir Optional working directory path
     * @param useRoot If true, tries to execute the command via `su -c`
     * @return The standard output and error output combined, or error message.
     */
    fun executeCommand(command: String, workDir: String? = null, useRoot: Boolean = false): String

    /**
     * Checks if the device has root access available via the `su` binary.
     */
    fun isRootAvailable(): Boolean
}
