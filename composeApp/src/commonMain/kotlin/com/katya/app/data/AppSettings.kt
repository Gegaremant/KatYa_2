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
    THOUGHTS_ONLY,
    SHORT,
    FULL,
}

enum class SttEngine {
    GKPSR,
    SYSTEM,
    LOCAL,
    CLOUD,
}

enum class TtsEngine {
    SYSTEM,
    LOCAL,
    CLOUD,
}

enum class AgentMode {
    SHORT,
    CONVERSATIONAL,
}

enum class Distro {
    DEBIAN,
    TERMUX,
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
        sections[ImportSection.SERVERS] = "1"
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
        sections[ImportSection.SERVERS] = "1"
    }
    return sections
}

data class ServiceInstance(
    val instanceId: String,
    val serviceId: String,
)

class AppSettings(internal val settings: Settings) {
    companion object {
        private const val KEY_LOG_FILE_PATH = "log_file_path"
    }

    fun getLogFilePath(): String? = settings.getStringOrNull(KEY_LOG_FILE_PATH)
    fun setLogFilePath(path: String?) {
        if (path != null) {
            settings.putString(KEY_LOG_FILE_PATH, path)
        } else {
            settings.remove(KEY_LOG_FILE_PATH)
        }
    }

    // App open tracking
    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(AppSettingsKeys.KEY_APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(AppSettingsKeys.KEY_APP_OPENS, newCount)
        return newCount
    }

