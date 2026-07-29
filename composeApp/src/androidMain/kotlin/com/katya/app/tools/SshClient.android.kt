package com.katya.app.tools

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream

actual class SshClient actual constructor() {
    actual fun executeCommand(
        host: String,
        port: Int,
        user: String,
        pass: String,
        command: String,
    ): String {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, port)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.inputStream = null
            channel.setErrStream(System.err)

            val inputStream: InputStream = channel.inputStream
            channel.connect()

            val output = StringBuilder()
            val buffer = ByteArray(1024)
            while (true) {
                while (inputStream.available() > 0) {
                    val i = inputStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    output.append(String(buffer, 0, i))
                }
                if (channel.isClosed) {
                    break
                }
                try {
                    Thread.sleep(100)
                } catch (ee: Exception) {
                }
            }
            return output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return "SSH Error: ${e.message}"
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}
