@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package com.katya.app.data

import com.katya.app.data.AppSettingsKeys.KEY_CONFIGURED_SERVICES
import com.katya.app.data.AppSettingsKeys.KEY_CURRENT_SERVICE_ID
import com.katya.app.data.AppSettingsKeys.KEY_FREE_FALLBACK_ENABLED
import com.katya.app.data.AppSettingsKeys.KEY_TOOL_PREFIX
import com.katya.app.data.EmailAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun AppSettings.exportToJson(
    toolIds: List<String>,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    conversations: List<Conversation> = emptyList(),
): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    map["version"] = JsonPrimitive(1)

    if (ImportSection.SERVICES in sections) {
        val configuredJson = settings.getString(KEY_CONFIGURED_SERVICES, "")
        if (configuredJson.isNotBlank()) {
            map["configured_services"] = Json.parseToJsonElement(configuredJson)
        }
        map["current_service_id"] = JsonPrimitive(settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id))
        map["free_fallback_enabled"] = JsonPrimitive(isFreeFallbackEnabled())
        map["monitor_overlay_mode"] = JsonPrimitive(settings.getString(AppSettingsKeys.KEY_MONITOR_OVERLAY_MODE, MonitorOverlayMode.SHORT.name))
        map["send_delay_ms"] = JsonPrimitive(getSendDelayMs())
        map["sys_tts_pitch"] = JsonPrimitive(getSysTtsPitch())
        map["sys_tts_rate"] = JsonPrimitive(getSysTtsRate())

        val instances = getConfiguredServiceInstances()
        if (instances.isNotEmpty()) {
            val instanceSettings = JsonArray(
                instances.map { instance ->
                    JsonObject(
                        buildMap {
                            put("instanceId", JsonPrimitive(instance.instanceId))
                            val apiKey = getInstanceApiKey(instance.instanceId)
                            if (apiKey.isNotBlank()) put("api_key", JsonPrimitive(apiKey))
                            val modelId = getInstanceModelId(instance.instanceId)
                            if (modelId.isNotBlank()) put("model_id", JsonPrimitive(modelId))
                            val baseUrl = getInstanceBaseUrl(instance.instanceId)
                            if (baseUrl.isNotBlank()) put("base_url", JsonPrimitive(baseUrl))
                        },
                    )
                },
            )
            map["instance_settings"] = instanceSettings
        }
    }

    if (ImportSection.SOUL in sections) {
        val soul = getSoulText()
        if (soul.isNotBlank()) map["soul_text"] = JsonPrimitive(soul)
    }

    if (ImportSection.MEMORY in sections) {
        map["memory_enabled"] = JsonPrimitive(isMemoryEnabled())
        val memoriesJson = getMemoriesJson()
        if (memoriesJson.isNotBlank() && memoriesJson != "[]") {
            map["agent_memories"] = Json.parseToJsonElement(memoriesJson)
        }
    }

    if (ImportSection.SCHEDULING in sections) {
        map["scheduling_enabled"] = JsonPrimitive(isSchedulingEnabled())
        val tasksJson = getScheduledTasksJson()
        if (tasksJson.isNotBlank() && tasksJson != "[]") {
            map["scheduled_tasks"] = Json.parseToJsonElement(tasksJson)
        }
    }

    if (ImportSection.HEARTBEAT in sections) {
        val heartbeatConfig = getHeartbeatConfigJson()
        if (heartbeatConfig.isNotBlank()) {
            map["heartbeat_config"] = Json.parseToJsonElement(heartbeatConfig)
        }
        val heartbeatPrompt = getHeartbeatPrompt()
        if (heartbeatPrompt.isNotBlank()) map["heartbeat_prompt"] = JsonPrimitive(heartbeatPrompt)
        val heartbeatLog = getHeartbeatLogJson()
        if (heartbeatLog.isNotBlank()) {
            map["heartbeat_log"] = Json.parseToJsonElement(heartbeatLog)
        }
    }

    if (ImportSection.EMAIL in sections) {
        map["email_enabled"] = JsonPrimitive(isEmailEnabled())
        val emailAccountsJson = getEmailAccountsJson()
        if (emailAccountsJson.isNotBlank()) {
            map["email_accounts"] = Json.parseToJsonElement(emailAccountsJson)
            try {
                val accounts = Json.parseToJsonElement(emailAccountsJson).jsonArray
                val passwords = mutableMapOf<String, JsonElement>()
                val syncStates = mutableMapOf<String, JsonElement>()
                for (account in accounts) {
                    val id = account.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                    val password = getEmailPassword(id)
                    if (password.isNotBlank()) passwords[id] = JsonPrimitive(password)
                    val syncState = getEmailSyncStateJson(id)
                    if (syncState.isNotBlank()) syncStates[id] = Json.parseToJsonElement(syncState)
                }
                if (passwords.isNotEmpty()) map["email_passwords"] = JsonObject(passwords)
                if (syncStates.isNotEmpty()) map["email_sync_states"] = JsonObject(syncStates)
            } catch (_: Exception) {
            }
        }
        map["email_poll_interval"] = JsonPrimitive(getEmailPollIntervalMinutes())
    }

    if (ImportSection.SMS in sections) {
        map["sms_enabled"] = JsonPrimitive(isSmsEnabled())
        map["sms_poll_interval"] = JsonPrimitive(getSmsPollIntervalMinutes())
        map["sms_send_enabled"] = JsonPrimitive(isSmsSendEnabled())
    }

    if (ImportSection.SPLINTERLANDS in sections) {
        map["splinterlands_enabled"] = JsonPrimitive(isSplinterlandsEnabled())
        val splinterlandsAccountJson = getSplinterlandsAccountJson()
        if (splinterlandsAccountJson.isNotBlank()) {
            map["splinterlands_account"] = Json.parseToJsonElement(splinterlandsAccountJson)
        }
        val splinterlandsInstanceIdsJson = getSplinterlandsInstanceIdsJson()
        if (splinterlandsInstanceIdsJson.isNotBlank()) {
            map["splinterlands_instance_ids"] = Json.parseToJsonElement(splinterlandsInstanceIdsJson)
        }
        // Posting keys are per-account secrets that live outside the settings
        // snapshot, so they travel here and come back via the extras pass.
        try {
            val accountId = SharedJson.decodeFromString<com.katya.app.splinterlands.SplinterlandsAccount>(splinterlandsAccountJson).id
            val postingKey = getSplinterlandsPostingKey(accountId)
            if (accountId.isNotBlank() && postingKey.isNotBlank()) {
                map["splinterlands_posting_keys"] = JsonObject(mapOf(accountId to JsonPrimitive(postingKey)))
            }
        } catch (_: Exception) {
        }
        val splinterlandsBattleLogJson = getSplinterlandsBattleLogJson()
        if (splinterlandsBattleLogJson.isNotBlank()) {
            map["splinterlands_battle_log"] = Json.parseToJsonElement(splinterlandsBattleLogJson)
        }
    }

    if (ImportSection.TOOLS in sections) {
        val toolStates = mutableMapOf<String, JsonElement>()
        for (toolId in toolIds) {
            toolStates[toolId] = JsonPrimitive(isToolEnabled(toolId))
        }
        if (toolStates.isNotEmpty()) map["tool_overrides"] = JsonObject(toolStates)
    }

    if (ImportSection.MCP in sections) {
        val mcpJson = getMcpServersJson()
        if (mcpJson.isNotBlank()) {
            map["mcp_servers"] = Json.parseToJsonElement(mcpJson)
        }
    }

    if (ImportSection.CONVERSATIONS in sections && conversations.isNotEmpty()) {
        try {
            map["conversations"] = Json.parseToJsonElement(SharedJson.encodeToString(conversations))
        } catch (_: Exception) {
        }
    }

    if (ImportSection.SERVERS in sections) {
        val serverIp = getServerIp()
        if (serverIp.isNotBlank()) map["server_ip"] = JsonPrimitive(serverIp)
        map["server_port"] = JsonPrimitive(getServerPort())
        val serverUser = getServerUser()
        if (serverUser.isNotBlank()) map["server_user"] = JsonPrimitive(serverUser)
        val serverPassword = getServerPassword()
        if (serverPassword.isNotBlank()) map["server_password"] = JsonPrimitive(serverPassword)
        map["tunnel_persistent_reconnect"] = JsonPrimitive(isTunnelPersistentReconnectEnabled())
        map["vless_enabled"] = JsonPrimitive(isVlessEnabled())
        val vlessUri = getVlessUri()
        if (vlessUri.isNotBlank()) map["vless_uri"] = JsonPrimitive(vlessUri)
        map["vless_proxy_profiles"] = JsonPrimitive(getVlessProxyProfilesJson())
        val activeVlessProxyId = getActiveVlessProxyId()
        if (activeVlessProxyId.isNotBlank()) map["active_vless_proxy_id"] = JsonPrimitive(activeVlessProxyId)
        map["active_connection_mode"] = JsonPrimitive(getActiveConnectionMode())
        map["agent_visibility_enabled"] = JsonPrimitive(isAgentVisibilityEnabled())
    }

    // Feedback #12: the complete, key-by-key settings snapshot. The hand-picked
    // fields above stay for older versions to read; the snapshot is what makes a
    // restore on a fresh install actually reproduce the setup.
    map.putAll(exportSnapshotDocument())

    return JsonObject(map)
}

