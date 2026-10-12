package com.katya.app.tools

import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolInfo
import com.katya.app.network.tools.ToolSchema
import com.katya.app.sandbox.SshConfigManager
import java.io.File

/**
 * Регистрация SSH-хоста по алиасу.
 *
 * На Android инструмент писал блок в `~/.ssh/config` внутри proot-песочницы —
 * там, откуда команда `ssh` берёт настройки. На десктопе песочницы нет, а
 * `ssh config` читает `~/.ssh/config` пользователя, поэтому пишем именно туда:
 * иначе алиас был бы записан в файл, который никто не открывает, и следующий
 * же вызов `ssh myalias` упал бы с «Host not found».
 *
 * Класс `SshConfigManager` общий — он чистый файловый ввод-вывод, и блоки,
 * которые он пишет, помечены маркерами `# kai:<marker>:start`, так что
 * повторная регистрация того же хоста не плодит дубликаты, а чужую часть
 * конфига не трогает.
 */
object SshConfigureHostTool : Tool {

    /** Конфиг пользователя, а не каталог приложения: его читает системный `ssh`. */
    private val sshConfigFile: File
        get() = File(System.getProperty("user.home"), ".ssh/config")

    override val schema = ToolSchema(
        name = "ssh_configure_host",
        description = """Register a named SSH host alias in ~/.ssh/config so subsequent commands can run `ssh <alias>` instead of repeating user/host/port/identity flags every time.

Write the config ONCE per remote. Then invoke ssh BY the alias: `ssh myalias 'cmd'`, `scp file myalias:`, `sftp myalias`. Bypassing the alias discards every setting the tool just wrote.""",
        parameters = mapOf(
            "alias" to ParameterSchema("string", "Short name for the host, e.g. 'server'. Used as `ssh <alias>`.", true),
            "host" to ParameterSchema("string", "IP address or domain of the server", true),
            "user" to ParameterSchema("string", "SSH user name", true),
            "port" to ParameterSchema("integer", "SSH port (usually 22)", false),
            "identity_file" to ParameterSchema("string", "Path to the private key. Must already exist under ~/.ssh — this tool does NOT create or upload keys.", false),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: Map<String, Any>): Any = try {
        val alias = (args["alias"] as? String)?.trim().orEmpty()
        if (alias.isEmpty()) {
            return mapOf("success" to false, "error" to "alias is required")
        }
        val host = (args["host"] as? String)?.trim().orEmpty()
        if (host.isEmpty()) {
            return mapOf("success" to false, "error" to "host is required")
        }
        val user = (args["user"] as? String)?.trim().orEmpty()
        val port = (args["port"] as? Number)?.toInt() ?: 22
        val identity = (args["identity_file"] as? String)?.trim().orEmpty()

        val identityCheck = validateIdentity(identity)
        if (identityCheck != null) {
            return mapOf("success" to false, "error" to identityCheck)
        }

        // Домашний каталог пользователя, а не каталог приложения: `ssh` читает
        // конфиг только из ~/.ssh, и блок в `~/.katya/.ssh` он бы не увидел.
        val manager = SshConfigManager(File(System.getProperty("user.home")))
        val written = manager.upsertHost(
            alias = alias,
            hostname = host,
            user = user.ifEmpty { "root" },
            port = port,
            identityFile = identity,
        )
        if (!written) {
            return mapOf(
                "success" to false,
                "error" to "could not write ${sshConfigFile.absolutePath}",
            )
        }
        mapOf(
            "success" to true,
            "alias" to alias,
            "config_path" to sshConfigFile.absolutePath,
            "message" to "Host '$alias' written to ${sshConfigFile.absolutePath}. " +
                "Invoke it as `ssh $alias 'cmd'`, `scp file $alias:`, `sftp $alias`.",
        )
    } catch (e: Exception) {
        mapOf("success" to false, "error" to "Failed to configure host: ${e.message}")
    }

    /**
     * Ключ должен уже лежать на диске.
     *
     * Инструмент ничего не создаёт и никуда не кладёт: загрузка ключа на сервер —
     * отдельное действие, о котором пользователь знает, а молчаливая попытка
     * сгенерировать ключ выглядела бы так, будто доступ уже настроен.
     */
    private fun validateIdentity(identity: String): String? {
        if (identity.isEmpty()) return null
        val file = File(identity)
        if (!file.isAbsolute) return "identity_file must be an absolute path"
        if (!file.isFile) return "identity_file does not exist: $identity — place the key there first"
        return null
    }

    val toolInfo = ToolInfo(
        id = "ssh_configure_host",
        name = "Настройка SSH-хоста",
        description = "Записать алиасу блок в ~/.ssh/config, чтобы писать `ssh <алиас>`",
    )
}
