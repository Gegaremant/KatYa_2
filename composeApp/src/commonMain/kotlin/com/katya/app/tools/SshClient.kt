package com.katya.app.tools

expect class SshClient() {
    fun executeCommand(
        host: String,
        port: Int,
        user: String,
        pass: String,
        command: String,
    ): String

    fun uploadFile(
        host: String,
        port: Int,
        user: String,
        pass: String,
        localPath: String,
        remotePath: String
    ): String

    fun downloadFile(
        host: String,
        port: Int,
        user: String,
        pass: String,
        remotePath: String,
        localPath: String
    ): String
}