/**
 * Applies a backup.
 *
 * A backup produced by 3.1.3-fix and newer carries a complete settings snapshot
 * ([SNAPSHOT_KEY]); it is the source of truth then, and the hand-migrated fields
 * below are used only for the few things the snapshot deliberately leaves out
 * (conversations, per-account e-mail passwords and sync state, Splinterlands
 * posting keys, the heartbeat log). Older backups take the legacy path.
 */
fun AppSettings.importFromJson(
    json: JsonObject,
    toolIds: List<String>,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    replace: Boolean = true,
): Int {
    if (json[SNAPSHOT_KEY] == null) {
        return importLegacyFromJson(json, toolIds, sections, replace)
    }
    val mode = if (replace) ImportMode.Replace else ImportMode.Merge
    var errors = applySnapshot(json, sections, mode)
    errors += importSnapshotExtras(json, sections, replace)
    return errors
}

/** Everything a settings snapshot does not carry, applied straight from the JSON. */
private fun AppSettings.importSnapshotExtras(
    json: JsonObject,
    sections: Set<ImportSection>,
    replace: Boolean,
): Int {
    var errors = 0
    if (ImportSection.EMAIL in sections) {
        try {
            val accounts = json["email_accounts"]?.jsonArray ?: JsonArray(emptyList())
            for (account in accounts) {
                val id = account.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                json["email_passwords"]?.jsonObject?.get(id)?.jsonPrimitive?.content?.let {
                    setEmailPassword(id, it)
                }
                if (replace) {
                    json["email_sync_states"]?.jsonObject?.get(id)?.let {
                        setEmailSyncStateJson(id, it.toString())
                    }
                }
            }
        } catch (_: Exception) {
            errors++
        }
    }
    if (ImportSection.SPLINTERLANDS in sections && replace) {
        try {
            val accountId = json["splinterlands_account"]?.let { element ->
                runCatching { SharedJson.decodeFromString<com.katya.app.splinterlands.SplinterlandsAccount>(element.toString()) }.getOrNull()
            }?.id
            if (!accountId.isNullOrBlank()) {
                json["splinterlands_posting_keys"]?.jsonObject?.get(accountId)?.jsonPrimitive?.content?.let {
                    setSplinterlandsPostingKey(accountId, it)
                }
            }
        } catch (_: Exception) {
            errors++
        }
    }
    if (ImportSection.CONVERSATIONS in sections) {
        // The database inside the zip is the main carrier for chats, but a
        // plain-JSON backup keeps them inline, so honour that too.
        try {
            val element = json["conversations"]
            if (element != null) {
                val conversations = sanitizeConversations(element)
                setConversationsJson(SharedJson.encodeToString(ConversationsData(conversations = conversations)))
            }
        } catch (_: Exception) {
            errors++
        }
    }
    return errors
}

