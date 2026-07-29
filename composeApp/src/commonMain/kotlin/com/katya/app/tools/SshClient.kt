package com.katya.app.tools

expect class SshClient() {
    fun executeCommand(
        host: String,
        port: Int,
        user: String,
        pass: String,
        command: String,
    ): String
}
