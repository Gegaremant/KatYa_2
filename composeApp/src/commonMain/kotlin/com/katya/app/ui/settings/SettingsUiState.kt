package com.katya.app.ui.settings

import androidx.compose.runtime.Immutable
import com.katya.app.data.EmailAccount
import com.katya.app.data.EmailSyncState
import com.katya.app.data.HeartbeatLogEntry
import com.katya.app.data.MemoryEntry
import com.katya.app.data.QuickAction
import com.katya.app.data.ScheduledTask
import com.katya.app.data.Service
import com.katya.app.data.ServiceEntry
import com.katya.app.data.SmsSyncState
import com.katya.app.data.ThemeMode
import com.katya.app.inference.DownloadError
import com.katya.app.inference.LocalModel
import com.katya.app.network.dtos.SponsorsResponseDto
import com.katya.app.network.tools.ToolInfo
import com.katya.app.skills.RegistrySkillEntry
import com.katya.app.skills.SkillManifest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.StringResource

@Immutable
data class ConfiguredServiceEntry(
    val instanceId: String,
    val service: Service,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: SettingsModel? = null,
    val models: ImmutableList<SettingsModel> = persistentListOf(),
)

enum class ConnectionStatus {
    Unknown,
    Checking,
    Connected,
    ErrorInvalidKey,
    ErrorQuotaExhausted,
    ErrorRateLimited,
    ErrorConnectionFailed,
    ErrorLocalNetworkDenied,
    Error,
}

enum class SettingsTab {
    General,
    Agent,
    Services,
    Tools,
    Integrations,
    Servers,
}

@Immutable
data class SettingsUiState(
    val currentTab: SettingsTab = SettingsTab.Services,
    val configuredServices: ImmutableList<ConfiguredServiceEntry> = persistentListOf(),
    val expandedServiceId: String? = null,
    val availableServicesToAdd: ImmutableList<Service> = persistentListOf(),
    val quickActions: ImmutableList<QuickAction> = persistentListOf(),
    val tools: ImmutableList<ToolInfo> = persistentListOf(),
    val soulText: String = "",
    val agentMode: com.katya.app.data.AgentMode = com.katya.app.data.AgentMode.CONVERSATIONAL,
    val sendDelayMs: Long = 1000L,
    val sttEngine: com.katya.app.data.SttEngine = com.katya.app.data.SttEngine.SYSTEM,
    val ttsEngine: com.katya.app.data.TtsEngine = com.katya.app.data.TtsEngine.SYSTEM,
    val sysTtsPitch: Float = 1.0f,
    val sysTtsRate: Float = 1.5f,
    val cloudSttUrl: String = "https://api.openai.com/v1/audio/transcriptions",
    val cloudSttKey: String = "",
    val cloudSttModel: String = "whisper-1",
    val cloudTtsUrl: String = "https://api.openai.com/v1/audio/speech",
    val cloudTtsKey: String = "",
    val cloudTtsModel: String = "tts-1",
    val cloudTtsVoice: String = "alloy",
    val ttsEngineInstalled: Boolean = true,
    val distro: com.katya.app.data.Distro = com.katya.app.data.Distro.DEBIAN,
    val isDynamicUiEnabled: Boolean = true,
    val isAgentVisibilityEnabled: Boolean = true,
    val isVoiceResponseEnabled: Boolean = true,
    val isVoiceRecognitionEnabled: Boolean = true,
    val isWakeWordEnabled: Boolean = false,
    val wakeWordModelLang: String = "ru",
    val wakeWordTrigger: String = "привет катя",
    val isWakeWordVibrationEnabled: Boolean = true,
    val isWakeWordSoundEnabled: Boolean = true,
    val isWatchIntegrationEnabled: Boolean = false,
    val voskDownloadProgress: Float? = null,
    val isVoskDownloading: Boolean = false,
    val isVoskReady: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val isMemoryEnabled: Boolean = true,
    val memories: ImmutableList<MemoryEntry> = persistentListOf(),
    val isSchedulingEnabled: Boolean = true,
    val scheduledTasks: ImmutableList<ScheduledTask> = persistentListOf(),
    val isDaemonEnabled: Boolean = false,
    val showDaemonToggle: Boolean = false,
    val isHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: ImmutableList<HeartbeatLogEntry> = persistentListOf(),
    val heartbeatServiceEntries: ImmutableList<ServiceEntry> = persistentListOf(),
    val heartbeatSelectedInstanceId: String? = null,
    val isRefreshingHeartbeat: Boolean = false,
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val emailAccounts: ImmutableList<EmailAccount> = persistentListOf(),
    val emailPollIntervalMinutes: Int = 15,
    val emailPendingCount: Int = 0,
    val emailSyncStates: ImmutableMap<String, EmailSyncState> = persistentMapOf(),
    val refreshingEmailAccountIds: ImmutableSet<String> = persistentSetOf(),
    val showSmsSection: Boolean = false,
    val isSmsEnabled: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val smsPollIntervalMinutes: Int = 15,
    val smsPendingCount: Int = 0,
    val smsSyncState: SmsSyncState = SmsSyncState(),
    val isRefreshingSms: Boolean = false,
    val isSmsSendEnabled: Boolean = false,
    val smsSendPermissionGranted: Boolean = false,
    val showNotificationsSection: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val notificationListenerAccessGranted: Boolean = false,
    val notificationListenerBound: Boolean = false,
    val notificationPendingCount: Int = 0,
    val isVlessEnabled: Boolean = false,
    val vlessUri: String = "",
    val isVlessConnected: Boolean = false,
    // Headless DeepSeek sign-in, driven from the service card itself.
    // dsAuthInstanceId is the account the login belongs to — the old dialog
    // always wrote to the first DeepSeek instance, so the second account got
    // the first one's session.
    val dsAuthInstanceId: String = "",
    val dsAuthEmail: String = "",
    val dsAuthPassword: String = "",
    val dsAuthStatus: String = "",
    val dsAuthRunning: Boolean = false,
    val isFreeFallbackEnabled: Boolean = true,
    val uiScale: Float = 1.0f,
    val showUiScale: Boolean = false,
    val mcpServers: ImmutableList<McpServerUiState> = persistentListOf(),
    val showAddMcpServerDialog: Boolean = false,
    val skills: ImmutableList<SkillManifest> = persistentListOf(),
    val showAddSkillDialog: Boolean = false,
    val isInstallingSkill: Boolean = false,
    val skillInstallError: String? = null,
    val browsableSkills: ImmutableList<RegistrySkillEntry> = persistentListOf(),
    val isBrowsingSkills: Boolean = false,
    val browseSkillsFailed: Boolean = false,
    val skillsBulk: BulkProgress = BulkProgress(),
    val mcpBulk: BulkProgress = BulkProgress(),
    val localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    val totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    val localFreeSpaceBytes: Long = 0L,
    val localDownloadingModelIds: ImmutableSet<String> = persistentSetOf(),
    val localDownloadProgresses: ImmutableMap<String, Float> = persistentMapOf(),
    val localDownloadErrors: ImmutableMap<String, DownloadError> = persistentMapOf(),
    val modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    // Piper conversational voices
    val piperInstalledVoices: ImmutableList<com.katya.app.tts.PiperVoiceInfo> = persistentListOf(),
    val piperSelectedVoice: String? = null,
    val piperVoiceUrl: String = "",
    val piperDownloadingBase: String? = null,
    val piperDownloadProgress: Float? = null,
    val piperDownloadError: String? = null,
    val currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pendingDeletion: PendingDeletion? = null,
    val monitorOverlayMode: com.katya.app.data.MonitorOverlayMode = com.katya.app.data.MonitorOverlayMode.OFF,
    val hfRepoUrl: String = "",
    val isFetchingHfModels: Boolean = false,
    val hfError: String? = null,
    val hfModels: ImmutableList<LocalModel> = persistentListOf(),
)