private fun AppSettings.importLegacyFromJson(
    json: JsonObject,
    toolIds: List<String>,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    replace: Boolean = true,
): Int {
    var errors = 0

    val oldInstances = try {
        getConfiguredServiceInstances()
    } catch (_: Exception) {
        emptyList()
    }

    if (ImportSection.SERVICES in sections) {
        try {
            // Feedback #1: an absent key used to be written as "", which *erased* the
            // service list when importing a file that predates the key. Absent means
            // "this file says nothing about it", not "empty" — only Replace clears it.
            // A `configured_services` stored as a real array (legacy layout) is
            // re-serialised, which is exactly the string the legacy exporter wrote.
            json["configured_services"]?.let { element ->
                settings.putString(
                    KEY_CONFIGURED_SERVICES,
                    if (element is JsonPrimitive) element.content else element.toString(),
                )
            } ?: run { if (replace) settings.remove(KEY_CONFIGURED_SERVICES) }
            json["current_service_id"]?.jsonPrimitive?.content?.let { settings.putString(KEY_CURRENT_SERVICE_ID, it) }
            json["free_fallback_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setFreeFallbackEnabled(it) }

            json["monitor_overlay_mode"]?.jsonPrimitive?.content?.let {
                try {
                    val mode = MonitorOverlayMode.valueOf(it)
                    setMonitorOverlayMode(mode)
                } catch (_: Exception) {}
            }
            json["send_delay_ms"]?.jsonPrimitive?.content?.toLongOrNull()?.let { setSendDelayMs(it) }
            json["sys_tts_pitch"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { setSysTtsPitch(it) }
            json["sys_tts_rate"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { setSysTtsRate(it) }
        } catch (_: Exception) {
            errors++
        }

        try {
            oldInstances.forEach { removeInstanceSettings(it.instanceId) }
            val importedInstances = getConfiguredServiceInstances()
            json["instance_settings"]?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                val instanceId = obj["instanceId"]?.jsonPrimitive?.content ?: return@forEach
                obj["api_key"]?.jsonPrimitive?.content?.let { setInstanceApiKey(instanceId, it) }
                obj["model_id"]?.jsonPrimitive?.content?.let { setInstanceModelId(instanceId, it) }
                obj["base_url"]?.jsonPrimitive?.content?.let { baseUrl ->
                    val service = importedInstances.find { it.instanceId == instanceId }
                        ?.let { Service.fromId(it.serviceId) }
                    if (service == Service.OpenAICompatible && baseUrl.isNotBlank()) {
                        setInstanceBaseUrl(instanceId, ensureBaseUrlHasVersionPath(baseUrl))
                    } else {
                        setInstanceBaseUrl(instanceId, baseUrl)
                    }
                }
            }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        settings.remove(KEY_CONFIGURED_SERVICES)
        settings.putString(KEY_CURRENT_SERVICE_ID, Service.Free.id)
        settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, true)
        oldInstances.forEach { removeInstanceSettings(it.instanceId) }
    }

    if (ImportSection.SOUL in sections) {
        try {
            json["soul_text"]?.jsonPrimitive?.content?.let { setSoulText(it) } ?: run { if (replace) setSoulText("") }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSoulText("")
    }

    if (ImportSection.MEMORY in sections) {
        try {
            json["memory_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setMemoryEnabled(it) }
            val memoriesElement = json["agent_memories"]
            if (memoriesElement != null) {
                setMemoriesJson(sanitizeMemories(memoriesElement))
            } else if (replace) {
                setMemoriesJson("")
            }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setMemoryEnabled(true)
        setMemoriesJson("")
    }

    if (ImportSection.SCHEDULING in sections) {
        try {
            json["scheduling_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setSchedulingEnabled(it) }
            val tasksElement = json["scheduled_tasks"]
            if (tasksElement != null) {
                setScheduledTasksJson(sanitizeScheduledTasks(tasksElement))
            } else if (replace) {
                setScheduledTasksJson("")
            }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSchedulingEnabled(false)
        setScheduledTasksJson("")
    }

    if (ImportSection.HEARTBEAT in sections) {
        try {
            // Feedback #1: absent means "the file says nothing", so an old backup no
            // longer blanks out a heartbeat the user configured themselves.
            json["heartbeat_config"]?.let { setHeartbeatConfigJson(it.toString()) } ?: run { if (replace) setHeartbeatConfigJson("") }
            json["heartbeat_prompt"]?.jsonPrimitive?.content?.let { setHeartbeatPrompt(it) } ?: run { if (replace) setHeartbeatPrompt("") }
            json["heartbeat_log"]?.let { setHeartbeatLogJson(it.toString()) } ?: run { if (replace) setHeartbeatLogJson("") }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setHeartbeatConfigJson("")
        setHeartbeatPrompt("")
        setHeartbeatLogJson("")
    }

    if (ImportSection.EMAIL in sections) {
        try {
            json["email_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setEmailEnabled(it) }
            val importedAccountsJson = json["email_accounts"]?.toString() ?: ""
            if (replace) {
                setEmailAccountsJson(importedAccountsJson)
                json["email_passwords"]?.jsonObject?.forEach { (accountId, pw) ->
                    setEmailPassword(accountId, pw.jsonPrimitive.content)
                }
            } else {
                val currentAccountsJson = getEmailAccountsJson()
                val currentAccounts = if (currentAccountsJson.isNotBlank()) {
                    try {
                        Json.decodeFromString<List<EmailAccount>>(currentAccountsJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                val importedAccounts = if (importedAccountsJson.isNotBlank()) {
                    try {
                        Json.decodeFromString<List<EmailAccount>>(importedAccountsJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                val existingIds = currentAccounts.map { it.id }.toSet()
                val existingEmails = currentAccounts.map { it.email }.toSet()
                val accountsToAdd = importedAccounts.filter { it.id !in existingIds && it.email !in existingEmails }
                val mergedAccounts = currentAccounts + accountsToAdd
                setEmailAccountsJson(Json.encodeToString(mergedAccounts))
                val importedPasswords = json["email_passwords"]?.jsonObject ?: emptyMap()
                accountsToAdd.forEach { account ->
                    importedPasswords[account.id]?.jsonPrimitive?.content?.let { setEmailPassword(account.id, it) }
                }
            }
            json["email_sync_states"]?.jsonObject?.forEach { (accountId, sync) ->
                setEmailSyncStateJson(accountId, sync.toString())
            }
            json["email_poll_interval"]?.jsonPrimitive?.content?.toInt()?.let { setEmailPollIntervalMinutes(it) }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setEmailEnabled(true)
        setEmailAccountsJson("")
        setEmailPollIntervalMinutes(15)
    }

    if (ImportSection.SMS in sections) {
        try {
            setSmsEnabled(json["sms_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
            setSmsPollIntervalMinutes(json["sms_poll_interval"]?.jsonPrimitive?.content?.toInt() ?: 15)
            setSmsSendEnabled(json["sms_send_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSmsEnabled(false)
        setSmsPollIntervalMinutes(15)
        setSmsSendEnabled(false)
    }

    if (ImportSection.SPLINTERLANDS in sections) {
        try {
            json["splinterlands_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setSplinterlandsEnabled(it) }
            json["splinterlands_account"]?.let { setSplinterlandsAccountJson(it.toString()) }
            json["splinterlands_instance_ids"]?.let { setSplinterlandsInstanceIdsJson(it.toString()) }
            json["splinterlands_battle_log"]?.let { setSplinterlandsBattleLogJson(it.toString()) }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSplinterlandsEnabled(false)
        setSplinterlandsAccountJson("")
        setSplinterlandsInstanceIdsJson("")
        setSplinterlandsBattleLogJson("")
    }

    if (ImportSection.TOOLS in sections) {
        try {
            for (toolId in toolIds) {
                settings.remove("$KEY_TOOL_PREFIX$toolId")
            }
            json["tool_overrides"]?.jsonObject?.forEach { (toolId, enabled) ->
                setToolEnabled(toolId, enabled.jsonPrimitive.content.toBoolean())
            }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        for (toolId in toolIds) {
            settings.remove("$KEY_TOOL_PREFIX$toolId")
        }
    }

    if (ImportSection.MCP in sections) {
        try {
            json["mcp_servers"]?.let { setMcpServersJson(it.toString()) } ?: run { if (replace) setMcpServersJson("") }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setMcpServersJson("")
    }

    // Feedback #1: `configured_services` was written as `settings.putString(..., "")`
    // when the key was missing, which *erased* the service list on every import of a
    // file that predates the key. Absent means "this file says nothing", not "empty".
    if (ImportSection.SERVICES in sections) {
        try {
            json["configured_services"]?.let { element ->
                settings.putString(KEY_CONFIGURED_SERVICES, if (element is JsonPrimitive) element.content else element.toString())
            } ?: run { if (replace) settings.remove(KEY_CONFIGURED_SERVICES) }
            json["current_service_id"]?.jsonPrimitive?.content?.let { settings.putString(KEY_CURRENT_SERVICE_ID, it) }
            json["free_fallback_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setFreeFallbackEnabled(it) }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        settings.remove(KEY_CONFIGURED_SERVICES)
        setFreeFallbackEnabled(true)
    }

    if (ImportSection.CONVERSATIONS in sections) {
        try {
            val element = json["conversations"]
            if (element != null) {
                val conversations = sanitizeConversations(element)
                val wrapped = SharedJson.encodeToString(ConversationsData(conversations = conversations))
                setConversationsJson(wrapped)
            } else if (replace) {
                setConversationsJson("")
            }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setConversationsJson("")
    }

    if (ImportSection.SERVERS in sections) {
        try {
            // Feedback #1: same rule as the services list — a key the file does not
            // carry must not be turned into an empty value. An old backup that simply
            // predates `vless_uri` used to wipe the tunnel the user had configured.
            json["server_ip"]?.let { setServerIp(legacyText(it)) }
            json["server_port"]?.let { legacyText(it).toIntOrNull()?.let { port -> setServerPort(port) } }
            json["server_user"]?.let { setServerUser(legacyText(it)) }
            json["server_password"]?.let { setServerPassword(legacyText(it)) }
            json["tunnel_persistent_reconnect"]?.let { setTunnelPersistentReconnectEnabled(legacyBool(it)) }
            json["vless_enabled"]?.let { setVlessEnabled(legacyBool(it)) }
            json["vless_uri"]?.let { setVlessUri(legacyText(it)) }
            json["vless_proxy_profiles"]?.jsonPrimitive?.content?.let { setVlessProxyProfilesJson(it) }
            json["active_vless_proxy_id"]?.jsonPrimitive?.content?.let { setActiveVlessProxyId(it) }
            json["active_connection_mode"]?.jsonPrimitive?.content?.let { setActiveConnectionMode(it) }
            json["agent_visibility_enabled"]?.jsonPrimitive?.content?.toBoolean()?.let { setAgentVisibilityEnabled(it) }
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setServerIp("")
        setServerPort(22)
        setServerUser("")
        setServerPassword("")
        setTunnelPersistentReconnectEnabled(false)
        setVlessEnabled(false)
        setVlessUri("")
        setVlessProxyProfilesJson("[]")
        setActiveVlessProxyId("")
        setActiveConnectionMode("LOCAL")
        setAgentVisibilityEnabled(true)
    }

    return errors
}

/**
 * Reads a scalar out of a backup field of any shape.
 *
 * Feedback #1: the legacy exporter wrote some settings as real JSON structures and
 * some as strings, so a reader that assumes `.jsonPrimitive` throws on perfectly
 * valid older files. These two helpers accept both and never throw.
 */
private fun legacyText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> element.content
    else -> element.toString()
}

/** A boolean field of a legacy backup. Anything unparseable reads as `false`. */
private fun legacyBool(element: JsonElement): Boolean = when (element) {
    is JsonPrimitive -> element.content.toBooleanStrictOrNull() ?: false
    else -> false
}

private fun sanitizeScheduledTasks(element: JsonElement): String {
    val array = try {
        element.jsonArray
    } catch (_: Exception) {
        return "[]"
    }
    val now = Clock.System.now().toEpochMilliseconds()
    val tasks = array.mapNotNull { item ->
        try {
            SharedJson.decodeFromString<ScheduledTask>(item.toString())
        } catch (_: Exception) {
            try {
                val obj = item.jsonObject
                ScheduledTask(
                    id = obj["id"]?.jsonPrimitive?.content ?: Uuid.random().toString(),
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    prompt = obj["prompt"]?.jsonPrimitive?.content ?: "",
                    scheduledAtEpochMs = obj["scheduledAtEpochMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                    createdAtEpochMs = obj["createdAtEpochMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                    cron = obj["cron"]?.jsonPrimitive?.content,
                    lastResult = obj["lastResult"]?.jsonPrimitive?.content,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
    return SharedJson.encodeToString(tasks)
}

private fun sanitizeMemories(element: JsonElement): String {
    val array = try {
        element.jsonArray
    } catch (_: Exception) {
        return "[]"
    }
    val now = Clock.System.now().toEpochMilliseconds()
    val memories = array.mapNotNull { item ->
        try {
            SharedJson.decodeFromString<MemoryEntry>(item.toString())
        } catch (_: Exception) {
            try {
                val obj = item.jsonObject
                MemoryEntry(
                    key = obj["key"]?.jsonPrimitive?.content ?: Uuid.random().toString(),
                    content = obj["content"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                    category = obj["category"]?.jsonPrimitive?.content?.let { name ->
                        try {
                            MemoryCategory.valueOf(name)
                        } catch (_: Exception) {
                            MemoryCategory.GENERAL
                        }
                    } ?: MemoryCategory.GENERAL,
                    hitCount = obj["hitCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                    source = obj["source"]?.jsonPrimitive?.content,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
    return SharedJson.encodeToString(memories)
}

private fun sanitizeConversations(element: JsonElement): List<Conversation> {
    val array = try {
        element.jsonArray
    } catch (_: Exception) {
        return emptyList()
    }
    return array.mapNotNull { item ->
        try {
            SharedJson.decodeFromString<Conversation>(item.toString())
        } catch (_: Exception) {
            null
        }
    }
}
