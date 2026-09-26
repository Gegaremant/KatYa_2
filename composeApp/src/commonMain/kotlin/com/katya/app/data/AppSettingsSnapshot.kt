package com.katya.app.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A store that holds nothing and answers every read with the supplied default.
 *
 * This is how the "factory default" side of the diff is obtained: run the real
 * getters against this and you get exactly what a fresh install would show,
 * without a second hand-written table of defaults that could drift.
 */
private class DefaultsOnlySettings : Settings {
    override val keys: Set<String> = emptySet()
    override val size: Int = 0
    override fun clear() = Unit
    override fun remove(key: String) = Unit
    override fun hasKey(key: String): Boolean = false
    override fun putInt(key: String, value: Int) = Unit
    override fun getInt(key: String, defaultValue: Int): Int = defaultValue
    override fun getIntOrNull(key: String): Int? = null
    override fun putLong(key: String, value: Long) = Unit
    override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    override fun getLongOrNull(key: String): Long? = null
    override fun putString(key: String, value: String) = Unit
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getStringOrNull(key: String): String? = null
    override fun putFloat(key: String, value: Float) = Unit
    override fun getFloat(key: String, defaultValue: Float): Float = defaultValue
    override fun getFloatOrNull(key: String): Float? = null
    override fun putDouble(key: String, value: Double) = Unit
    override fun getDouble(key: String, defaultValue: Double): Double = defaultValue
    override fun getDoubleOrNull(key: String): Double? = null
    override fun putBoolean(key: String, value: Boolean) = Unit
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = null
}

/**
 * Complete, key-by-key settings snapshot — feedback #12/#13.
 *
 * The old export hand-picked a couple of dozen fields, so everything it forgot
 * (`sandbox_enabled`, `god_mode_enabled`, `onboarding_completed`, theme, voice,
 * STT/TTS, wake word, server profiles…) was simply lost on restore. This file is
 * the exhaustive list instead: every user-facing setting, read through the very
 * same getter the app uses, so a value exported here is exactly the value the app
 * would show.
 *
 * Defaults are not written out by hand. They come from running the same getters
 * against an empty store, so a default can never drift away from the one the app
 * actually uses — and "merge only what is non-default" (the "Дополнить" mode)
 * stays honest even after a default changes in some future release.
 *
 * Deliberately *not* exported: the encryption key (shipping it next to the
 * encrypted database would defeat the encryption), migration flags, log file
 * paths, the open-counter and the transient sync/pending caches. Those are device
 * state, not configuration.
 */

/** Top-level key of the flat settings map inside a backup. */
const val SNAPSHOT_KEY = "app_settings"

/** Top-level key of the per-service-instance map inside a backup. */
const val SNAPSHOT_INSTANCES_KEY = "app_settings_instances"

/** How an imported backup is merged into the current configuration. */
enum class ImportMode {
    /**
     * "Дополнить": take over only the values that differ from the factory default.
     * A fresh install ends up with the backup's configuration, while a configured
     * install keeps everything the backup says nothing about.
     */
    Merge,

    /**
     * "Заменить": the backup becomes the configuration. Values the backup does not
     * carry (because they are still at their default) are reset to that default.
     */
    Replace,
}

/**
 * The diff for both import modes, computed together.
 *
 * The review dialog lets the user flip between "Дополнить" and "Заменить", and
 * the two answer different questions (what is non-default vs what differs from
 * here), so both are prepared while the backup is being read.
 */
data class ImportPreviews(
    val merge: ImportPreview,
    val replace: ImportPreview,
) {
    operator fun get(mode: ImportMode): ImportPreview = if (mode == ImportMode.Merge) merge else replace

    companion object {
        /** An empty review — used by previews and by the no-op action defaults. */
        fun empty(mode: ImportMode = ImportMode.Merge): ImportPreview = ImportPreview(mode, emptyList(), emptyMap(), false)
    }
}

/** One row of the pre-import diff. Values are already formatted for display. */
data class SettingDiff(
    val key: String,
    val section: ImportSection,
    val current: String,
    val incoming: String,
    val secret: Boolean,
    /** False when this row would not be touched by the chosen mode. */
    val changed: Boolean,
)

/** Everything the import dialog needs to show before the user commits. */
data class ImportPreview(
    val mode: ImportMode,
    val diff: List<SettingDiff>,
    val sectionCounts: Map<ImportSection, String?>,
    val hasSnapshot: Boolean,
) {
    val changedCount: Int get() = diff.count { it.changed }
}