@Immutable
data class McpServerUiState(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val connectionStatus: McpConnectionStatus,
    val tools: ImmutableList<ToolInfo>,
)

enum class McpConnectionStatus {
    Unknown,
    Connecting,
    Connected,
    Error,
}

/** Why a bulk "add everything" run finished. The UI turns this into a localized message. */
enum class BulkResult {
    /** Nothing has run yet. */
    None,

    /** Everything the run could offer was already there. */
    AllInstalled,

    /** The skill catalogue could not be fetched (no network, registries empty). */
    NoCatalogue,

    /** There are no MCP servers configured at all. */
    NoServers,

    /** The run finished: [BulkProgress.ok] succeeded, [BulkProgress.failed] failed. */
    Done,
}

/**
 * Progress of a bulk "add everything" run (feedback #5, #6).
 *
 * [current] is a skill id or a server name — rendered verbatim because those are
 * user data, not UI copy.
 */
@Immutable
data class BulkProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val ok: Int = 0,
    val failed: Int = 0,
    val result: BulkResult = BulkResult.None,
)

sealed interface PendingDeletion {
    data class Memory(val key: String) : PendingDeletion
    data class QuickAction(val action: com.katya.app.data.QuickAction, val index: Int) : PendingDeletion
    data class Task(val id: String) : PendingDeletion
    data class EmailAccount(val id: String) : PendingDeletion
    data class Service(val instanceId: String) : PendingDeletion
    data class McpServer(val serverId: String) : PendingDeletion
    data class Skill(val id: String) : PendingDeletion
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class PartialSuccess(val errorCount: Int) : ImportResult
    data object Failure : ImportResult
}

@Immutable
data class SettingsModel(
    val id: String,
    val subtitle: String,
    val description: String? = null,
    val descriptionRes: StringResource? = null,
    val isSelected: Boolean = false,
    /** Human-readable name to display in place of [id], when the provider exposes one. */
    val displayName: String? = null,
    /** Max context window in tokens, from the API or the curated catalog. */
    val contextWindow: Long? = null,
    /** Release date as "YYYY-MM" or "YYYY-MM-DD", from the API or the curated catalog. */
    val releaseDate: String? = null,
    /** Parameter count, pre-formatted for display (e.g. "70B", "8B", "3.3B"). */
    val parameterCount: String? = null,
    /** LMArena Elo score, or null when unknown. */
    val arenaScore: Int? = null,
    /**
     * Feedback #13: whether this model can actually be driven with tools.
     *
     * `null` means "not advertised" — the common case, so it must never be read as
     * "no tools". `false` is a definite no: the provider told us, and sending a tools
     * payload to such a model produces a reply that looks like a tool call but never
     * executes anything.
     */
    val supportsTools: Boolean? = null,
)
