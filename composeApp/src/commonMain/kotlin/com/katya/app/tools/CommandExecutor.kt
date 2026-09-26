package com.katya.app.tools

expect class CommandExecutor() {
    /**
     * Executes a local command on the device.
     * @param command The command to execute (e.g., "ls -la")
     * @param workDir Optional working directory path
     * @param useRoot If true, tries to execute the command via `su -c`
     * @return The standard output and error output combined, or error message.
     */
    fun executeCommand(command: String, workDir: String? = null, useRoot: Boolean = false, isLogAction: Boolean = false): String

    /**
     * Checks if the device has root access available via the `su` binary.
     */
    fun isRootAvailable(): Boolean

    /**
     * Same question, but ignoring the cached answer.
     *
     * A root manager that is still waiting for the user to answer its grant dialog
     * can answer "no" first and "yes" a moment later, and the cache would pin that
     * first "no" for its whole TTL. Retry loops that wait for a human must be able
     * to ask again, so they use this instead.
     */
    fun isRootAvailableUncached(): Boolean
}