/** One exported setting: where it lives, how to read it, how to write it back. */
private class Spec(
    val key: String,
    val section: ImportSection,
    val read: (AppSettings) -> JsonElement,
    val write: (AppSettings, JsonElement) -> Unit,
    val secret: Boolean = false,
    /**
     * Set for keys that are only ever written when the backup carries them, so
     * "merge only non-defaults" must not filter them out. Tool toggles work that
     * way: the store holds a key exactly when the user deviated from the tool's
     * own default, which lives in the tool registry rather than here.
     */
    val forceApply: Boolean = false,
) {
    fun default(): JsonElement = read(AppSettings(DefaultsOnlySettings()))
}

private fun text(element: JsonElement): String = element.jsonPrimitive.content

// The read helpers take their AppSettings as a receiver, so every row of the
// table below reads as the plain getter it wraps; writes keep an explicit
// `app` parameter to match the setter pairs already written that way.
private fun strSpec(
    key: String,
    section: ImportSection,
    secret: Boolean = false,
    read: AppSettings.() -> String,
    write: (AppSettings, String) -> Unit,
) = Spec(key, section, { JsonPrimitive(it.read()) }, { app, value -> write(app, text(value)) }, secret)

private fun boolSpec(
    key: String,
    section: ImportSection,
    read: AppSettings.() -> Boolean,
    write: (AppSettings, Boolean) -> Unit,
) = Spec(
    key,
    section,
    { JsonPrimitive(it.read()) },
    { app, value ->
        val flag = value.jsonPrimitive.booleanOrNull
        if (flag != null) write(app, flag)
    },
)

private fun intSpec(
    key: String,
    section: ImportSection,
    read: AppSettings.() -> Int,
    write: (AppSettings, Int) -> Unit,
) = Spec(key, section, { JsonPrimitive(it.read()) }, { app, value -> value.jsonPrimitive.intOrNull?.let { write(app, it) } })

private fun longSpec(
    key: String,
    section: ImportSection,
    read: AppSettings.() -> Long,
    write: (AppSettings, Long) -> Unit,
) = Spec(key, section, { JsonPrimitive(it.read()) }, { app, value -> value.jsonPrimitive.longOrNull?.let { write(app, it) } })

private fun floatSpec(
    key: String,
    section: ImportSection,
    read: AppSettings.() -> Float,
    write: (AppSettings, Float) -> Unit,
) = Spec(key, section, { JsonPrimitive(it.read()) }, { app, value -> value.jsonPrimitive.floatOrNull?.let { write(app, it) } })

private fun enumSpec(
    key: String,
    section: ImportSection,
    read: AppSettings.() -> String,
    values: Set<String>,
    write: (AppSettings, String) -> Unit,
) = Spec(
    key,
    section,
    { JsonPrimitive(it.read()) },
    { app, value ->
        val name = text(value)
        if (name in values) write(app, name)
    },
)

/**
 * Every setting that belongs in a backup. Keep this list exhaustive: when a new
 * setting is added to [AppSettings], add it here too, otherwise restoring a
 * backup silently loses it again — which is exactly the bug this file replaces.
 */
