package com.katya.app.tools

import com.katya.app.data.AppSettings
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema

class ServerMonitoringTool(private val appSettings: AppSettings) : Tool {

    override val schema = ToolSchema(
        name = "server_monitor",
        description = "Connects to the server via SSH and returns current CPU, RAM, and Temperature metrics.",
        parameters = emptyMap(),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val host = appSettings.getServerIp()
        val port = appSettings.getServerPort()
        val user = appSettings.getServerUser()
        val pass = appSettings.getServerPassword()

        if (host.isBlank() || user.isBlank()) {
            return mapOf(
                "success" to false,
                "error" to "SSH credentials are not configured in settings.",
            )
        }

        return try {
            val sshClient = SshClient()
            val cpuInfo = sshClient.executeCommand(host, port, user, pass, "top -bn1 | head -n 5")
            val ramInfo = sshClient.executeCommand(host, port, user, pass, "free -m")
            val tempInfo = sshClient.executeCommand(host, port, user, pass, "sensors || cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")

            val combined = """
                Server Status for $host
                
                --- CPU Info ---
                $cpuInfo
                
                --- RAM Info ---
                $ramInfo
                
                --- Temperature Info ---
                $tempInfo
            """.trimIndent()

            mapOf(
                "success" to true,
                "status_report" to combined,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to "Error executing SSH command: ${e.message}",
            )
        }
    }
}
