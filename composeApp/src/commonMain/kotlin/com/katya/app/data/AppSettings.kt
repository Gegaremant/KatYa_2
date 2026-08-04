package com.katya.app.data

import com.katya.app.defaultUiScale
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ImportSection {
    SERVICES,
    SOUL,
    MEMORY,
    SCHEDULING,
    HEARTBEAT,
    EMAIL,
    SMS,
    SPLINTERLANDS,
    TOOLS,
    MCP,
    CONVERSATIONS,
    MODELS,
    SERVERS,
}

enum class ThemeMode {
    System,
    Light,
    Dark,
    OledBlack,
}

enum class MonitorOverlayMode {
    OFF,
    SHORT,
    FULL,
}

enum class VoiceUiMode {
    FULL_SCREEN,
    BOTTOM_SHEET,
}

/**
 * Stricter than [detectImportSections]: only includes sections that contain actual user data,
 * skipping ones that exist purely because of default feature-toggle flags (e.g. `sms_enabled = false`,
 * `splinterlands_enabled = false`, `mcp_servers = []`). Used to drive the Export preview dialog.
 */
fun detectExportableSections(json: JsonObject): Map<ImportSection, String?> {
    val sections = mutableMapOf<ImportSection, String?>()

    val configured = json["configured_services"]?.jsonArray
    if (configured != null && configured.isNotEmpty()) {
        sections[ImportSection.SERVICES] = "${configured.size}"
    } else if (json["current_service_id"] != null) {
        sections[ImportSection.SERVICES] = null
    }

    if (json["soul_text"] != null) {
        sections[ImportSection.SOUL] = null
    }

    if (json["memory_enabled"] != null || json["agent_memories"] != null) {
        val memories = json["agent_memories"]?.jsonArray
        sections[ImportSection.MEMORY] = memories?.size?.toString() ?: "0"
    }

    if (json["scheduling_enabled"] != null || json["scheduled_tasks"] != null) {
        val tasks = json["scheduled_tasks"]?.jsonArray
        sections[ImportSection.SCHEDULING] = tasks?.size?.toString() ?: "0"
    }

    val heartbeatHasPrompt = json["heartbeat_prompt"] != null
    val heartbeatHasConfig = json["heartbeat_config"] != null
    val heartbeatHasLog = json["heartbeat_log"]?.jsonArray?.isNotEmpty() == true
    if (heartbeatHasPrompt || heartbeatHasConfig || heartbeatHasLog) {
        sections[ImportSection.HEARTBEAT] = null
    }

    val emails = json["email_accounts"]?.jsonArray
    if (emails != null && emails.isNotEmpty()) {
        sections[ImportSection.EMAIL] = "${emails.size}"
    } else if (json["email_enabled"] != null) {
        sections[ImportSection.EMAIL] = null
    }

    val smsEnabled = json["sms_enabled"]?.jsonPrimitive?.content?.toBoolean() == true
    val smsSendEnabled = json["sms_send_enabled"]?.jsonPrimitive?.content?.toBoolean() == true
    if (smsEnabled || smsSendEnabled) {
        sections[ImportSection.SMS] = null
    }

    if (json["splinterlands_account"] != null) {
        sections[ImportSection.SPLINTERLANDS] = null
    }

    val toolOverrides = json["tool_overrides"]?.jsonObject
    if (toolOverrides != null && toolOverrides.isNotEmpty()) {
        val enabled = toolOverrides.count { (_, v) ->
            try {
                v.jsonPrimitive.content.toBoolean()
            } catch (_: Exception) {
                false
            }
        }
        sections[ImportSection.TOOLS] = "$enabled"
    }

    val mcp = json["mcp_servers"]?.jsonArray
    if (mcp != null && mcp.isNotEmpty()) {
        sections[ImportSection.MCP] = "${mcp.size}"
    } else if (json["mcp_servers"] != null) {
        sections[ImportSection.MCP] = "0"
    }

    val conversations = json["conversations"]?.jsonArray
    if (conversations != null && conversations.isNotEmpty()) {
        sections[ImportSection.CONVERSATIONS] = "${conversations.size}"
    }

    if (json["server_ip"] != null) {
        sections[ImportSection.SERVERS] = null
    }

    return sections
}