private fun snapshotSpecs(): List<Spec> = listOf(
    // --- Services and models -------------------------------------------------
    strSpec("configured_services", ImportSection.SERVICES, read = { getStringRaw("configured_services") }, write = { app, v -> app.settings.putString(AppSettingsKeys.KEY_CONFIGURED_SERVICES, v) }),
    strSpec("current_service_id", ImportSection.SERVICES, read = { getStringRaw(AppSettingsKeys.KEY_CURRENT_SERVICE_ID) }, write = { app, v -> app.settings.putString(AppSettingsKeys.KEY_CURRENT_SERVICE_ID, v) }),
    boolSpec("free_fallback_enabled", ImportSection.SERVICES, read = { isFreeFallbackEnabled() }, write = { app, v -> app.setFreeFallbackEnabled(v) }),
    enumSpec("free_mode", ImportSection.SERVICES, read = { getFreeMode().name }, values = FreeMode.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setFreeMode(FreeMode.valueOf(v)) }),
    boolSpec("free_service_primary", ImportSection.SERVICES, read = { isFreeServicePrimary() }, write = { app, v -> app.setFreeServicePrimary(v) }),
    strSpec("quickActions", ImportSection.TOOLS, read = { getQuickActionsJson() }, write = { app, v -> app.setQuickActionsJson(v) }),

    // --- Soul and memory -----------------------------------------------------
    strSpec(AppSettingsKeys.KEY_SOUL, ImportSection.SOUL, read = { getSoulText() }, write = { app, v -> app.setSoulText(v) }),
    boolSpec(AppSettingsKeys.KEY_MEMORY_ENABLED, ImportSection.MEMORY, read = { isMemoryEnabled() }, write = { app, v -> app.setMemoryEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_MEMORY_INSTRUCTIONS, ImportSection.MEMORY, read = { getMemoryInstructions() }, write = { app, v -> app.settings.putString(AppSettingsKeys.KEY_MEMORY_INSTRUCTIONS, v) }),
    strSpec(AppSettingsKeys.KEY_AGENT_MEMORIES, ImportSection.MEMORY, read = { getMemoriesJson() }, write = { app, v -> app.setMemoriesJson(v) }),

    // --- Scheduling and heartbeat -------------------------------------------
    boolSpec(AppSettingsKeys.KEY_SCHEDULING_ENABLED, ImportSection.SCHEDULING, read = { isSchedulingEnabled() }, write = { app, v -> app.setSchedulingEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_SCHEDULED_TASKS, ImportSection.SCHEDULING, read = { getScheduledTasksJson() }, write = { app, v -> app.setScheduledTasksJson(v) }),
    strSpec(AppSettingsKeys.KEY_HEARTBEAT_CONFIG, ImportSection.HEARTBEAT, read = { getHeartbeatConfigJson() }, write = { app, v -> app.setHeartbeatConfigJson(v) }),
    strSpec(AppSettingsKeys.KEY_HEARTBEAT_PROMPT, ImportSection.HEARTBEAT, read = { getHeartbeatPrompt() }, write = { app, v -> app.setHeartbeatPrompt(v) }),

    // --- Email ---------------------------------------------------------------
    boolSpec(AppSettingsKeys.KEY_EMAIL_ENABLED, ImportSection.EMAIL, read = { isEmailEnabled() }, write = { app, v -> app.setEmailEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_EMAIL_ACCOUNTS, ImportSection.EMAIL, read = { getEmailAccountsJson() }, write = { app, v -> app.setEmailAccountsJson(v) }),
    intSpec(AppSettingsKeys.KEY_EMAIL_POLL_INTERVAL, ImportSection.EMAIL, read = { getEmailPollIntervalMinutes() }, write = { app, v -> app.setEmailPollIntervalMinutes(v) }),

    // --- SMS and notifications ---------------------------------------------
    boolSpec(AppSettingsKeys.KEY_SMS_ENABLED, ImportSection.SMS, read = { isSmsEnabled() }, write = { app, v -> app.setSmsEnabled(v) }),
    intSpec(AppSettingsKeys.KEY_SMS_POLL_INTERVAL, ImportSection.SMS, read = { getSmsPollIntervalMinutes() }, write = { app, v -> app.setSmsPollIntervalMinutes(v) }),
    boolSpec(AppSettingsKeys.KEY_SMS_SEND_ENABLED, ImportSection.SMS, read = { isSmsSendEnabled() }, write = { app, v -> app.setSmsSendEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_NOTIFICATIONS_ENABLED, ImportSection.SMS, read = { isNotificationsEnabled() }, write = { app, v -> app.setNotificationsEnabled(v) }),

    // --- Splinterlands -------------------------------------------------------
    boolSpec(AppSettingsKeys.KEY_SPLINTERLANDS_ENABLED, ImportSection.SPLINTERLANDS, read = { isSplinterlandsEnabled() }, write = { app, v -> app.setSplinterlandsEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_SPLINTERLANDS_ACCOUNT, ImportSection.SPLINTERLANDS, read = { getSplinterlandsAccountJson() }, write = { app, v -> app.setSplinterlandsAccountJson(v) }),
    strSpec(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_ID, ImportSection.SPLINTERLANDS, read = { getSplinterlandsInstanceId() }, write = { app, v -> app.setSplinterlandsInstanceId(v) }),
    strSpec(AppSettingsKeys.KEY_SPLINTERLANDS_INSTANCE_IDS, ImportSection.SPLINTERLANDS, read = { getSplinterlandsInstanceIdsJson() }, write = { app, v -> app.setSplinterlandsInstanceIdsJson(v) }),

    // --- MCP -----------------------------------------------------------------
    // The MCP config carries per-server `headers`, and those routinely hold
    // `Authorization: Bearer …`. The whole blob is one settings value, so it is
    // masked as a unit — a diff that helpfully printed the token back at the user
    // (or into a screenshot of the import dialog) was a leak. This only affects
    // display; the export file still contains the real values.
    strSpec(AppSettingsKeys.KEY_MCP_SERVERS, ImportSection.MCP, secret = true, read = { getMcpServersJson() }, write = { app, v -> app.setMcpServersJson(v) }),

    // --- Servers, tunnel, VLESS ---------------------------------------------
    strSpec("local_server_profiles", ImportSection.SERVERS, read = { getLocalServerProfilesJson() }, write = { app, v -> app.setLocalServerProfilesJson(v) }),
    strSpec("active_local_server_id", ImportSection.SERVERS, read = { getActiveLocalServerId() }, write = { app, v -> app.setActiveLocalServerId(v) }),
    strSpec(AppSettingsKeys.KEY_SERVER_IP, ImportSection.SERVERS, read = { getServerIp() }, write = { app, v -> app.setServerIp(v) }),
    intSpec(AppSettingsKeys.KEY_SERVER_PORT, ImportSection.SERVERS, read = { getServerPort() }, write = { app, v -> app.setServerPort(v) }),
    strSpec(AppSettingsKeys.KEY_SERVER_USER, ImportSection.SERVERS, read = { getServerUser() }, write = { app, v -> app.setServerUser(v) }),
    strSpec(AppSettingsKeys.KEY_SERVER_PASSWORD, ImportSection.SERVERS, secret = true, read = { getServerPassword() }, write = { app, v -> app.setServerPassword(v) }),
    strSpec("auto_backup_directory", ImportSection.SERVERS, read = { getAutoBackupDirectory() }, write = { app, v -> app.setAutoBackupDirectory(v) }),
    boolSpec(AppSettingsKeys.KEY_TUNNEL_PERSISTENT_RECONNECT, ImportSection.SERVERS, read = { isTunnelPersistentReconnectEnabled() }, write = { app, v -> app.setTunnelPersistentReconnectEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_VLESS_ENABLED, ImportSection.SERVERS, read = { isVlessEnabled() }, write = { app, v -> app.setVlessEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_VLESS_URI, ImportSection.SERVERS, secret = true, read = { getVlessUri() }, write = { app, v -> app.setVlessUri(v) }),
    strSpec("vless_proxy_profiles", ImportSection.SERVERS, secret = true, read = { getVlessProxyProfilesJson() }, write = { app, v -> app.setVlessProxyProfilesJson(v) }),
    strSpec("active_vless_proxy_id", ImportSection.SERVERS, read = { getActiveVlessProxyId() }, write = { app, v -> app.setActiveVlessProxyId(v) }),
    strSpec("active_connection_mode", ImportSection.SERVERS, read = { getActiveConnectionMode() }, write = { app, v -> app.setActiveConnectionMode(v) }),
    boolSpec(AppSettingsKeys.KEY_AGENT_VISIBILITY_ENABLED, ImportSection.SERVERS, read = { isAgentVisibilityEnabled() }, write = { app, v -> app.setAgentVisibilityEnabled(v) }),

    // --- Appearance and behaviour -------------------------------------------
    enumSpec(AppSettingsKeys.KEY_THEME_MODE, ImportSection.SETTINGS, read = { getThemeMode().name }, values = ThemeMode.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setThemeMode(ThemeMode.valueOf(v)) }),
    enumSpec(AppSettingsKeys.KEY_MONITOR_OVERLAY_MODE, ImportSection.SETTINGS, read = { getMonitorOverlayMode().name }, values = MonitorOverlayMode.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setMonitorOverlayMode(MonitorOverlayMode.valueOf(v)) }),
    boolSpec(AppSettingsKeys.KEY_DYNAMIC_UI_ENABLED, ImportSection.SETTINGS, read = { isDynamicUiEnabled() }, write = { app, v -> app.setDynamicUiEnabled(v) }),
    enumSpec("voice_ui_mode", ImportSection.SETTINGS, read = { getVoiceUiMode().name }, values = VoiceUiMode.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setVoiceUiMode(VoiceUiMode.valueOf(v)) }),
    strSpec("audio_theme", ImportSection.SETTINGS, read = { getAudioTheme() }, write = { app, v -> app.setAudioTheme(v) }),
    floatSpec(AppSettingsKeys.KEY_UI_SCALE, ImportSection.SETTINGS, read = { getUiScale() }, write = { app, v -> app.setUiScale(v) }),
    boolSpec("auto_recovery_enabled", ImportSection.SETTINGS, read = { isConstantAutoRecoveryEnabled() }, write = { app, v -> app.setConstantAutoRecoveryEnabled(v) }),
    boolSpec("show_device_state_enabled", ImportSection.SETTINGS, read = { isShowDeviceStateEnabled() }, write = { app, v -> app.setShowDeviceStateEnabled(v) }),
    boolSpec("show_connection_state_enabled", ImportSection.SETTINGS, read = { isShowConnectionStateEnabled() }, write = { app, v -> app.setShowConnectionStateEnabled(v) }),
    boolSpec("voice_thoughts_enabled", ImportSection.SETTINGS, read = { isShowAndVoiceThoughtsEnabled() }, write = { app, v -> app.setShowAndVoiceThoughtsEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_LOGGING_ENABLED, ImportSection.SETTINGS, read = { isLoggingEnabled() }, write = { app, v -> app.setLoggingEnabled(v) }),
    strSpec("logging_options", ImportSection.SETTINGS, read = { getLoggingOptionsJson() }, write = { app, v -> app.setLoggingOptionsJson(v) }),

    // --- Voice: wake word, STT, TTS -----------------------------------------
    boolSpec(AppSettingsKeys.KEY_WAKE_WORD_ENABLED, ImportSection.SETTINGS, read = { isWakeWordEnabled() }, write = { app, v -> app.setWakeWordEnabled(v) }),
    strSpec(AppSettingsKeys.KEY_WAKE_WORD, ImportSection.SETTINGS, read = { getWakeWord() }, write = { app, v -> app.setWakeWord(v) }),
    strSpec(AppSettingsKeys.KEY_WAKE_WORD_TRIGGER, ImportSection.SETTINGS, read = { getWakeWordTrigger() }, write = { app, v -> app.setWakeWordTrigger(v) }),
    strSpec(AppSettingsKeys.KEY_WAKE_WORD_MODEL_LANG, ImportSection.SETTINGS, read = { getWakeWordModelLang() }, write = { app, v -> app.setWakeWordModelLang(v) }),
    boolSpec(AppSettingsKeys.KEY_WAKE_WORD_VIBRATION, ImportSection.SETTINGS, read = { isWakeWordVibrationEnabled() }, write = { app, v -> app.setWakeWordVibration(v) }),
    boolSpec(AppSettingsKeys.KEY_WAKE_WORD_SOUND, ImportSection.SETTINGS, read = { isWakeWordSoundEnabled() }, write = { app, v -> app.setWakeWordSound(v) }),
    boolSpec(AppSettingsKeys.KEY_VOICE_RESPONSE_ENABLED, ImportSection.SETTINGS, read = { isVoiceResponseEnabled() }, write = { app, v -> app.setVoiceResponseEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_VOICE_RECOGNITION_ENABLED, ImportSection.SETTINGS, read = { isVoiceRecognitionEnabled() }, write = { app, v -> app.setVoiceRecognitionEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_WATCH_INTEGRATION_ENABLED, ImportSection.SETTINGS, read = { isWatchIntegrationEnabled() }, write = { app, v -> app.setWatchIntegrationEnabled(v) }),
    enumSpec("stt_engine", ImportSection.SETTINGS, read = { getSttEngine().name }, values = SttEngine.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setSttEngine(SttEngine.valueOf(v)) }),
    enumSpec("tts_engine", ImportSection.SETTINGS, read = { getTtsEngine().name }, values = TtsEngine.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setTtsEngine(TtsEngine.valueOf(v)) }),
    strSpec("cloud_stt_url", ImportSection.SETTINGS, read = { getCloudSttUrl() }, write = { app, v -> app.setCloudSttUrl(v) }),
    strSpec("cloud_stt_key", ImportSection.SETTINGS, secret = true, read = { getCloudSttKey() }, write = { app, v -> app.setCloudSttKey(v) }),
    strSpec("cloud_stt_model", ImportSection.SETTINGS, read = { getCloudSttModel() }, write = { app, v -> app.setCloudSttModel(v) }),
    strSpec("cloud_tts_url", ImportSection.SETTINGS, read = { getCloudTtsUrl() }, write = { app, v -> app.setCloudTtsUrl(v) }),
    strSpec("cloud_tts_key", ImportSection.SETTINGS, secret = true, read = { getCloudTtsKey() }, write = { app, v -> app.setCloudTtsKey(v) }),
    strSpec("cloud_tts_model", ImportSection.SETTINGS, read = { getCloudTtsModel() }, write = { app, v -> app.setCloudTtsModel(v) }),
    strSpec("cloud_tts_voice", ImportSection.SETTINGS, read = { getCloudTtsVoice() }, write = { app, v -> app.setCloudTtsVoice(v) }),
    floatSpec("sys_tts_pitch", ImportSection.SETTINGS, read = { getSysTtsPitch() }, write = { app, v -> app.setSysTtsPitch(v) }),
    floatSpec("sys_tts_rate", ImportSection.SETTINGS, read = { getSysTtsRate() }, write = { app, v -> app.setSysTtsRate(v) }),

    // --- Sandbox, root, onboarding, daemon ----------------------------------
    boolSpec(AppSettingsKeys.KEY_SANDBOX_ENABLED, ImportSection.SETTINGS, read = { isSandboxEnabled() }, write = { app, v -> app.setSandboxEnabled(v) }),
    enumSpec("sandbox_distro", ImportSection.SETTINGS, read = { getDistro().name }, values = Distro.entries.mapTo(mutableSetOf()) { it.name }, write = { app, v -> app.setDistro(Distro.valueOf(v)) }),
    boolSpec(AppSettingsKeys.KEY_GOD_MODE_ENABLED, ImportSection.SETTINGS, read = { isGodModeEnabled() }, write = { app, v -> app.setGodModeEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_HAS_REQUESTED_ROOT, ImportSection.SETTINGS, read = { hasRequestedRoot() }, write = { app, v -> app.setRequestedRoot(v) }),
    boolSpec(AppSettingsKeys.KEY_DAEMON_ENABLED, ImportSection.SETTINGS, read = { isDaemonEnabled() }, write = { app, v -> app.setDaemonEnabled(v) }),
    boolSpec(AppSettingsKeys.KEY_ONBOARDING_COMPLETED, ImportSection.SETTINGS, read = { isOnboardingCompleted() }, write = { app, v -> app.setOnboardingCompleted(v) }),
    boolSpec(AppSettingsKeys.KEY_INTRO_VOICE_DISABLED, ImportSection.SETTINGS, read = { isIntroVoiceDisabled() }, write = { app, v -> app.setIntroVoiceDisabled(v) }),
    boolSpec(AppSettingsKeys.KEY_INTRO_VOICE_PLAYED, ImportSection.SETTINGS, read = { isIntroVoicePlayed() }, write = { app, v -> app.setIntroVoicePlayed(v) }),
    boolSpec(AppSettingsKeys.KEY_SKIP_COMPONENTS_PROMPT, ImportSection.SETTINGS, read = { isComponentsPromptSkipped() }, write = { app, v -> app.setComponentsPromptSkipped(v) }),
)

/** Raw read of a key, used where no getter exists. */
private fun AppSettings.getStringRaw(key: String): String = settings.getString(key, "")

/**
 * Tool toggles. A tool's key only exists in the store when the user deviated
 * from that tool's own default, so carrying the key *is* carrying a decision —
 * both an explicit "on" for a default-off tool and an explicit "off" for a
 * default-on one. That's why these are [Spec.forceApply] rather than filtered
 * through a default this file cannot know.
 */
private fun AppSettings.toolSpecs(incomingKeys: Set<String>): List<Spec> {
    val localKeys = settings.keys.filter { it.startsWith(AppSettingsKeys.KEY_TOOL_PREFIX) }
    // Only tool-prefixed keys from the backup. Taking the whole flat map here would
    // invent a TOOLS-section row for every setting in the file — the review dialog
    // would then list `ui_scale` under "Инструменты" as if it were a tool toggle.
    val incomingToolKeys = incomingKeys.filter { it.startsWith(AppSettingsKeys.KEY_TOOL_PREFIX) }
    return (localKeys + incomingToolKeys).distinct().map { key ->
        val toolId = key.removePrefix(AppSettingsKeys.KEY_TOOL_PREFIX)
        Spec(
            key = key,
            section = ImportSection.TOOLS,
            read = { JsonPrimitive(it.isToolEnabled(toolId)) },
            write = { app, value ->
                value.jsonPrimitive.booleanOrNull?.let { app.setToolEnabled(toolId, it) }
            },
            forceApply = true,
        )
    }
}

/** Per-service-instance settings. */
private class InstanceSpec(
    val instanceId: String,
    val field: String,
    val secret: Boolean,
    val read: (AppSettings) -> String,
    val write: (AppSettings, String) -> Unit,
) {
    val key: String = "instance.$instanceId.$field"
}

private fun instanceSpecs(instanceIds: Collection<String>): List<InstanceSpec> = instanceIds.flatMap { id ->
    listOf(
        InstanceSpec(id, "api_key", secret = true, read = { it.getInstanceApiKey(id) }, write = { app, v -> app.setInstanceApiKey(id, v) }),
        InstanceSpec(id, "model_id", secret = false, read = { it.getInstanceModelId(id) }, write = { app, v -> app.setInstanceModelId(id, v) }),
        InstanceSpec(id, "base_url", secret = false, read = { it.getInstanceBaseUrl(id) }, write = { app, v -> app.setInstanceBaseUrl(id, v) }),
        InstanceSpec(id, "deepseek_session", secret = true, read = { it.getInstanceDeepSeekSession(id) }, write = { app, v -> app.setInstanceDeepSeekSession(id, v) }),
    )
}

/** Instance ids known locally, so their keys make it into the export. */
private fun AppSettings.localInstanceIds(): List<String> = getConfiguredServiceInstances().map { it.instanceId }

/** Instance ids declared by a backup, so a restore onto a clean install sees them. */
private fun instanceIdsIn(imported: JsonObject): List<String> = imported[SNAPSHOT_INSTANCES_KEY]?.jsonObject?.keys?.toList().orEmpty()

/**
 * The flat settings map of a backup.
 *
 * A 3.1.3-fix backup nests everything under [SNAPSHOT_KEY]. An older backup keeps
 * the same field names at the top level, so its flat map *is* the document minus
 * the bookkeeping keys. Falling back to an empty object here is what made the
 * review dialog answer "Различий нет" for every legacy file while the import
 * itself went on to change plenty — the diff was computed from nothing.
 */
private fun flatMapOf(imported: JsonObject): JsonObject = imported[SNAPSHOT_KEY]?.jsonObject
    ?: JsonObject(imported.filterKeys { it !in NON_SETTING_TOP_LEVEL_KEYS })

/** Top-level keys that are structure rather than a setting, and never a diff row. */
private val NON_SETTING_TOP_LEVEL_KEYS = setOf(
    "version",
    "conversations",
    "email_passwords",
    "email_sync_states",
    "splinterlands_posting_keys",
    SNAPSHOT_INSTANCES_KEY,
)

/** Instance specs for a diff: the union of what exists here and what the backup has. */
private fun AppSettings.allInstanceIds(imported: JsonObject): List<String> = (localInstanceIds() + instanceIdsIn(imported)).distinct()

private fun AppSettings.instanceSpecList(imported: JsonObject): List<Spec> = instanceSpecListFor(allInstanceIds(imported))

/** Instance specs for a known set of instance ids. */
private fun instanceSpecListFor(instanceIds: List<String>): List<Spec> = instanceSpecs(instanceIds).map { spec ->
    Spec(
        key = spec.key,
        section = ImportSection.SERVICES,
        read = { JsonPrimitive(spec.read(it)) },
        write = { app, value -> spec.write(app, text(value)) },
        secret = spec.secret,
        // Same reasoning as tool toggles: an instance key only exists in a
        // backup when it carries a value worth keeping.
        forceApply = true,
    )
}

/**
 * The flat settings map for a backup.
 *
 * Only values that differ from the factory default are written: a backup states
 * what the user actually chose, and "Дополнить" on import is then a plain "apply
 * what is here".
 */
fun AppSettings.exportSnapshot(): JsonObject {
    val values = mutableMapOf<String, JsonElement>()
    for (spec in snapshotSpecs()) {
        val value = spec.read(this)
        if (value != spec.default()) values[spec.key] = value
    }
    for (spec in toolSpecs(emptySet())) {
        values[spec.key] = spec.read(this)
    }
    for (spec in instanceSpecListFor(localInstanceIds())) {
        val value = spec.read(this)
        if (value != JsonPrimitive("")) values[spec.key] = value
    }
    return JsonObject(values)
}

/** The per-instance part of a backup: one object per configured service instance. */
fun AppSettings.exportSnapshotInstances(): JsonObject {
    val instances = mutableMapOf<String, JsonElement>()
    for (instanceId in localInstanceIds()) {
        val fields = mutableMapOf<String, JsonElement>()
        for (spec in instanceSpecs(listOf(instanceId))) {
            val value = JsonPrimitive(spec.read(this))
            if (value.isNotBlankJson()) fields[spec.field] = value
        }
        if (fields.isNotEmpty()) instances[instanceId] = JsonObject(fields)
    }
    return JsonObject(instances)
}

private fun JsonPrimitive.isNotBlankJson(): Boolean = content.isNotBlank()

/** Formats a value for the diff list, hiding the content of secrets. */
private fun displayValue(value: JsonElement, secret: Boolean): String {
    val raw = value.jsonPrimitive.content
    if (!secret) return raw.ifBlank { "—" }
    return if (raw.isBlank()) "—" else "••••••••"
}

/**
 * Would this value be written in [mode]?
 *
 * Merge only takes non-defaults (that is the whole point of "Дополнить" on a
 * fresh install); Replace takes anything that differs from what is here now.
 */
private fun shouldApply(spec: Spec, incoming: JsonElement, current: JsonElement, mode: ImportMode): Boolean = when {
    spec.forceApply -> true
    mode == ImportMode.Merge -> incoming != spec.default()
    else -> incoming != current
}

/** Every spec that takes part in a diff or an import for this backup. */
private fun AppSettings.allSpecs(incomingKeys: Set<String>, imported: JsonObject): List<Spec> = snapshotSpecs() + toolSpecs(incomingKeys) + instanceSpecList(imported)

/**
 * Builds the pre-import diff: every setting, its current value, the value the
 * backup carries and whether the chosen mode would actually change it.
 */
fun AppSettings.previewSnapshot(
    imported: JsonObject,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    mode: ImportMode,
): ImportPreview {
    val flat = flatMapOf(imported)
    val specs = allSpecs(flat.keys, imported)
    val diff = specs.mapNotNull { spec ->
        if (spec.section !in sections) return@mapNotNull null
        val incoming = flat[spec.key] ?: return@mapNotNull null
        val current = spec.read(this)
        SettingDiff(
            key = spec.key,
            section = spec.section,
            current = displayValue(current, spec.secret),
            incoming = displayValue(incoming, spec.secret),
            secret = spec.secret,
            changed = shouldApply(spec, incoming, current, mode),
        )
    }
    val counts = mutableMapOf<ImportSection, String?>()
    diff.groupBy { it.section }.forEach { (section, rows) ->
        counts[section] = rows.count { it.changed }.toString()
    }
    return ImportPreview(
        mode = mode,
        diff = diff.sortedWith(compareBy({ it.section.name }, { it.key })),
        sectionCounts = counts,
        hasSnapshot = imported[SNAPSHOT_KEY] != null,
    )
}

/**
 * Applies a backup's flat settings map.
 *
 * Returns the number of settings that could not be written. The service list is
 * applied before the rest, because the per-instance keys only mean something once
 * the instances they belong to exist.
 */
fun AppSettings.applySnapshot(
    imported: JsonObject,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    mode: ImportMode,
): Int {
    val flat = flatMapOf(imported)
    if (flat.isEmpty()) return 0
    var errors = 0
    val specs = allSpecs(flat.keys, imported)

    for (spec in specs) {
        if (spec.section !in sections) continue
        val incoming = flat[spec.key]
        val apply = when {
            incoming != null -> shouldApply(spec, incoming, spec.read(this), mode)
            // Replace resets what the backup says nothing about — that is what
            // "the backup becomes the configuration" means.
            mode == ImportMode.Replace && !spec.forceApply -> true
            else -> false
        }
        if (!apply) continue
        val value = incoming ?: spec.default()
        try {
            spec.write(this, value)
        } catch (_: Exception) {
            errors++
        }
    }

    // Instances that exist here but not in the backup lose their own keys on
    // replace; the list itself was already replaced above.
    if (mode == ImportMode.Replace && ImportSection.SERVICES in sections) {
        val incomingIds = instanceIdsIn(imported).toSet()
        localInstanceIds().filterNot { it in incomingIds }.forEach { removeInstanceSettings(it) }
    }

    return errors
}

/** Convenience: the snapshot half of a backup (settings + instances). */
fun AppSettings.exportSnapshotDocument(): JsonObject = JsonObject(
    buildMap {
        put("version", JsonPrimitive(2))
        put(SNAPSHOT_KEY, exportSnapshot())
        put(SNAPSHOT_INSTANCES_KEY, exportSnapshotInstances())
    },
)