    // Tool enable/disable settings
    fun isToolEnabled(toolId: String, defaultEnabled: Boolean = true): Boolean = settings.getBoolean("$AppSettingsKeys.KEY_TOOL_PREFIX$toolId", defaultEnabled)

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        settings.putBoolean("$AppSettingsKeys.KEY_TOOL_PREFIX$toolId", enabled)
    }

    fun getConversationsJson(): String? = settings.getStringOrNull(AppSettingsKeys.KEY_CONVERSATIONS)

    fun setConversationsJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_CONVERSATIONS, json)
    }

    fun removeConversationsJson() {
        settings.remove(AppSettingsKeys.KEY_CONVERSATIONS)
    }

    fun getCurrentConversationId(): String? = settings.getStringOrNull(AppSettingsKeys.KEY_CURRENT_CONVERSATION_ID)

    fun setCurrentConversationId(id: String?) {
        if (id == null) {
            settings.remove(AppSettingsKeys.KEY_CURRENT_CONVERSATION_ID)
        } else {
            settings.putString(AppSettingsKeys.KEY_CURRENT_CONVERSATION_ID, id)
        }
    }

    fun getCurrentInteractiveMode(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_CURRENT_INTERACTIVE_MODE, false)

    fun setCurrentInteractiveMode(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_CURRENT_INTERACTIVE_MODE, enabled)
    }

    fun isCurrentConversationMigrated(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_CURRENT_CONVERSATION_MIGRATED, false)

    fun markCurrentConversationMigrated() {
        settings.putBoolean(AppSettingsKeys.KEY_CURRENT_CONVERSATION_MIGRATED, true)
    }

    fun getEncryptionKey(): ByteArray? {
        val encoded = settings.getStringOrNull(AppSettingsKeys.KEY_ENCRYPTION_KEY) ?: return null
        return try {
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.decode(encoded)
        } catch (_: Exception) {
            null
        }
    }

    // Free fallback
    fun isFreeFallbackEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_FREE_FALLBACK_ENABLED, true)

    fun setFreeFallbackEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_FREE_FALLBACK_ENABLED, enabled)
    }

    fun getFreeMode(): FreeMode {
        val stored = settings.getStringOrNull(AppSettingsKeys.KEY_FREE_MODE) ?: return FreeMode.FAST
        return FreeMode.entries.find { it.name == stored } ?: FreeMode.FAST
    }

    fun setFreeMode(mode: FreeMode) {
        settings.putString(AppSettingsKeys.KEY_FREE_MODE, mode.name)
    }

    fun isFreeServicePrimary(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_FREE_SERVICE_PRIMARY, false)

    // Server Monitoring
    fun getLocalServerProfilesJson(): String = settings.getString("local_server_profiles", "[]")
    fun setLocalServerProfilesJson(json: String) {
        settings.putString("local_server_profiles", json)
    }
    fun getActiveLocalServerId(): String = settings.getString("active_local_server_id", "")
    fun setActiveLocalServerId(id: String) {
        settings.putString("active_local_server_id", id)
    }

    // Legacy fallback
    fun getServerIp(): String = settings.getString(AppSettingsKeys.KEY_SERVER_IP, "")
    fun setServerIp(ip: String) {
        settings.putString(AppSettingsKeys.KEY_SERVER_IP, ip)
    }

    fun getServerPort(): Int = settings.getInt(AppSettingsKeys.KEY_SERVER_PORT, 22)
    fun setServerPort(port: Int) {
        settings.putInt(AppSettingsKeys.KEY_SERVER_PORT, port)
    }

    fun getAutoBackupDirectory(): String = settings.getString("auto_backup_directory", "")
    fun setAutoBackupDirectory(path: String) {
        settings.putString("auto_backup_directory", path)
    }

    fun getServerUser(): String = settings.getString(AppSettingsKeys.KEY_SERVER_USER, "")
    fun setServerUser(user: String) {
        settings.putString(AppSettingsKeys.KEY_SERVER_USER, user)
    }

    fun getServerPassword(): String = settings.getString(AppSettingsKeys.KEY_SERVER_PASSWORD, "")
    fun setServerPassword(password: String) {
        settings.putString(AppSettingsKeys.KEY_SERVER_PASSWORD, password)
    }

    fun isTunnelPersistentReconnectEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_TUNNEL_PERSISTENT_RECONNECT, false)
    fun setTunnelPersistentReconnectEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_TUNNEL_PERSISTENT_RECONNECT, enabled)
    }

    fun getWakeWord(): String = settings.getString(AppSettingsKeys.KEY_WAKE_WORD, "привет катя")
    fun setWakeWord(word: String) {
        settings.putString(AppSettingsKeys.KEY_WAKE_WORD, word)
    }

    fun isVoiceResponseEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_VOICE_RESPONSE_ENABLED, true)

    fun setWakeWordEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_WAKE_WORD_ENABLED, enabled)
    }

    fun isWakeWordEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_WAKE_WORD_ENABLED, false)

    fun setWakeWordModelLang(lang: String) {
        settings.putString(AppSettingsKeys.KEY_WAKE_WORD_MODEL_LANG, lang)
    }

    fun getWakeWordModelLang(): String = settings.getString(AppSettingsKeys.KEY_WAKE_WORD_MODEL_LANG, "ru")

    fun setWakeWordTrigger(trigger: String) {
        settings.putString(AppSettingsKeys.KEY_WAKE_WORD_TRIGGER, trigger)
    }

    fun getWakeWordTrigger(): String = settings.getString(AppSettingsKeys.KEY_WAKE_WORD_TRIGGER, "Привет Катя")

    fun setWakeWordVibration(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_WAKE_WORD_VIBRATION, enabled)
    }

    fun isWakeWordVibrationEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_WAKE_WORD_VIBRATION, true)

    fun setWakeWordSound(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_WAKE_WORD_SOUND, enabled)
    }

    fun isWakeWordSoundEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_WAKE_WORD_SOUND, true)
    fun setVoiceResponseEnabled(enabled: Boolean) = settings.putBoolean(AppSettingsKeys.KEY_VOICE_RESPONSE_ENABLED, enabled)
    
    fun isVoiceRecognitionEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_VOICE_RECOGNITION_ENABLED, true)
    fun setVoiceRecognitionEnabled(enabled: Boolean) = settings.putBoolean(AppSettingsKeys.KEY_VOICE_RECOGNITION_ENABLED, enabled)

    fun isWatchIntegrationEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_WATCH_INTEGRATION_ENABLED, false)
    fun setWatchIntegrationEnabled(enabled: Boolean) = settings.putBoolean(AppSettingsKeys.KEY_WATCH_INTEGRATION_ENABLED, enabled)

    fun setFreeServicePrimary(primary: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_FREE_SERVICE_PRIMARY, primary)
    }

    // Soul (system prompt)
    fun getAudioTheme(): String = settings.getString("audio_theme", "katya")
    fun setAudioTheme(theme: String) = settings.putString("audio_theme", theme)

    fun getSttEngine(): SttEngine {
        val name = settings.getString("stt_engine", SttEngine.GKPSR.name)
        return try {
            SttEngine.valueOf(name)
        } catch (e: Exception) {
            SttEngine.GKPSR
        }
    }
    fun setSttEngine(engine: SttEngine) {
        settings.putString("stt_engine", engine.name)
        _sttEngineFlow.value = engine
    }

    private val _sttEngineFlow = MutableStateFlow(getSttEngine())
    val sttEngineFlow: StateFlow<SttEngine> = _sttEngineFlow

    fun getTtsEngine(): TtsEngine {
        val name = settings.getString("tts_engine", TtsEngine.SYSTEM.name)
        return try {
            if (name == "RHVOICE" || name == "PIPER") TtsEngine.LOCAL else TtsEngine.valueOf(name)
        } catch (e: IllegalArgumentException) {
            TtsEngine.SYSTEM
        }
    }
    fun setTtsEngine(engine: TtsEngine) {
        settings.putString("tts_engine", engine.name)
        _ttsEngineFlow.value = engine
    }

    private val _ttsEngineFlow = MutableStateFlow(getTtsEngine())
    val ttsEngineFlow: StateFlow<TtsEngine> = _ttsEngineFlow

    fun getAgentMode(): AgentMode {
        val name = settings.getString("agent_mode", AgentMode.CONVERSATIONAL.name)
        return try { AgentMode.valueOf(name) } catch (e: Exception) { AgentMode.CONVERSATIONAL }
    }
    fun setAgentMode(mode: AgentMode) = settings.putString("agent_mode", mode.name)
    
    fun getSendDelayMs(): Long = settings.getLong("send_delay_ms", 1000L)
    fun setSendDelayMs(delay: Long) = settings.putLong("send_delay_ms", delay)

    // OpenAI-compatible cloud speech settings (STT = /audio/transcriptions, TTS = /audio/speech)
    fun getCloudSttUrl(): String = settings.getString("cloud_stt_url", DEFAULTS.CLOUD_STT_URL)
    fun setCloudSttUrl(url: String) = settings.putString("cloud_stt_url", url)
    fun getCloudSttKey(): String = settings.getString("cloud_stt_key", "")
    fun setCloudSttKey(key: String) = settings.putString("cloud_stt_key", key)
    fun getCloudSttModel(): String = settings.getString("cloud_stt_model", DEFAULTS.CLOUD_STT_MODEL)
    fun setCloudSttModel(model: String) = settings.putString("cloud_stt_model", model)
    fun getCloudTtsUrl(): String = settings.getString("cloud_tts_url", DEFAULTS.CLOUD_TTS_URL)
    fun setCloudTtsUrl(url: String) = settings.putString("cloud_tts_url", url)
    fun getCloudTtsKey(): String = settings.getString("cloud_tts_key", "")
    fun setCloudTtsKey(key: String) = settings.putString("cloud_tts_key", key)
    fun getCloudTtsModel(): String = settings.getString("cloud_tts_model", DEFAULTS.CLOUD_TTS_MODEL)
    fun setCloudTtsModel(model: String) = settings.putString("cloud_tts_model", model)
    fun getCloudTtsVoice(): String = settings.getString("cloud_tts_voice", DEFAULTS.CLOUD_TTS_VOICE)
    fun setCloudTtsVoice(voice: String) = settings.putString("cloud_tts_voice", voice)

    // Piper on-device voice selection (base name of the installed *.tflite voice)
    fun getPiperSelectedVoice(): String? = settings.getStringOrNull("piper_selected_voice")
    fun setPiperSelectedVoice(baseName: String?) {
        if (baseName == null) settings.remove("piper_selected_voice") else settings.putString("piper_selected_voice", baseName)
    }

    // region Speech defaults
    private object DEFAULTS {
        const val CLOUD_STT_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val CLOUD_STT_MODEL = "whisper-1"
        const val CLOUD_TTS_URL = "https://api.openai.com/v1/audio/speech"
        const val CLOUD_TTS_MODEL = "tts-1"
        const val CLOUD_TTS_VOICE = "alloy"
    }
    // endregion

    fun getDistro(): Distro {
        val name = settings.getString("sandbox_distro", Distro.DEBIAN.name)
        return try {
            Distro.valueOf(name)
        } catch (e: Exception) {
            Distro.DEBIAN
        }
    }
    fun setDistro(distro: Distro) = settings.putString("sandbox_distro", distro.name)

    fun getSoulText(): String = settings.getString(AppSettingsKeys.KEY_SOUL, "")

    fun setSoulText(text: String) {
        settings.putString(AppSettingsKeys.KEY_SOUL, text)
    }

    // Memory
    fun isMemoryEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_MEMORY_ENABLED, true)

    fun setMemoryEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_MEMORY_ENABLED, enabled)
    }

    fun getMemoryInstructions(): String = settings.getString(AppSettingsKeys.KEY_MEMORY_INSTRUCTIONS, AppSettingsKeys.DEFAULT_MEMORY_INSTRUCTIONS)

    // Agent memories
    fun getMemoriesJson(): String = settings.getString(AppSettingsKeys.KEY_AGENT_MEMORIES, "[]")

    fun setMemoriesJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_AGENT_MEMORIES, json)
    }

    // Scheduling
    fun isSchedulingEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_SCHEDULING_ENABLED, true)

    fun setSchedulingEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_SCHEDULING_ENABLED, enabled)
    }

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_DYNAMIC_UI_ENABLED, true)

    fun setDynamicUiEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_DYNAMIC_UI_ENABLED, enabled)
    }

    private val _themeModeFlow = MutableStateFlow(loadInitialThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

    fun getThemeMode(): ThemeMode = _themeModeFlow.value

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(AppSettingsKeys.KEY_THEME_MODE, mode.name)
        _themeModeFlow.value = mode
    }

    // Monitor Overlay Mode
    private val _systemStatusFlow = MutableStateFlow<String?>(null)
    val systemStatusFlow: StateFlow<String?> = _systemStatusFlow

    fun setSystemStatus(status: String?) {
        _systemStatusFlow.value = status
    }

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
        settings.putString(AppSettingsKeys.KEY_MONITOR_OVERLAY_MODE, mode.name)
        _monitorOverlayModeFlow.value = mode
    }

    private fun loadInitialMonitorOverlayMode(): MonitorOverlayMode {
        val raw = settings.getString(AppSettingsKeys.KEY_MONITOR_OVERLAY_MODE, "")
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
        val raw = settings.getString(AppSettingsKeys.KEY_THEME_MODE, "")
        if (raw.isNotEmpty()) {
            return try {
                ThemeMode.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                ThemeMode.System
            }
        }
        // Migrate the legacy boolean OLED toggle: true → OledBlack, false → System.
        return if (settings.getBoolean(AppSettingsKeys.KEY_OLED_MODE_ENABLED, false)) ThemeMode.OledBlack else ThemeMode.System
    }

    // Daemon mode
    fun isDaemonEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_DAEMON_ENABLED, enabled)
    }

    // Root access dialog
    fun hasRequestedRoot(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_HAS_REQUESTED_ROOT, false)

    fun setRequestedRoot(requested: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_HAS_REQUESTED_ROOT, requested)
    }

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_SANDBOX_ENABLED, true)

    fun setSandboxEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_SANDBOX_ENABLED, enabled)
    }

    // GOD_MODE
    fun isGodModeEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_GOD_MODE_ENABLED, false)
    fun setGodModeEnabled(enabled: Boolean) = settings.putBoolean(AppSettingsKeys.KEY_GOD_MODE_ENABLED, enabled)

    // Onboarding
    fun isOnboardingCompleted(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_ONBOARDING_COMPLETED, completed)
    }

    // Agent Visibility (showing operations in UI)
    fun isAgentVisibilityEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_AGENT_VISIBILITY_ENABLED, true)

    fun setAgentVisibilityEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_AGENT_VISIBILITY_ENABLED, enabled)
    }

    // VLESS Proxy
    fun getVlessProxyProfilesJson(): String = settings.getString("vless_proxy_profiles", "[]")
    fun setVlessProxyProfilesJson(json: String) {
        settings.putString("vless_proxy_profiles", json)
    }
    fun getActiveVlessProxyId(): String = settings.getString("active_vless_proxy_id", "")
    fun setActiveVlessProxyId(id: String) {
        settings.putString("active_vless_proxy_id", id)
    }

    // active connection mode: "VLESS", "LOCAL", "NONE"
    fun getActiveConnectionMode(): String = settings.getString("active_connection_mode", "NONE")
    fun setActiveConnectionMode(mode: String) {
        settings.putString("active_connection_mode", mode)
    }

    fun isConstantAutoRecoveryEnabled(): Boolean = settings.getBoolean("auto_recovery_enabled", false)
    fun setConstantAutoRecoveryEnabled(enabled: Boolean) {
        settings.putBoolean("auto_recovery_enabled", enabled)
    }

    fun isShowDeviceStateEnabled(): Boolean = settings.getBoolean("show_device_state_enabled", false)
    fun setShowDeviceStateEnabled(enabled: Boolean) {
        settings.putBoolean("show_device_state_enabled", enabled)
    }

    fun isShowConnectionStateEnabled(): Boolean = settings.getBoolean("show_connection_state_enabled", false)
    fun setShowConnectionStateEnabled(enabled: Boolean) {
        settings.putBoolean("show_connection_state_enabled", enabled)
    }

    fun isShowAndVoiceThoughtsEnabled(): Boolean = settings.getBoolean("voice_thoughts_enabled", false)
    fun setShowAndVoiceThoughtsEnabled(enabled: Boolean) {
        settings.putBoolean("voice_thoughts_enabled", enabled)
    }

    fun getLoggingOptionsJson(): String = settings.getString("logging_options", "{}")
    fun setLoggingOptionsJson(json: String) {
        settings.putString("logging_options", json)
    }

    // Legacy fallback
    fun isVlessEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_VLESS_ENABLED, false)

    fun setVlessEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_VLESS_ENABLED, enabled)
    }

    fun getVlessUri(): String = settings.getString(AppSettingsKeys.KEY_VLESS_URI, "")

    fun setVlessUri(uri: String) {
        settings.putString(AppSettingsKeys.KEY_VLESS_URI, uri)
    }

    private val _isVlessConnectedFlow = MutableStateFlow(false)
    val isVlessConnectedFlow: StateFlow<Boolean> = _isVlessConnectedFlow

    fun isVlessConnected(): Boolean = _isVlessConnectedFlow.value

    fun setVlessConnected(connected: Boolean) {
        _isVlessConnectedFlow.value = connected
    }

    fun getScheduledTasksJson(): String = settings.getString(AppSettingsKeys.KEY_SCHEDULED_TASKS, "[]")

    fun setScheduledTasksJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SCHEDULED_TASKS, json)
    }

    // Heartbeat config
    fun getHeartbeatConfigJson(): String = settings.getString(AppSettingsKeys.KEY_HEARTBEAT_CONFIG, "")

    fun setHeartbeatConfigJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_HEARTBEAT_CONFIG, json)
    }

    // Heartbeat log
    fun getHeartbeatLogJson(): String = settings.getString(AppSettingsKeys.KEY_HEARTBEAT_LOG, "")

    fun setHeartbeatLogJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_HEARTBEAT_LOG, json)
    }

    // Heartbeat prompt
    fun getHeartbeatPrompt(): String = settings.getString(AppSettingsKeys.KEY_HEARTBEAT_PROMPT, "")

    fun setHeartbeatPrompt(text: String) {
        settings.putString(AppSettingsKeys.KEY_HEARTBEAT_PROMPT, text)
    }

    // MCP Servers
    fun getMcpServersJson(): String = settings.getString(AppSettingsKeys.KEY_MCP_SERVERS, "")

    fun setMcpServersJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_MCP_SERVERS, json)
    }

    // UI Scale
    private val _uiScaleFlow = MutableStateFlow(settings.getFloat(AppSettingsKeys.KEY_UI_SCALE, defaultUiScale))
    val uiScaleFlow: StateFlow<Float> = _uiScaleFlow

    fun getUiScale(): Float = _uiScaleFlow.value

    fun setUiScale(scale: Float) {
        settings.putFloat(AppSettingsKeys.KEY_UI_SCALE, scale)
        _uiScaleFlow.value = scale
    }

    // Email
    fun isEmailEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_EMAIL_ENABLED, true)

    fun setEmailEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_EMAIL_ENABLED, enabled)
    }

    fun getEmailAccountsJson(): String = settings.getString(AppSettingsKeys.KEY_EMAIL_ACCOUNTS, "")

    fun setEmailAccountsJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_EMAIL_ACCOUNTS, json)
    }

    fun getEmailPassword(accountId: String): String = settings.getString("${AppSettingsKeys.KEY_EMAIL_PASSWORD_PREFIX}$accountId", "")

    fun setEmailPassword(accountId: String, password: String) {
        settings.putString("${AppSettingsKeys.KEY_EMAIL_PASSWORD_PREFIX}$accountId", password)
    }

    fun removeEmailPassword(accountId: String) {
        settings.remove("${AppSettingsKeys.KEY_EMAIL_PASSWORD_PREFIX}$accountId")
    }

    fun getEmailSyncStateJson(accountId: String): String = settings.getString("${AppSettingsKeys.KEY_EMAIL_SYNC_PREFIX}$accountId", "")

    fun setEmailSyncStateJson(accountId: String, json: String) {
        settings.putString("${AppSettingsKeys.KEY_EMAIL_SYNC_PREFIX}$accountId", json)
    }

    fun getEmailPollIntervalMinutes(): Int = settings.getInt(AppSettingsKeys.KEY_EMAIL_POLL_INTERVAL, 15)

    fun setEmailPollIntervalMinutes(minutes: Int) {
        settings.putInt(AppSettingsKeys.KEY_EMAIL_POLL_INTERVAL, minutes)
    }

    fun getEmailPendingJson(): String = settings.getString(AppSettingsKeys.KEY_EMAIL_PENDING, "")

    fun setEmailPendingJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_EMAIL_PENDING, json)
    }

    // SMS (FOSS-only, Android-only — settings layer is platform-agnostic, feature gate
    // is enforced by the READ_SMS permission being declared only in foss/AndroidManifest.xml)
    fun isSmsEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_SMS_ENABLED, false)

    fun setSmsEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_SMS_ENABLED, enabled)
    }

    fun getSmsPollIntervalMinutes(): Int = settings.getInt(AppSettingsKeys.KEY_SMS_POLL_INTERVAL, 15)

    fun setSmsPollIntervalMinutes(minutes: Int) {
        settings.putInt(AppSettingsKeys.KEY_SMS_POLL_INTERVAL, minutes)
    }

    fun getSmsPendingJson(): String = settings.getString(AppSettingsKeys.KEY_SMS_PENDING, "")

    fun setSmsPendingJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SMS_PENDING, json)
    }

    fun getSmsSyncStateJson(): String = settings.getString(AppSettingsKeys.KEY_SMS_SYNC_STATE, "")

    fun setSmsSyncStateJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SMS_SYNC_STATE, json)
    }

    fun isSmsSendEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_SMS_SEND_ENABLED, false)

    fun setSmsSendEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_SMS_SEND_ENABLED, enabled)
    }

    fun getSmsDraftsJson(): String = settings.getString(AppSettingsKeys.KEY_SMS_DRAFTS, "")

    fun setSmsDraftsJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SMS_DRAFTS, json)
    }

    // Notifications (FOSS-only, Android-only — settings layer is platform-agnostic, feature
    // gate is enforced by the listener service being declared only in foss/AndroidManifest.xml)
    fun isNotificationsEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_NOTIFICATIONS_ENABLED, enabled)
    }

    fun getNotificationsPendingJson(): String = settings.getString(AppSettingsKeys.KEY_NOTIFICATIONS_PENDING, "")

    fun setNotificationsPendingJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_NOTIFICATIONS_PENDING, json)
    }

    fun getNotificationsStoreJson(): String = settings.getString(AppSettingsKeys.KEY_NOTIFICATIONS_STORE, "")

    fun setNotificationsStoreJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_NOTIFICATIONS_STORE, json)
    }

    fun getNotificationsSyncStateJson(): String = settings.getString(AppSettingsKeys.KEY_NOTIFICATIONS_SYNC_STATE, "")

    fun setNotificationsSyncStateJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_NOTIFICATIONS_SYNC_STATE, json)
    }

    // Local model context size
    fun getModelContextTokens(modelId: String): Int = settings.getInt("${AppSettingsKeys.KEY_MODEL_CONTEXT_PREFIX}$modelId", 0)

    fun setModelContextTokens(modelId: String, contextTokens: Int) {
        settings.putInt("${AppSettingsKeys.KEY_MODEL_CONTEXT_PREFIX}$modelId", contextTokens)
    }

    // Splinterlands
    fun isSplinterlandsEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_SPLINTERLANDS_ENABLED, false)

    fun setSplinterlandsEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_SPLINTERLANDS_ENABLED, enabled)
    }

    fun getSplinterlandsAccountJson(): String = settings.getString(AppSettingsKeys.KEY_SPLINTERLANDS_ACCOUNT, "")

    fun setSplinterlandsAccountJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SPLINTERLANDS_ACCOUNT, json)
    }

    fun getSplinterlandsPostingKey(): String = settings.getString(AppSettingsKeys.KEY_SPLINTERLANDS_POSTING_KEY, "")

    fun getSplinterlandsPostingKey(accountId: String): String = settings.getString("${AppSettingsKeys.KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", "")
        .ifEmpty { getSplinterlandsPostingKey() } // fallback to legacy key

    fun setSplinterlandsPostingKey(accountId: String, key: String) {
        settings.putString("${AppSettingsKeys.KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", key)
    }

    fun getSplinterlandsInstanceId(): String = settings.getString(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_ID, "")

    fun setSplinterlandsInstanceId(instanceId: String) {
        settings.putString(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_ID, instanceId)
    }

    fun getSplinterlandsInstanceIdsJson(): String = settings.getString(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_IDS, "")

    fun setSplinterlandsInstanceIdsJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_IDS, json)
    }

    fun getSplinterlandsBattleLogJson(): String = settings.getString(AppSettingsKeys.KEY_SPLINTERLANDS_BATTLE_LOG, "")

    fun setSplinterlandsBattleLogJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_SPLINTERLANDS_BATTLE_LOG, json)
    }

    // Logging
    fun isLoggingEnabled(): Boolean = settings.getBoolean(AppSettingsKeys.KEY_LOGGING_ENABLED, false)

    fun setLoggingEnabled(enabled: Boolean) {
        settings.putBoolean(AppSettingsKeys.KEY_LOGGING_ENABLED, enabled)
    }

    fun getQuickActionsJson(): String = settings.getString(AppSettingsKeys.KEY_QUICK_ACTIONS, "[]")

    fun setQuickActionsJson(json: String) {
        settings.putString(AppSettingsKeys.KEY_QUICK_ACTIONS, json)
    }
}