fun detectImportSections(json: JsonObject): Map<ImportSection, String?> {
    val sections = mutableMapOf<ImportSection, String?>()
    if (json["configured_services"] != null || json["current_service_id"] != null || json["free_fallback_enabled"] != null || json["instance_settings"] != null) {
        val count = json["configured_services"]?.jsonArray?.size
        sections[ImportSection.SERVICES] = count?.let { "$it" }
    }
    if (json["soul_text"] != null) {
        sections[ImportSection.SOUL] = null
    }
    if (json["memory_enabled"] != null || json["agent_memories"] != null) {
        val count = json["agent_memories"]?.jsonArray?.size
        sections[ImportSection.MEMORY] = count?.let { "$it" }
    }
    if (json["scheduling_enabled"] != null || json["scheduled_tasks"] != null) {
        val count = json["scheduled_tasks"]?.jsonArray?.size
        sections[ImportSection.SCHEDULING] = count?.let { "$it" }
    }
    if (json["heartbeat_config"] != null || json["heartbeat_prompt"] != null || json["heartbeat_log"] != null) {
        sections[ImportSection.HEARTBEAT] = null
    }
    if (json["email_enabled"] != null || json["email_accounts"] != null) {
        val count = json["email_accounts"]?.jsonArray?.size
        sections[ImportSection.EMAIL] = count?.let { "$it" }
    }
    if (json["sms_enabled"] != null || json["sms_poll_interval"] != null || json["sms_send_enabled"] != null) {
        sections[ImportSection.SMS] = null
    }
    if (json["splinterlands_enabled"] != null || json["splinterlands_account"] != null) {
        sections[ImportSection.SPLINTERLANDS] = null
    }
    if (json["tool_overrides"] != null) {
        val enabled = json["tool_overrides"]?.jsonObject?.count { (_, v) ->
            try {
                v.jsonPrimitive.content.toBoolean()
            } catch (_: Exception) {
                false
            }
        }
        sections[ImportSection.TOOLS] = enabled?.let { "$it" }
    }
    if (json["mcp_servers"] != null) {
        val count = json["mcp_servers"]?.jsonArray?.size
        sections[ImportSection.MCP] = count?.let { "$it" }
    }
    if (json["conversations"] != null) {
        val count = try {
            json["conversations"]?.jsonArray?.size
        } catch (_: Exception) {
            null
        }
        sections[ImportSection.CONVERSATIONS] = count?.let { "$it" }
    }
    if (json["server_ip"] != null) {
        sections[ImportSection.SERVERS] = null
    }
    return sections
}

data class ServiceInstance(
    val instanceId: String,
    val serviceId: String,
)

class AppSettings(internal val settings: Settings) {

    // App open tracking
    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(KEY_APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(KEY_APP_OPENS, newCount)
        return newCount
    }

    // Tool enable/disable settings
    fun isToolEnabled(toolId: String, defaultEnabled: Boolean = true): Boolean = settings.getBoolean("$KEY_TOOL_PREFIX$toolId", defaultEnabled)

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        settings.putBoolean("$KEY_TOOL_PREFIX$toolId", enabled)
    }

    fun getConversationsJson(): String? = settings.getStringOrNull(KEY_CONVERSATIONS)

    fun setConversationsJson(json: String) {
        settings.putString(KEY_CONVERSATIONS, json)
    }

    fun removeConversationsJson() {
        settings.remove(KEY_CONVERSATIONS)
    }

    fun getCurrentConversationId(): String? = settings.getStringOrNull(KEY_CURRENT_CONVERSATION_ID)

    fun setCurrentConversationId(id: String?) {
        if (id == null) {
            settings.remove(KEY_CURRENT_CONVERSATION_ID)
        } else {
            settings.putString(KEY_CURRENT_CONVERSATION_ID, id)
        }
    }

    fun getCurrentInteractiveMode(): Boolean = settings.getBoolean(KEY_CURRENT_INTERACTIVE_MODE, false)

    fun setCurrentInteractiveMode(enabled: Boolean) {
        settings.putBoolean(KEY_CURRENT_INTERACTIVE_MODE, enabled)
    }

    fun isCurrentConversationMigrated(): Boolean = settings.getBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, false)

    fun markCurrentConversationMigrated() {
        settings.putBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, true)
    }

    fun getEncryptionKey(): ByteArray? {
        val encoded = settings.getStringOrNull(KEY_ENCRYPTION_KEY) ?: return null
        return try {
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.decode(encoded)
        } catch (_: Exception) {
            null
        }
    }

    // Free fallback
    fun isFreeFallbackEnabled(): Boolean = settings.getBoolean(KEY_FREE_FALLBACK_ENABLED, true)

    fun setFreeFallbackEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, enabled)
    }

    fun getFreeMode(): FreeMode {
        val stored = settings.getStringOrNull(KEY_FREE_MODE) ?: return FreeMode.FAST
        return FreeMode.entries.find { it.name == stored } ?: FreeMode.FAST
    }

    fun setFreeMode(mode: FreeMode) {
        settings.putString(KEY_FREE_MODE, mode.name)
    }

    fun isFreeServicePrimary(): Boolean = settings.getBoolean(KEY_FREE_SERVICE_PRIMARY, false)

    // Server Monitoring
    fun getServerIp(): String = settings.getString(KEY_SERVER_IP, "")
    fun setServerIp(ip: String) {
        settings.putString(KEY_SERVER_IP, ip)
    }

    fun getServerPort(): Int = settings.getInt(KEY_SERVER_PORT, 22)
    fun setServerPort(port: Int) {
        settings.putInt(KEY_SERVER_PORT, port)
    }

    fun getAutoBackupDirectory(): String = settings.getString("auto_backup_directory", "")
    fun setAutoBackupDirectory(path: String) {
        settings.putString("auto_backup_directory", path)
    }

    fun getServerUser(): String = settings.getString(KEY_SERVER_USER, "")
    fun setServerUser(user: String) {
        settings.putString(KEY_SERVER_USER, user)
    }

    fun getServerPassword(): String = settings.getString(KEY_SERVER_PASSWORD, "")
    fun setServerPassword(password: String) {
        settings.putString(KEY_SERVER_PASSWORD, password)
    }

    fun isTunnelPersistentReconnectEnabled(): Boolean = settings.getBoolean(KEY_TUNNEL_PERSISTENT_RECONNECT, false)
    fun setTunnelPersistentReconnectEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_TUNNEL_PERSISTENT_RECONNECT, enabled)
    }

    fun getWakeWord(): String = settings.getString(KEY_WAKE_WORD, "привет катя")
    fun setWakeWord(word: String) {
        settings.putString(KEY_WAKE_WORD, word)
    }

    fun isVoiceResponseEnabled(): Boolean = settings.getBoolean(KEY_VOICE_RESPONSE_ENABLED, true)

    fun setWakeWordEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_WAKE_WORD_ENABLED, enabled)
    }

    fun isWakeWordEnabled(): Boolean = settings.getBoolean(KEY_WAKE_WORD_ENABLED, false)

    fun setWakeWordModelLang(lang: String) {
        settings.putString(KEY_WAKE_WORD_MODEL_LANG, lang)
    }

    fun getWakeWordModelLang(): String = settings.getString(KEY_WAKE_WORD_MODEL_LANG, "ru")

    fun setWakeWordTrigger(trigger: String) {
        settings.putString(KEY_WAKE_WORD_TRIGGER, trigger)
    }

    fun getWakeWordTrigger(): String = settings.getString(KEY_WAKE_WORD_TRIGGER, "Привет Катя")

    fun setWakeWordVibration(enabled: Boolean) {
        settings.putBoolean(KEY_WAKE_WORD_VIBRATION, enabled)
    }

    fun isWakeWordVibrationEnabled(): Boolean = settings.getBoolean(KEY_WAKE_WORD_VIBRATION, true)

    fun setWakeWordSound(enabled: Boolean) {
        settings.putBoolean(KEY_WAKE_WORD_SOUND, enabled)
    }

    fun isWakeWordSoundEnabled(): Boolean = settings.getBoolean(KEY_WAKE_WORD_SOUND, true)
    fun setVoiceResponseEnabled(enabled: Boolean) = settings.putBoolean(KEY_VOICE_RESPONSE_ENABLED, enabled)

    fun isWatchIntegrationEnabled(): Boolean = settings.getBoolean(KEY_WATCH_INTEGRATION_ENABLED, false)
    fun setWatchIntegrationEnabled(enabled: Boolean) = settings.putBoolean(KEY_WATCH_INTEGRATION_ENABLED, enabled)

    fun setFreeServicePrimary(primary: Boolean) {
        settings.putBoolean(KEY_FREE_SERVICE_PRIMARY, primary)
    }

    // Soul (system prompt)
    fun getSoulText(): String = settings.getString(KEY_SOUL, "")

    fun setSoulText(text: String) {
        settings.putString(KEY_SOUL, text)
    }

    // Memory
    fun isMemoryEnabled(): Boolean = settings.getBoolean(KEY_MEMORY_ENABLED, true)

    fun setMemoryEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_MEMORY_ENABLED, enabled)
    }

    fun getMemoryInstructions(): String = settings.getString(KEY_MEMORY_INSTRUCTIONS, DEFAULT_MEMORY_INSTRUCTIONS)

    // Agent memories
    fun getMemoriesJson(): String = settings.getString(KEY_AGENT_MEMORIES, "[]")

    fun setMemoriesJson(json: String) {
        settings.putString(KEY_AGENT_MEMORIES, json)
    }

    // Scheduling
    fun isSchedulingEnabled(): Boolean = settings.getBoolean(KEY_SCHEDULING_ENABLED, true)

    fun setSchedulingEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SCHEDULING_ENABLED, enabled)
    }

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean = settings.getBoolean(KEY_DYNAMIC_UI_ENABLED, true)

    fun setDynamicUiEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DYNAMIC_UI_ENABLED, enabled)
    }

    private val _themeModeFlow = MutableStateFlow(loadInitialThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

    fun getThemeMode(): ThemeMode = _themeModeFlow.value

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(KEY_THEME_MODE, mode.name)
        _themeModeFlow.value = mode
    }

    // Monitor Overlay Mode
    private val _monitorOverlayModeFlow = MutableStateFlow(loadInitialMonitorOverlayMode())
    val monitorOverlayModeFlow: StateFlow<MonitorOverlayMode> = _monitorOverlayModeFlow

    private val _voiceUiModeFlow = MutableStateFlow(getVoiceUiMode())
    val voiceUiModeFlow: StateFlow<VoiceUiMode> = _voiceUiModeFlow

    fun getVoiceUiMode(): VoiceUiMode {
        val modeStr = settings.getString("voice_ui_mode", VoiceUiMode.FULL_SCREEN.name)
        return try {
            VoiceUiMode.valueOf(modeStr)
        } catch (e: Exception) {
            VoiceUiMode.FULL_SCREEN
        }
    }

    fun setVoiceUiMode(mode: VoiceUiMode) {
        settings.putString("voice_ui_mode", mode.name)
        _voiceUiModeFlow.value = mode
    }

    fun getMonitorOverlayMode(): MonitorOverlayMode = _monitorOverlayModeFlow.value

    fun setMonitorOverlayMode(mode: MonitorOverlayMode) {
        settings.putString(KEY_MONITOR_OVERLAY_MODE, mode.name)
        _monitorOverlayModeFlow.value = mode
    }

    private fun loadInitialMonitorOverlayMode(): MonitorOverlayMode {
        val raw = settings.getString(KEY_MONITOR_OVERLAY_MODE, "")
        if (raw.isNotEmpty()) {
            return try {
                MonitorOverlayMode.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                MonitorOverlayMode.SHORT
            }
        }
        return MonitorOverlayMode.SHORT
    }

    private fun loadInitialThemeMode(): ThemeMode {
        val raw = settings.getString(KEY_THEME_MODE, "")
        if (raw.isNotEmpty()) {
            return try {
                ThemeMode.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                ThemeMode.System
            }
        }
        // Migrate the legacy boolean OLED toggle: true → OledBlack, false → System.
        return if (settings.getBoolean(KEY_OLED_MODE_ENABLED, false)) ThemeMode.OledBlack else ThemeMode.System
    }

    // Daemon mode
    fun isDaemonEnabled(): Boolean = settings.getBoolean(KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DAEMON_ENABLED, enabled)
    }

    // Root access dialog
    fun hasRequestedRoot(): Boolean = settings.getBoolean(KEY_HAS_REQUESTED_ROOT, false)

    fun setRequestedRoot(requested: Boolean) {
        settings.putBoolean(KEY_HAS_REQUESTED_ROOT, requested)
    }

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean = settings.getBoolean(KEY_SANDBOX_ENABLED, true)

    fun setSandboxEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SANDBOX_ENABLED, enabled)
    }

    // Agent Visibility (showing operations in UI)
    fun isAgentVisibilityEnabled(): Boolean = settings.getBoolean(KEY_AGENT_VISIBILITY_ENABLED, true)

    fun setAgentVisibilityEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_AGENT_VISIBILITY_ENABLED, enabled)
    }

    // VLESS Proxy
    fun isVlessEnabled(): Boolean = settings.getBoolean(KEY_VLESS_ENABLED, false)

    fun setVlessEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_VLESS_ENABLED, enabled)
    }

    fun getVlessUri(): String = settings.getString(KEY_VLESS_URI, "")

    fun setVlessUri(uri: String) {
        settings.putString(KEY_VLESS_URI, uri)
    }

    fun getScheduledTasksJson(): String = settings.getString(KEY_SCHEDULED_TASKS, "[]")

    fun setScheduledTasksJson(json: String) {
        settings.putString(KEY_SCHEDULED_TASKS, json)
    }

    // Heartbeat config
    fun getHeartbeatConfigJson(): String = settings.getString(KEY_HEARTBEAT_CONFIG, "")

    fun setHeartbeatConfigJson(json: String) {
        settings.putString(KEY_HEARTBEAT_CONFIG, json)
    }

    // Heartbeat log
    fun getHeartbeatLogJson(): String = settings.getString(KEY_HEARTBEAT_LOG, "")

    fun setHeartbeatLogJson(json: String) {
        settings.putString(KEY_HEARTBEAT_LOG, json)
    }

    // Heartbeat prompt
    fun getHeartbeatPrompt(): String = settings.getString(KEY_HEARTBEAT_PROMPT, "")

    fun setHeartbeatPrompt(text: String) {
        settings.putString(KEY_HEARTBEAT_PROMPT, text)
    }

    // MCP Servers
    fun getMcpServersJson(): String = settings.getString(KEY_MCP_SERVERS, "")

    fun setMcpServersJson(json: String) {
        settings.putString(KEY_MCP_SERVERS, json)
    }

    // UI Scale
    private val _uiScaleFlow = MutableStateFlow(settings.getFloat(KEY_UI_SCALE, defaultUiScale))
    val uiScaleFlow: StateFlow<Float> = _uiScaleFlow

    fun getUiScale(): Float = _uiScaleFlow.value

    fun setUiScale(scale: Float) {
        settings.putFloat(KEY_UI_SCALE, scale)
        _uiScaleFlow.value = scale
    }

    // Email
    fun isEmailEnabled(): Boolean = settings.getBoolean(KEY_EMAIL_ENABLED, true)

    fun setEmailEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_EMAIL_ENABLED, enabled)
    }

    fun getEmailAccountsJson(): String = settings.getString(KEY_EMAIL_ACCOUNTS, "")

    fun setEmailAccountsJson(json: String) {
        settings.putString(KEY_EMAIL_ACCOUNTS, json)
    }

    fun getEmailPassword(accountId: String): String = settings.getString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", "")

    fun setEmailPassword(accountId: String, password: String) {
        settings.putString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", password)
    }

    fun removeEmailPassword(accountId: String) {
        settings.remove("${KEY_EMAIL_PASSWORD_PREFIX}$accountId")
    }

    fun getEmailSyncStateJson(accountId: String): String = settings.getString("${KEY_EMAIL_SYNC_PREFIX}$accountId", "")

    fun setEmailSyncStateJson(accountId: String, json: String) {
        settings.putString("${KEY_EMAIL_SYNC_PREFIX}$accountId", json)
    }

    fun getEmailPollIntervalMinutes(): Int = settings.getInt(KEY_EMAIL_POLL_INTERVAL, 15)

    fun setEmailPollIntervalMinutes(minutes: Int) {
        settings.putInt(KEY_EMAIL_POLL_INTERVAL, minutes)
    }

    fun getEmailPendingJson(): String = settings.getString(KEY_EMAIL_PENDING, "")

    fun setEmailPendingJson(json: String) {
        settings.putString(KEY_EMAIL_PENDING, json)
    }

    // SMS (FOSS-only, Android-only — settings layer is platform-agnostic, feature gate
    // is enforced by the READ_SMS permission being declared only in foss/AndroidManifest.xml)
    fun isSmsEnabled(): Boolean = settings.getBoolean(KEY_SMS_ENABLED, false)

    fun setSmsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SMS_ENABLED, enabled)
    }

    fun getSmsPollIntervalMinutes(): Int = settings.getInt(KEY_SMS_POLL_INTERVAL, 15)

    fun setSmsPollIntervalMinutes(minutes: Int) {
        settings.putInt(KEY_SMS_POLL_INTERVAL, minutes)
    }

    fun getSmsPendingJson(): String = settings.getString(KEY_SMS_PENDING, "")

    fun setSmsPendingJson(json: String) {
        settings.putString(KEY_SMS_PENDING, json)
    }

    fun getSmsSyncStateJson(): String = settings.getString(KEY_SMS_SYNC_STATE, "")

    fun setSmsSyncStateJson(json: String) {
        settings.putString(KEY_SMS_SYNC_STATE, json)
    }

    fun isSmsSendEnabled(): Boolean = settings.getBoolean(KEY_SMS_SEND_ENABLED, false)

    fun setSmsSendEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SMS_SEND_ENABLED, enabled)
    }

    fun getSmsDraftsJson(): String = settings.getString(KEY_SMS_DRAFTS, "")

    fun setSmsDraftsJson(json: String) {
        settings.putString(KEY_SMS_DRAFTS, json)
    }

    // Notifications (FOSS-only, Android-only — settings layer is platform-agnostic, feature
    // gate is enforced by the listener service being declared only in foss/AndroidManifest.xml)
    fun isNotificationsEnabled(): Boolean = settings.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
    }

    fun getNotificationsPendingJson(): String = settings.getString(KEY_NOTIFICATIONS_PENDING, "")

    fun setNotificationsPendingJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_PENDING, json)
    }

    fun getNotificationsStoreJson(): String = settings.getString(KEY_NOTIFICATIONS_STORE, "")

    fun setNotificationsStoreJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_STORE, json)
    }

    fun getNotificationsSyncStateJson(): String = settings.getString(KEY_NOTIFICATIONS_SYNC_STATE, "")

    fun setNotificationsSyncStateJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_SYNC_STATE, json)
    }

    // Local model context size
    fun getModelContextTokens(modelId: String): Int = settings.getInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", 0)

    fun setModelContextTokens(modelId: String, contextTokens: Int) {
        settings.putInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", contextTokens)
    }

    // Splinterlands
    fun isSplinterlandsEnabled(): Boolean = settings.getBoolean(KEY_SPLINTERLANDS_ENABLED, false)

    fun setSplinterlandsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SPLINTERLANDS_ENABLED, enabled)
    }

    fun getSplinterlandsAccountJson(): String = settings.getString(KEY_SPLINTERLANDS_ACCOUNT, "")

    fun setSplinterlandsAccountJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_ACCOUNT, json)
    }

    fun getSplinterlandsPostingKey(): String = settings.getString(KEY_SPLINTERLANDS_POSTING_KEY, "")

    fun getSplinterlandsPostingKey(accountId: String): String = settings.getString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", "")
        .ifEmpty { getSplinterlandsPostingKey() } // fallback to legacy key

    fun setSplinterlandsPostingKey(accountId: String, key: String) {
        settings.putString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", key)
    }

    fun getSplinterlandsInstanceId(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_ID, "")

    fun setSplinterlandsInstanceId(instanceId: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_ID, instanceId)
    }

    fun getSplinterlandsInstanceIdsJson(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_IDS, "")

    fun setSplinterlandsInstanceIdsJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_IDS, json)
    }

    fun getSplinterlandsBattleLogJson(): String = settings.getString(KEY_SPLINTERLANDS_BATTLE_LOG, "")

    fun setSplinterlandsBattleLogJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_BATTLE_LOG, json)
    }

    // Logging
    fun isLoggingEnabled(): Boolean = settings.getBoolean(KEY_LOGGING_ENABLED, false)

    fun setLoggingEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_LOGGING_ENABLED, enabled)
    }

    fun getQuickActionsJson(): String = settings.getString(KEY_QUICK_ACTIONS, "[]")

    fun setQuickActionsJson(json: String) {
        settings.putString(KEY_QUICK_ACTIONS, json)
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"

        const val KEY_CONVERSATIONS = "conversations_json"
        const val KEY_CURRENT_CONVERSATION_ID = "current_conversation_id"
        const val KEY_CURRENT_INTERACTIVE_MODE = "current_interactive_mode"
        const val KEY_CURRENT_CONVERSATION_MIGRATED = "current_conversation_migrated"
        const val KEY_ENCRYPTION_KEY = "encryption_key"
        const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
        const val KEY_TOOL_PREFIX = "tool_enabled_"
        const val KEY_SOUL = "soul_text"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_MEMORY_INSTRUCTIONS = "memory_instructions"
        const val KEY_AGENT_MEMORIES = "agent_memories"
        const val KEY_SCHEDULED_TASKS = "scheduled_tasks"
        const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
        const val KEY_DYNAMIC_UI_ENABLED = "dynamic_ui_enabled"
        const val KEY_OLED_MODE_ENABLED = "oled_mode_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DAEMON_ENABLED = "daemon_enabled"
        const val KEY_HAS_REQUESTED_ROOT = "has_requested_root"
        const val KEY_HEARTBEAT_CONFIG = "heartbeat_config"
        const val KEY_HEARTBEAT_PROMPT = "heartbeat_prompt"
        const val KEY_HEARTBEAT_LOG = "heartbeat_log"

        const val KEY_EMAIL_ENABLED = "email_enabled"
        const val KEY_EMAIL_ACCOUNTS = "email_accounts"
        const val KEY_EMAIL_PASSWORD_PREFIX = "email_password_"
        const val KEY_EMAIL_SYNC_PREFIX = "email_sync_"
        const val KEY_EMAIL_POLL_INTERVAL = "email_poll_interval"
        const val KEY_EMAIL_PENDING = "email_pending"

        const val KEY_SMS_ENABLED = "sms_enabled"
        const val KEY_SMS_POLL_INTERVAL = "sms_poll_interval"
        const val KEY_SMS_PENDING = "sms_pending"
        const val KEY_SMS_SYNC_STATE = "sms_sync_state"
        const val KEY_SMS_SEND_ENABLED = "sms_send_enabled"
        const val KEY_SMS_DRAFTS = "sms_drafts"

        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_NOTIFICATIONS_PENDING = "notifications_pending"
        const val KEY_NOTIFICATIONS_STORE = "notifications_store"
        const val KEY_NOTIFICATIONS_SYNC_STATE = "notifications_sync_state"
        const val KEY_CONFIGURED_SERVICES = "configured_services"
        internal const val KEY_FREE_FALLBACK_ENABLED = "freeFallbackEnabled"
        internal const val KEY_QUICK_ACTIONS = "quickActions"
        const val KEY_FREE_MODE = "free_mode"
        const val KEY_FREE_SERVICE_PRIMARY = "free_service_primary"
        const val KEY_SERVICES_MIGRATION_COMPLETE = "services_migration_complete_v1"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_MCP_SERVERS = "mcp_servers"
        const val KEY_INSTANCE_MIGRATION_COMPLETE = "instance_migration_complete_v1"
        const val KEY_BASE_URL_V1_MIGRATION_COMPLETE = "base_url_v1_migration_complete"

        const val KEY_SPLINTERLANDS_ENABLED = "splinterlands_enabled"
        const val KEY_SPLINTERLANDS_ACCOUNT = "splinterlands_account"
        const val KEY_SPLINTERLANDS_POSTING_KEY = "splinterlands_posting_key"
        const val KEY_SPLINTERLANDS_BATTLE_LOG = "splinterlands_battle_log"
        const val KEY_SPLINTERLANDS_INSTANCE_ID = "splinterlands_instance_id"
        const val KEY_SPLINTERLANDS_INSTANCE_IDS = "splinterlands_instance_ids"

        const val KEY_MODEL_CONTEXT_PREFIX = "model_context_"

        const val KEY_SANDBOX_ENABLED = "sandbox_enabled"
        const val KEY_AGENT_VISIBILITY_ENABLED = "agent_visibility_enabled"
        const val KEY_VLESS_ENABLED = "vless_enabled"
        const val KEY_VLESS_URI = "vless_uri"

        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SERVER_USER = "server_user"
        const val KEY_SERVER_PASSWORD = "server_password"
        const val KEY_TUNNEL_PERSISTENT_RECONNECT = "tunnel_persistent_reconnect"

        const val KEY_LOGGING_ENABLED = "logging_enabled"

        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        const val KEY_WAKE_WORD_SOUND = "wake_word_sound_enabled"
        const val KEY_VOICE_RESPONSE_ENABLED = "voice_response_enabled"
        const val KEY_WATCH_INTEGRATION_ENABLED = "watch_integration_enabled"
        private const val KEY_WAKE_WORD_MODEL_LANG = "wake_word_model_lang"
        private const val KEY_WAKE_WORD_TRIGGER = "wake_word_trigger"
        private const val KEY_WAKE_WORD_VIBRATION = "wake_word_vibration"

        const val KEY_MONITOR_OVERLAY_MODE = "monitor_overlay_mode"

        // Basic memory guidance shared by every chat variant. The advanced `## Structured
        // Learning` block lives in `ChatSystemPromptBuilder.DEFAULT_STRUCTURED_LEARNING_SECTION`
        // and is composed in only for the remote variant.
        const val DEFAULT_MEMORY_INSTRUCTIONS =
            "You have persistent memory across conversations. " +
                "All your stored memories are listed in the system prompt grouped by category.\n\n" +
                "When you learn important information about the user (name, preferences, projects, goals, etc.), " +
                "proactively use the memory_store tool to save it.\n" +
                "Use the memory_forget tool to remove outdated or incorrect memories.\n" +
                "Do not store trivial or transient information."
    }
}
