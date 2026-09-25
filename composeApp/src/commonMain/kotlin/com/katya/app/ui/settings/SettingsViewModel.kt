package com.katya.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katya.app.BackupPayload
import com.katya.app.DaemonController
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.data.DataRepository
import com.katya.app.data.EmailAccount
import com.katya.app.data.ImportMode
import com.katya.app.data.ImportPreviews
import com.katya.app.data.ImportSection
import com.katya.app.data.Service
import com.katya.app.data.TaskScheduler
import com.katya.app.data.ThemeMode
import com.katya.app.data.applyPreparedImport
import com.katya.app.data.supportsAgenticFlows
import com.katya.app.device.DeviceAdminManager
import com.katya.app.getBackgroundDispatcher
import com.katya.app.httpClient
import com.katya.app.inference.LocalModel
import com.katya.app.isEmailSupported
import com.katya.app.isNotificationsSupported
import com.katya.app.isSmsSupported
import com.katya.app.mcp.PopularMcpServer
import com.katya.app.network.AnthropicInsufficientCreditsException
import com.katya.app.network.AnthropicInvalidApiKeyException
import com.katya.app.network.AnthropicOverloadedException
import com.katya.app.network.AnthropicRateLimitExceededException
import com.katya.app.network.GeminiInvalidApiKeyException
import com.katya.app.network.GeminiRateLimitExceededException
import com.katya.app.network.OpenAICompatibleConnectionException
import com.katya.app.network.OpenAICompatibleInvalidApiKeyException
import com.katya.app.network.OpenAICompatibleQuotaExhaustedException
import com.katya.app.network.OpenAICompatibleRateLimitExceededException
import com.katya.app.network.dtos.SponsorsResponseDto
import com.katya.app.skills.parseGitHubSkillUrl
import com.katya.app.tools.LocalNetworkPermissionController
import com.katya.app.tools.NotificationPermissionController
import com.katya.app.tools.isLocalNetworkUrl
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.error_unknown
import katya.composeapp.generated.resources.error_unrecognized_github_repo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
data class HuggingFaceTreeEntry(
    val type: String,
    val oid: String? = null,
    val size: Long = 0L,
    val path: String,
)

class SettingsViewModel(
    private val appSettings: com.katya.app.data.AppSettings,
    private val wakeWordPlatform: com.katya.app.stt.WakeWordPlatform,
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
    private val notificationPermissionController: NotificationPermissionController,
    private val taskScheduler: TaskScheduler,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
    private val localNetworkPermissionController: LocalNetworkPermissionController = LocalNetworkPermissionController(),
    private val systemRoleController: com.katya.app.tools.SystemRoleController = com.katya.app.tools.SystemRoleController(),
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private var pendingDeleteJob: Job? = null
    private var piperVoiceUrlText = ""

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        soulText = dataRepository.getSoulText(),
        agentMode = dataRepository.getAgentMode(),
        sendDelayMs = dataRepository.getSendDelayMs(),
        sttEngine = dataRepository.getSttEngine(),
        ttsEngine = dataRepository.getTtsEngine(),
        sysTtsPitch = dataRepository.getSysTtsPitch(),
        sysTtsRate = dataRepository.getSysTtsRate(),
        ttsEngineInstalled = isTtsEngineInstalled(dataRepository.getTtsEngine()),
        cloudSttUrl = dataRepository.getCloudSttUrl(),
        cloudSttKey = dataRepository.getCloudSttKey(),
        cloudSttModel = dataRepository.getCloudSttModel(),
        cloudTtsUrl = dataRepository.getCloudTtsUrl(),
        cloudTtsKey = dataRepository.getCloudTtsKey(),
        cloudTtsModel = dataRepository.getCloudTtsModel(),
        cloudTtsVoice = dataRepository.getCloudTtsVoice(),
        distro = dataRepository.getDistro(),
        isDynamicUiEnabled = dataRepository.isDynamicUiEnabled(),
        isAgentVisibilityEnabled = dataRepository.isAgentVisibilityEnabled(),
        isVoiceResponseEnabled = dataRepository.isVoiceResponseEnabled(),
        isVoiceRecognitionEnabled = dataRepository.isVoiceRecognitionEnabled(),
        isWatchIntegrationEnabled = dataRepository.isWatchIntegrationEnabled(),
        isWakeWordEnabled = dataRepository.isWakeWordEnabled(),
        wakeWordModelLang = dataRepository.getWakeWordModelLang(),
        wakeWordTrigger = dataRepository.getWakeWordTrigger(),
        isWakeWordVibrationEnabled = dataRepository.isWakeWordVibrationEnabled(),
        isWakeWordSoundEnabled = dataRepository.isWakeWordSoundEnabled(),
        themeMode = dataRepository.getThemeMode(),
        isMemoryEnabled = dataRepository.isMemoryEnabled(),
        memories = dataRepository.getMemories().toImmutableList(),
        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
        scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(),
        isDaemonEnabled = dataRepository.isDaemonEnabled(),
        isVlessEnabled = dataRepository.isVlessEnabled(),
        vlessUri = dataRepository.getVlessUri(),
        isVlessConnected = dataRepository.isVlessConnectedFlow.value,
        showDaemonToggle = currentPlatform is Platform.Mobile.Android,
        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
        heartbeatIntervalMinutes = dataRepository.getHeartbeatConfig().intervalMinutes,
        heartbeatActiveHoursStart = dataRepository.getHeartbeatConfig().activeHoursStart,
        heartbeatActiveHoursEnd = dataRepository.getHeartbeatConfig().activeHoursEnd,
        heartbeatPrompt = dataRepository.getHeartbeatPrompt(),
        heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
        heartbeatServiceEntries = dataRepository.getServiceEntries()
            .filter { supportsAgenticFlows(it.serviceId, it.modelId) }
            .toImmutableList(),
        heartbeatSelectedInstanceId = dataRepository.getHeartbeatInstanceId()?.takeIf { id ->
            dataRepository.getServiceEntries().any { it.instanceId == id }
        }.also { validId ->
            val savedId = dataRepository.getHeartbeatInstanceId()
            if (savedId != null && validId == null) dataRepository.setHeartbeatInstanceId(null)
        },
        isEmailEnabled = dataRepository.isEmailEnabled(),
        showEmailToggle = isEmailSupported,
        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
        emailPollIntervalMinutes = dataRepository.getEmailPollIntervalMinutes(),
        emailPendingCount = dataRepository.getPendingEmailCount(),
        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
        showSmsSection = isSmsSupported,
        isSmsEnabled = dataRepository.isSmsEnabled(),
        smsPermissionGranted = dataRepository.hasSmsPermission(),
        smsPollIntervalMinutes = dataRepository.getSmsPollIntervalMinutes(),
        smsPendingCount = dataRepository.getPendingSmsCount(),
        smsSyncState = dataRepository.getSmsSyncState(),
        isSmsSendEnabled = dataRepository.isSmsSendEnabled(),
        smsSendPermissionGranted = dataRepository.hasSmsSendPermission(),
        showNotificationsSection = isNotificationsSupported,
        isNotificationsEnabled = dataRepository.isNotificationsEnabled(),
        notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
        notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
        notificationPendingCount = dataRepository.getPendingNotificationCount(),
        isFreeFallbackEnabled = dataRepository.isFreeFallbackEnabled(),
        uiScale = dataRepository.getUiScale(),
        showUiScale = currentPlatform is Platform.Desktop,
        mcpServers = buildMcpServerEntries().toImmutableList(),
        skills = dataRepository.getInstalledSkills().toImmutableList(),
        localAvailableModels = dataRepository.getLocalAvailableModels().toImmutableList(),
        totalDeviceMemoryBytes = dataRepository.getTotalDeviceMemoryBytes(),
        localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes(),
        localDownloadingModelIds = dataRepository.getLocalDownloadingModelIds()?.value?.toImmutableSet() ?: persistentSetOf(),
        localDownloadProgresses = dataRepository.getLocalDownloadProgresses()?.value?.toImmutableMap() ?: persistentMapOf(),
        localDownloadErrors = dataRepository.getLocalDownloadErrors()?.value?.toImmutableMap() ?: persistentMapOf(),
        modelContextTokens = buildModelContextTokensMap(),
        piperInstalledVoices = dataRepository.getPiperInstalledVoices().toImmutableList(),
        piperSelectedVoice = dataRepository.getPiperSelectedVoice(),
        piperVoiceUrl = piperVoiceUrlText,
        piperDownloadingBase = dataRepository.getPiperDownloadingBaseName()?.value,
        piperDownloadProgress = dataRepository.getPiperDownloadProgress()?.value,
        piperDownloadError = dataRepository.getPiperDownloadError()?.value,
    )

    // Bound once so downstream Compose skipping works — a new SettingsActions
    // instance on every state emission would defeat it.
    val actions: SettingsActions = SettingsActions(
        onSelectTab = ::onSelectTab,
        onAddService = ::onAddService,
        onRemoveService = ::onRemoveService,
        onReorderServices = ::onReorderServices,
        onExpandService = ::onExpandService,
        onChangeApiKey = ::onChangeApiKey,
        onChangeBaseUrl = ::onChangeBaseUrl,
        onSelectModel = ::onSelectModel,
        onToggleTool = ::onToggleTool,
        onSaveSoul = ::onSaveSoul,
        onChangeAgentMode = ::onChangeAgentMode,
        onChangeSendDelayMs = ::onChangeSendDelayMs,
        onChangeSttEngine = ::onChangeSttEngine,
        onChangeTtsEngine = ::onChangeTtsEngine,
        onChangeSysTtsPitch = ::onChangeSysTtsPitch,
        onChangeSysTtsRate = ::onChangeSysTtsRate,
        onChangeCloudSttUrl = ::onChangeCloudSttUrl,
        onChangeCloudSttKey = ::onChangeCloudSttKey,
        onChangeCloudSttModel = ::onChangeCloudSttModel,
        onChangeCloudTtsUrl = ::onChangeCloudTtsUrl,
        onChangeCloudTtsKey = ::onChangeCloudTtsKey,
        onChangeCloudTtsModel = ::onChangeCloudTtsModel,
        onChangeCloudTtsVoice = ::onChangeCloudTtsVoice,
        onChangeDistro = ::onChangeDistro,
        onToggleDynamicUi = ::onToggleDynamicUi,
        onToggleAgentVisibility = ::onToggleAgentVisibility,
        onToggleVoiceResponse = ::onToggleVoiceResponse,
        onToggleVoiceRecognition = ::onToggleVoiceRecognition,
        onToggleWakeWord = ::onToggleWakeWord,
        onChangeWakeWordTrigger = ::onChangeWakeWordTrigger,
        onSelectWakeWordModelLang = ::onChangeWakeWordModelLang,
        onToggleWakeWordVibration = ::onToggleWakeWordVibration,
        onToggleWakeWordSound = ::onToggleWakeWordSound,
        onToggleWatchIntegration = ::onToggleWatchIntegration,
        onAddQuickAction = ::onAddQuickAction,
        onUpdateQuickAction = ::onUpdateQuickAction,
        onDeleteQuickAction = ::onDeleteQuickAction,
        onChangeThemeMode = ::onChangeThemeMode,
        onToggleMemory = ::onToggleMemory,
        onDeleteMemory = ::onDeleteMemory,
        onUpdateMemory = ::onUpdateMemory,
        onAddMemory = ::onAddMemory,
        onOpenDeviceAdminSettings = ::onOpenDeviceAdminSettings,
        onOpenTrustAgentSettings = ::onOpenTrustAgentSettings,
        onToggleScheduling = ::onToggleScheduling,
        onAddScheduledTask = ::onAddScheduledTask,
        onUpdateScheduledTask = ::onUpdateScheduledTask,
        onCancelTask = ::onCancelTask,
        onToggleDaemon = ::onToggleDaemon,
        onToggleVless = ::onToggleVless,
        onChangeVlessUri = ::onChangeVlessUri,
        onStartDeepSeekAuth = ::onStartDeepSeekAuth,
        onDeepSeekAuthStatus = ::onDeepSeekAuthStatus,
        onStopDeepSeekAuth = ::onStopDeepSeekAuth,
        onDeepSeekAuthSucceeded = ::onDeepSeekAuthSucceeded,
        onToggleHeartbeat = ::onToggleHeartbeat,
        onDownloadVosk = ::onDownloadVosk,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = ::onChangeHeartbeatService,
        onRefreshHeartbeat = ::onRefreshHeartbeat,
        onToggleEmail = ::onToggleEmail,
        onAddEmailAccount = ::onAddEmailAccount,
        onRemoveEmailAccount = ::onRemoveEmailAccount,
        onChangeEmailPollInterval = ::onChangeEmailPollInterval,
        onRefreshEmailAccount = ::onRefreshEmailAccount,
        onToggleSms = ::onToggleSms,
        onChangeSmsPollInterval = ::onChangeSmsPollInterval,
        onRefreshSms = ::onRefreshSms,
        onToggleSmsSend = ::onToggleSmsSend,
        onToggleNotifications = ::onToggleNotifications,
        onOpenNotificationListenerSettings = ::onOpenNotificationListenerSettings,
        onOpenAppPermissionSettings = ::onOpenAppPermissionSettings,
        onRecheckLocalNetworkPermission = ::onRecheckLocalNetworkPermission,
        onClearPendingNotifications = ::onClearPendingNotifications,
        onToggleFreeFallback = ::onToggleFreeFallback,
        onChangeUiScale = ::onChangeUiScale,
        onAddMcpServer = ::onAddMcpServer,
        onRemoveMcpServer = ::onRemoveMcpServer,
        onToggleMcpServer = ::onToggleMcpServer,
        onRefreshMcpServer = ::onRefreshMcpServer,
        onShowAddMcpServerDialog = ::onShowAddMcpServerDialog,
        onAddPopularMcpServer = ::onAddPopularMcpServer,
        onConnectAllMcpServers = ::onConnectAllMcpServers,
        onUninstallSkill = ::onUninstallSkill,
        onShowAddSkillDialog = ::onShowAddSkillDialog,
        onInstallGitHubSkill = ::onInstallGitHubSkill,
        onInstallBrowsedSkill = ::onInstallBrowsedSkill,
        onInstallAllBrowsedSkills = ::onInstallAllBrowsedSkills,
        onDownloadLocalModel = ::onDownloadLocalModel,
        onCancelLocalModelDownload = ::onCancelLocalModelDownload,
        onImportLocalModel = ::onImportLocalModel,
        onDeleteLocalModel = ::onDeleteLocalModel,
        onSaveLocalModelToDevice = ::onSaveLocalModelToDevice,
        onChangeModelContextTokens = ::onChangeModelContextTokens,
        onExportSettings = ::onExportSettings,
        onPrepareExport = ::onPrepareExport,
        onPrepareImport = ::onPrepareImport,
        onImportSettings = ::onImportSettings,
        onChangeMonitorOverlayMode = ::onChangeMonitorOverlayMode,
        onUndoDelete = ::onUndoDelete,
        onChangeHfRepoUrl = ::onChangeHfRepoUrl,
        onFetchHfModels = ::onFetchHfModels,
        onChangePiperVoiceUrl = ::onChangePiperVoiceUrl,
        onDownloadPiperVoice = ::onDownloadPiperVoice,
        onSelectPiperVoice = ::onSelectPiperVoice,
        onImportPiperVoice = ::onImportPiperVoice,
        onDeletePiperVoice = ::onDeletePiperVoice,
        onExportPiperVoice = ::onExportPiperVoice,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    init {
        viewModelScope.launch {
            dataRepository.isVlessConnectedFlow.collect { connected ->
                _state.update { it.copy(isVlessConnected = connected) }
            }
        }

        // Keep the scheduled-tasks list live: the scheduler completes/cancels tasks
        // in the background, and previously the UI only refreshed on manual actions,
        // so a task that fired never disappeared from Settings until app restart.
        viewModelScope.launch {
            dataRepository.scheduledTasksFlow.collect { tasks ->
                _state.update { it.copy(scheduledTasks = tasks.toImmutableList()) }
            }
        }

        runCatching {
            if (_state.value.isWakeWordEnabled) {
                wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), _state.value.wakeWordTrigger)
            }
        }.onFailure { e ->
            com.katya.app.tools.AppLogger.e(
                "WakeWord",
                "Wake-word init failed in SettingsViewModel, continuing without it: ${e.message}",
            )
        }

        viewModelScope.launch {
            wakeWordPlatform.isDownloading.collect { isDownloading ->
                _state.update {
                    it.copy(
                        isVoskDownloading = isDownloading,
                        isVoskReady = wakeWordPlatform.isModelReady(getModelUrl(it.wakeWordModelLang)),
                    )
                }
            }
        }
        viewModelScope.launch {
            wakeWordPlatform.downloadProgress.collect { progress ->
                _state.update { it.copy(voskDownloadProgress = progress) }
            }
        }

        // Initial check for Vosk readiness
        _state.update {
            it.copy(isVoskReady = wakeWordPlatform.isModelReady(getModelUrl(it.wakeWordModelLang)))
        }

        // Observe download state from the engine singleton (survives activity recreation)
        val downloadingFlow = dataRepository.getLocalDownloadingModelIds() ?: flowOf(emptySet())
        val progressFlow = dataRepository.getLocalDownloadProgresses() ?: flowOf(emptyMap())
        val errorFlow = dataRepository.getLocalDownloadErrors() ?: flowOf(emptyMap())
        viewModelScope.launch {
            combine(downloadingFlow, progressFlow, errorFlow) { modelIds, progresses, errors ->
                Triple(modelIds, progresses, errors)
            }.collect { (modelIds, progresses, errors) ->
                val prevModelIds = _state.value.localDownloadingModelIds
                _state.update {
                    it.copy(
                        localDownloadingModelIds = modelIds.toImmutableSet(),
                        localDownloadProgresses = progresses.toImmutableMap(),
                        localDownloadErrors = errors.toImmutableMap(),
                    )
                }
                if (prevModelIds.size > modelIds.size) {
                    // A download finished or cancelled — refresh
                    _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    refreshServiceList()
                    _state.value.configuredServices
                        .filter { it.service.isOnDevice }
                        .forEach { checkConnection(it.instanceId, it.service) }
                }
            }
        }

        // Keep Piper voice download state live: when a download ends, re-list voices.
        viewModelScope.launch {
            combine(
                dataRepository.getPiperDownloadingBaseName() ?: flowOf(null),
                dataRepository.getPiperDownloadProgress() ?: flowOf(null),
                dataRepository.getPiperDownloadError() ?: flowOf(null),
            ) { base, progress, error ->
                Triple(base, progress, error)
            }.collect { (base, progress, error) ->
                val prevBase = _state.value.piperDownloadingBase
                _state.update { it.copy(piperDownloadingBase = base, piperDownloadProgress = progress, piperDownloadError = error) }
                if (prevBase != null && base == null) refreshPiperVoices()
            }
        }
    }

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkAllConnections()
            connectEnabledMcpServers()
            fetchSponsors()
        }
        // Re-read notification listener state every time the screen becomes visible:
        // the user may have toggled access in system settings while we were backgrounded.
        if (isNotificationsSupported) {
            _state.update {
                it.copy(
                    notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
                    notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
                    notificationPendingCount = dataRepository.getPendingNotificationCount(),
                )
            }
        }
        // Re-read SMS permissions the same way: the user may have granted/revoked
        // them in the OS dialog or app settings, so the section must reflect the
        // real state right away instead of waiting for a refresh or app restart.
        if (isSmsSupported) {
            _state.update {
                it.copy(
                    smsPermissionGranted = dataRepository.hasSmsPermission(),
                    smsSendPermissionGranted = dataRepository.hasSmsSendPermission(),
                )
            }
        }
        // Re-check whether the selected TTS engine (e.g. RHVoice) is installed:
        // the user may have installed it via the market link in Settings and
        // returned to the app without restarting.
        _state.update {
            it.copy(ttsEngineInstalled = isTtsEngineInstalled(dataRepository.getTtsEngine()))
        }
    }

    private fun fetchSponsors() {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                val client = httpClient {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }
                val response = client.get("https://ghs.vercel.app/v3/sponsors/SimonSchubert")
                if (response.status.isSuccess()) {
                    val dto = response.body<SponsorsResponseDto>()
                    _state.update {
                        it.copy(
                            currentSponsors = dto.sponsors.current.toImmutableList(),
                            pastSponsors = dto.sponsors.past.toImmutableList(),
                        )
                    }
                }
            } catch (_: Exception) {
                // Silently ignore - sponsors are non-critical
            }
        }
    }

    private fun buildConfiguredServiceEntries(): List<ConfiguredServiceEntry> = dataRepository.getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val models = dataRepository.getInstanceModels(instance.instanceId, service).value
        ConfiguredServiceEntry(
            instanceId = instance.instanceId,
            service = service,
            apiKey = dataRepository.getInstanceApiKey(instance.instanceId),
            baseUrl = dataRepository.getInstanceBaseUrl(instance.instanceId, service),
            selectedModel = models.firstOrNull { it.isSelected },
            models = models.toImmutableList(),
        )
    }

    private fun computeAvailableServices(): List<Service> {
        // Allow all non-Free services (multiple instances of same type are allowed)
        // Pin OpenAI-Compatible and LiteRT (Local Model) to the top, then the featured Atlas Cloud
        // provider, then sort the rest alphabetically
        // Hide on-device services on platforms that don't support them
        return Service.all
            .filter { !it.isOnDevice || dataRepository.isLocalInferenceAvailable() }
            .sortedWith(
                compareBy<Service> {
                    when {
                        it is Service.OpenAICompatible || it.isOnDevice -> 0
                        it is Service.AtlasCloud -> 1
                        else -> 2
                    }
                }.thenBy { it.displayName },
            )
    }

    private fun refreshServiceList() {
        _state.update { current ->
            val existingStatuses = current.configuredServices.associate { it.instanceId to it.connectionStatus }
            val newEntries = buildConfiguredServiceEntries().map { entry ->
                val preservedStatus = existingStatuses[entry.instanceId]
                if (preservedStatus != null) entry.copy(connectionStatus = preservedStatus) else entry
            }
            current.copy(
                configuredServices = newEntries.toImmutableList(),
                availableServicesToAdd = computeAvailableServices().toImmutableList(),
            )
        }
    }

    private fun onSelectTab(tab: SettingsTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    private fun onAddService(service: Service) {
        val instance = dataRepository.addConfiguredService(service.id)
        refreshServiceList()
        _state.update { it.copy(expandedServiceId = instance.instanceId) }
        checkConnection(instance.instanceId, service)
    }

    private fun onRemoveService(instanceId: String) {
        commitPendingDeletion()
        _state.update {
            it.copy(
                expandedServiceId = if (it.expandedServiceId == instanceId) null else it.expandedServiceId,
                pendingDeletion = PendingDeletion.Service(instanceId),
            )
        }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Service(instanceId))
        }
    }

    private fun onReorderServices(orderedIds: List<String>) {
        dataRepository.reorderConfiguredServices(orderedIds)
        refreshServiceList()
    }

    private fun onExpandService(instanceId: String?) {
        _state.update { it.copy(expandedServiceId = instanceId) }
        if (instanceId != null) {
            refreshInstanceModels(instanceId)
        }
    }

    private fun refreshInstanceModels(instanceId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        val models = dataRepository.getInstanceModels(instanceId, entry.service).value
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(
                            models = models.toImmutableList(),
                            selectedModel = models.firstOrNull { it.isSelected },
                        )
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onChangeApiKey(instanceId: String, apiKey: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceApiKey(instanceId, apiKey)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(apiKey = apiKey, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onChangeBaseUrl(instanceId: String, baseUrl: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceBaseUrl(instanceId, baseUrl)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(baseUrl = baseUrl, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onSelectModel(instanceId: String, modelId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceSelectedModel(instanceId, entry.service, modelId)
        refreshInstanceModels(instanceId)
    }

    private fun onSaveSoul(text: String) {
        dataRepository.setSoulText(text)
        _state.update { it.copy(soulText = text) }
    }

    private fun onChangeAgentMode(mode: com.katya.app.data.AgentMode) {
        dataRepository.setAgentMode(mode)
        _state.update { it.copy(agentMode = mode) }
    }

    private fun onChangeSendDelayMs(delay: Long) {
        dataRepository.setSendDelayMs(delay)
        _state.update { it.copy(sendDelayMs = delay) }
    }

    private fun onChangeSttEngine(engine: com.katya.app.data.SttEngine) {
        dataRepository.setSttEngine(engine)
        _state.update { it.copy(sttEngine = engine) }
    }

    private fun onChangeTtsEngine(engine: com.katya.app.data.TtsEngine) {
        dataRepository.setTtsEngine(engine)
        _state.update { it.copy(ttsEngine = engine, ttsEngineInstalled = isTtsEngineInstalled(engine)) }
    }

    private fun onChangeSysTtsPitch(pitch: Float) {
        dataRepository.setSysTtsPitch(pitch)
        _state.update { it.copy(sysTtsPitch = pitch) }
    }

    private fun onChangeSysTtsRate(rate: Float) {
        dataRepository.setSysTtsRate(rate)
        _state.update { it.copy(sysTtsRate = rate) }
    }

    private fun onChangeCloudSttUrl(url: String) {
        dataRepository.setCloudSttUrl(url)
        _state.update { it.copy(cloudSttUrl = url) }
    }

    private fun onChangeCloudSttKey(key: String) {
        dataRepository.setCloudSttKey(key)
        _state.update { it.copy(cloudSttKey = key) }
    }

    private fun onChangeCloudSttModel(model: String) {
        dataRepository.setCloudSttModel(model)
        _state.update { it.copy(cloudSttModel = model) }
    }

    private fun onChangeCloudTtsUrl(url: String) {
        dataRepository.setCloudTtsUrl(url)
        _state.update { it.copy(cloudTtsUrl = url) }
    }

    private fun onChangeCloudTtsKey(key: String) {
        dataRepository.setCloudTtsKey(key)
        _state.update { it.copy(cloudTtsKey = key) }
    }

    private fun onChangeCloudTtsModel(model: String) {
        dataRepository.setCloudTtsModel(model)
        _state.update { it.copy(cloudTtsModel = model) }
    }

    private fun onChangeCloudTtsVoice(voice: String) {
        dataRepository.setCloudTtsVoice(voice)
        _state.update { it.copy(cloudTtsVoice = voice) }
    }

    private fun isTtsEngineInstalled(engine: com.katya.app.data.TtsEngine): Boolean = when (engine) {
        com.katya.app.data.TtsEngine.LOCAL -> true // Since we will ship local voices or download them internally
        else -> true
    }

    private fun onChangeDistro(distro: com.katya.app.data.Distro) {
        dataRepository.setDistro(distro)
        _state.update { it.copy(distro = distro) }
    }

    private fun onToggleDynamicUi(enabled: Boolean) {
        dataRepository.setDynamicUiEnabled(enabled)
        _state.update { it.copy(isDynamicUiEnabled = enabled) }
    }

    private fun onToggleAgentVisibility(enabled: Boolean) {
        dataRepository.setAgentVisibilityEnabled(enabled)
        _state.update { it.copy(isAgentVisibilityEnabled = enabled) }
    }

    private fun onToggleVoiceResponse(enabled: Boolean) {
        dataRepository.setVoiceResponseEnabled(enabled)
        _state.update { it.copy(isVoiceResponseEnabled = enabled) }
    }

    private fun onToggleVoiceRecognition(enabled: Boolean) {
        dataRepository.setVoiceRecognitionEnabled(enabled)
        _state.update { it.copy(isVoiceRecognitionEnabled = enabled) }
    }

    private fun onToggleWatchIntegration(enabled: Boolean) {
        dataRepository.setWatchIntegrationEnabled(enabled)
        _state.update { it.copy(isWatchIntegrationEnabled = enabled) }
    }

    private fun onAddQuickAction(action: com.katya.app.data.QuickAction) {
        val actions = dataRepository.getQuickActions().toMutableList()
        actions.add(action)
        dataRepository.setQuickActions(actions)
        _state.update { it.copy(quickActions = actions.toImmutableList()) }
    }

    private fun onUpdateQuickAction(action: com.katya.app.data.QuickAction) {
        val actions = dataRepository.getQuickActions().toMutableList()
        val index = actions.indexOfFirst { it.id == action.id }
        if (index != -1) {
            actions[index] = action
            dataRepository.setQuickActions(actions)
            _state.update { it.copy(quickActions = actions.toImmutableList()) }
        }
    }

    private fun onDeleteQuickAction(id: String) {
        commitPendingDeletion()
        val actions = dataRepository.getQuickActions().toMutableList()
        val index = actions.indexOfFirst { it.id == id }
        if (index != -1) {
            val deletedAction = actions.removeAt(index)
            dataRepository.setQuickActions(actions)
            _state.update { it.copy(quickActions = actions.toImmutableList()) }

            _state.update { it.copy(pendingDeletion = PendingDeletion.QuickAction(deletedAction, index)) }
            pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
                delay(4.seconds)
                executeDeletion(PendingDeletion.QuickAction(deletedAction, index))
            }
        }
    }

    private fun onChangeThemeMode(themeMode: ThemeMode) {
        appSettings.setThemeMode(themeMode)
        _state.update { it.copy(themeMode = themeMode) }
    }

    private fun onChangeMonitorOverlayMode(mode: com.katya.app.data.MonitorOverlayMode) {
        appSettings.setMonitorOverlayMode(mode)
        _state.update { it.copy(monitorOverlayMode = mode) }
    }

    private fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _state.update { it.copy(isMemoryEnabled = enabled) }
    }

    private fun onDeleteMemory(key: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Memory(key)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Memory(key))
        }
    }

    private fun onUpdateMemory(key: String, content: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.addMemory(key, content) // Update is handled, but add is also supported
            dataRepository.updateMemoryContent(key, content)
            _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList()) }
        }
    }

    private fun onAddMemory(key: String, content: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.addMemory(key, content)
            _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList()) }
        }
    }

    private fun onAddScheduledTask(
        description: String,
        prompt: String,
        scheduledAtEpochMs: Long,
        cron: String?,
        trigger: com.katya.app.data.TaskTrigger,
    ) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.addScheduledTask(description, prompt, scheduledAtEpochMs, cron, trigger)
            _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList()) }
        }
    }

    private fun onUpdateScheduledTask(task: com.katya.app.data.ScheduledTask) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.updateScheduledTask(task)
            _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList()) }
        }
    }

    private fun onToggleScheduling(enabled: Boolean) {
        dataRepository.setSchedulingEnabled(enabled)
        _state.update { it.copy(isSchedulingEnabled = enabled) }
    }

    private fun onCancelTask(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Task(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Task(id))
        }
    }

    private fun onToggleDaemon(enabled: Boolean) {
        dataRepository.setDaemonEnabled(enabled)
        if (enabled) {
            viewModelScope.launch { notificationPermissionController.requestPermission() }
            daemonController.start()
        } else {
            daemonController.stop()
        }
        _state.update { it.copy(isDaemonEnabled = enabled) }
    }

    private fun onToggleVless(enabled: Boolean) {
        dataRepository.setVlessEnabled(enabled)
        _state.update { it.copy(isVlessEnabled = enabled) }
        if (_state.value.isDaemonEnabled) {
            daemonController.start()
        }
    }

    private fun onChangeVlessUri(uri: String) {
        dataRepository.setVlessUri(uri)
        _state.update { it.copy(vlessUri = uri) }
        if (_state.value.isDaemonEnabled) {
            daemonController.start() // Restart daemon to apply new proxy config
        }
    }

    private fun onStartDeepSeekAuth(instanceId: String, email: String, password: String) {
        _state.update {
            it.copy(
                dsAuthInstanceId = instanceId,
                dsAuthEmail = email,
                dsAuthPassword = password,
                dsAuthRunning = true,
                dsAuthStatus = "Запускаю вход…",
            )
        }
    }

    private fun onDeepSeekAuthStatus(status: String) {
        _state.update { it.copy(dsAuthStatus = status) }
    }

    private fun onStopDeepSeekAuth() {
        _state.update {
            it.copy(
                dsAuthRunning = false,
                dsAuthInstanceId = "",
                dsAuthEmail = "",
                dsAuthPassword = "",
            )
        }
    }

    private fun onDeepSeekAuthSucceeded(instanceId: String) {
        onStopDeepSeekAuth()
        // Restart the sandbox proxy so it serves the session we just saved instead
        // of the one it loaded at boot.
        daemonController.switchFreeDeepSeekInstance(instanceId)
    }

    private fun onToggleHeartbeat(enabled: Boolean) {
        dataRepository.setHeartbeatEnabled(enabled)
        _state.update { it.copy(isHeartbeatEnabled = enabled) }
    }

    private fun onChangeHeartbeatInterval(minutes: Int) {
        dataRepository.setHeartbeatIntervalMinutes(minutes)
        _state.update { it.copy(heartbeatIntervalMinutes = minutes) }
    }

    private fun onChangeHeartbeatActiveHours(start: Int, end: Int) {
        dataRepository.setHeartbeatActiveHours(start, end)
        _state.update { it.copy(heartbeatActiveHoursStart = start, heartbeatActiveHoursEnd = end) }
    }

    private fun onSaveHeartbeatPrompt(text: String) {
        dataRepository.setHeartbeatPrompt(text)
        _state.update { it.copy(heartbeatPrompt = text) }
    }

    private fun onChangeHeartbeatService(instanceId: String?) {
        dataRepository.setHeartbeatInstanceId(instanceId)
        _state.update { it.copy(heartbeatSelectedInstanceId = instanceId) }
    }

    private fun onToggleWakeWord(enabled: Boolean) {
        dataRepository.setWakeWordEnabled(enabled)
        _state.update { it.copy(isWakeWordEnabled = enabled) }
        if (enabled) {
            runCatching {
                wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), _state.value.wakeWordTrigger)
            }.onFailure { e ->
                com.katya.app.tools.AppLogger.e("WakeWord", "Failed to start wake word: ${e.message}")
                _state.update { it.copy(isWakeWordEnabled = false) }
                dataRepository.setWakeWordEnabled(false)
            }
        } else {
            wakeWordPlatform.stopListening()
        }
    }

    private fun onToggleWakeWordVibration(enabled: Boolean) {
        dataRepository.setWakeWordVibration(enabled)
        _state.update { it.copy(isWakeWordVibrationEnabled = enabled) }
    }

    private fun onToggleWakeWordSound(enabled: Boolean) {
        dataRepository.setWakeWordSound(enabled)
        _state.update { it.copy(isWakeWordSoundEnabled = enabled) }
    }

    private fun onChangeWakeWordTrigger(trigger: String) {
        dataRepository.setWakeWordTrigger(trigger)
        _state.update { it.copy(wakeWordTrigger = trigger) }
        if (_state.value.isWakeWordEnabled) {
            runCatching {
                wakeWordPlatform.stopListening()
                wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), trigger)
            }.onFailure { e ->
                com.katya.app.tools.AppLogger.e("WakeWord", "Failed to restart wake word: ${e.message}")
            }
        }
    }

    private fun onChangeWakeWordModelLang(lang: String) {
        dataRepository.setWakeWordModelLang(lang)
        _state.update {
            it.copy(
                wakeWordModelLang = lang,
                isVoskReady = wakeWordPlatform.isModelReady(getModelUrl(lang)),
            )
        }
        if (_state.value.isWakeWordEnabled) {
            runCatching {
                wakeWordPlatform.stopListening()
                wakeWordPlatform.startListening(getModelUrl(lang), _state.value.wakeWordTrigger)
            }.onFailure { e ->
                com.katya.app.tools.AppLogger.e("WakeWord", "Failed to restart wake word: ${e.message}")
            }
        }
    }

    private fun getModelUrl(lang: String): String = when (lang) {
        "ru" -> "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        "en" -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        else -> lang
    }

    private fun onDownloadVosk() {
        wakeWordPlatform.startDownload(getModelUrl(_state.value.wakeWordModelLang))
    }

    private fun onRefreshHeartbeat() {
        if (_state.value.isRefreshingHeartbeat) return
        _state.update { it.copy(isRefreshingHeartbeat = true) }
        viewModelScope.launch(backgroundDispatcher) {
            taskScheduler.triggerHeartbeatNow()
            _state.update {
                it.copy(
                    isRefreshingHeartbeat = false,
                    heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
                )
            }
        }
    }

    private fun onToggleEmail(enabled: Boolean) {
        dataRepository.setEmailEnabled(enabled)
        _state.update { it.copy(isEmailEnabled = enabled) }
    }

    private fun onAddEmailAccount(account: EmailAccount, password: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.addEmailAccount(account, password)
            _state.update {
                it.copy(
                    emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
                    emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                )
            }
        }
    }

    private fun onRemoveEmailAccount(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.EmailAccount(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.EmailAccount(id))
        }
    }

    private fun onChangeEmailPollInterval(minutes: Int) {
        dataRepository.setEmailPollIntervalMinutes(minutes)
        _state.update { it.copy(emailPollIntervalMinutes = minutes) }
    }

    private fun onRefreshEmailAccount(id: String) {
        if (id in _state.value.refreshingEmailAccountIds) return
        _state.update { it.copy(refreshingEmailAccountIds = (it.refreshingEmailAccountIds + id).toPersistentSet()) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollEmailAccount(id)
            _state.update {
                it.copy(
                    refreshingEmailAccountIds = (it.refreshingEmailAccountIds - id).toPersistentSet(),
                    emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                    emailPendingCount = dataRepository.getPendingEmailCount(),
                )
            }
        }
    }

    private fun onToggleSms(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsPermission()) {
            // Ask for the OS permission first; only flip the toggle on if it's granted.
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsPermission()
                _state.update { it.copy(smsPermissionGranted = granted, isSmsEnabled = granted) }
                if (granted) {
                    dataRepository.setSmsEnabled(true)
                    // First poll seeds lastSeenId to the current inbox max, so the AI
                    // isn't drowned in historical messages on opt-in.
                    dataRepository.pollSms()
                    _state.update {
                        it.copy(
                            smsSyncState = dataRepository.getSmsSyncState(),
                            smsPendingCount = dataRepository.getPendingSmsCount(),
                        )
                    }
                }
            }
        } else {
            dataRepository.setSmsEnabled(enabled)
            _state.update { it.copy(isSmsEnabled = enabled) }
        }
    }

    private fun onChangeSmsPollInterval(minutes: Int) {
        dataRepository.setSmsPollIntervalMinutes(minutes)
        _state.update { it.copy(smsPollIntervalMinutes = minutes) }
    }

    private fun onRefreshSms() {
        if (_state.value.isRefreshingSms) return
        _state.update { it.copy(isRefreshingSms = true) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollSms()
            _state.update {
                it.copy(
                    isRefreshingSms = false,
                    smsSyncState = dataRepository.getSmsSyncState(),
                    smsPendingCount = dataRepository.getPendingSmsCount(),
                    smsPermissionGranted = dataRepository.hasSmsPermission(),
                )
            }
        }
    }

    private fun onToggleSmsSend(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsSendPermission()) {
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsSendPermission()
                _state.update { it.copy(smsSendPermissionGranted = granted, isSmsSendEnabled = granted) }
                if (granted) dataRepository.setSmsSendEnabled(true)
            }
        } else {
            dataRepository.setSmsSendEnabled(enabled)
            _state.update { it.copy(isSmsSendEnabled = enabled) }
        }
    }

    private fun onToggleNotifications(enabled: Boolean) {
        // Listener access is granted via system Settings, not a runtime permission
        // dialog. Set the toggle, then if access is missing, deep-link the user out
        // so they can enable Katya there. The toggle reflects the user's *intent*; the
        // listener still drops everything until access is granted.
        dataRepository.setNotificationsEnabled(enabled)
        _state.update {
            it.copy(
                isNotificationsEnabled = enabled,
                notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
            )
        }
        if (enabled && !dataRepository.isNotificationListenerAccessGranted()) {
            dataRepository.openNotificationListenerSettings()
        }
    }

    private fun onOpenNotificationListenerSettings() {
        dataRepository.openNotificationListenerSettings()
    }

    private fun onOpenAppPermissionSettings() {
        localNetworkPermissionController.openAppSettings()
    }

    private fun onChangeHfRepoUrl(url: String) {
        _state.update { it.copy(hfRepoUrl = url, hfError = null) }
    }

    private fun onChangePiperVoiceUrl(url: String) {
        piperVoiceUrlText = url
        _state.update { it.copy(piperVoiceUrl = url, piperDownloadError = null) }
    }

    private fun onDownloadPiperVoice(url: String) {
        if (url.trim().isBlank()) return
        dataRepository.startPiperVoiceDownload(url.trim())
    }

    private fun onSelectPiperVoice(baseName: String) {
        dataRepository.setPiperSelectedVoice(baseName)
        _state.update { it.copy(piperSelectedVoice = baseName) }
    }

    private fun onImportPiperVoice(fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.importPiperVoice(fileName, fileBytes)
                com.katya.app.showToast("Голос импортирован")
                refreshPiperVoices()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                com.katya.app.showToast("Не удалось импортировать голос: ${(t.message ?: t::class.simpleName)?.take(120)}")
            }
        }
    }

    private fun onDeletePiperVoice(baseName: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deletePiperVoice(baseName)
            refreshPiperVoices()
        }
    }

    private fun onExportPiperVoice(baseName: String) {
        viewModelScope.launch(backgroundDispatcher) {
            val ok = dataRepository.exportPiperVoice(baseName)
            com.katya.app.showToast(if (ok) "Голос сохранён на устройстве" else "Не удалось сохранить голос")
        }
    }

    private fun refreshPiperVoices() {
        _state.update {
            it.copy(
                piperInstalledVoices = dataRepository.getPiperInstalledVoices().toImmutableList(),
                piperSelectedVoice = dataRepository.getPiperSelectedVoice(),
            )
        }
    }

    private fun onFetchHfModels() {
        val url = _state.value.hfRepoUrl.trim()
        if (url.isBlank()) return
        val regex = Regex("huggingface\\.co/([^/]+)/([^/]+)")
        val match = regex.find(url)
        if (match == null) {
            _state.update { it.copy(hfError = "Invalid HuggingFace URL") }
            return
        }
        val user = match.groupValues[1]
        val repo = match.groupValues[2]

        _state.update { it.copy(isFetchingHfModels = true, hfError = null, hfModels = kotlinx.collections.immutable.persistentListOf()) }
        viewModelScope.launch(backgroundDispatcher) {
            try {
                val client = httpClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }
                val apiUrl = "https://huggingface.co/api/models/$user/$repo/tree/main"
                com.katya.app.tools.AppLogger.d("HfFetch", "Fetching external model listing: $apiUrl")
                val response = client.get(apiUrl)
                if (response.status.isSuccess()) {
                    val entries = response.body<List<HuggingFaceTreeEntry>>()
                    val models = entries.filter { it.type == "file" && (it.path.endsWith(".gguf") || it.path.endsWith(".litertlm")) }
                        .map { entry ->
                            val baseName = entry.path.substringAfterLast('/')
                            // On-device storage is one dir per model under litert_models/,
                            // so the id must be a plain directory name (no slashes).
                            val id = baseName
                                .removeSuffix(".litertlm")
                                .removeSuffix(".gguf")
                                .removeSuffix(".tflite")
                            LocalModel(
                                id = id,
                                displayName = baseName,
                                fileName = baseName,
                                sizeBytes = entry.size,
                                downloadUrl = "https://huggingface.co/$user/$repo/resolve/main/${entry.path}",
                                gpuMemoryMb = (entry.size / (1024 * 1024)).toInt() + 500,
                                defaultContextTokens = 4096,
                                maxContextTokens = 8192,
                                kvPerTokenBytes = 65000,
                            )
                        }
                    com.katya.app.tools.AppLogger.d("HfFetch", "Found ${models.size} model files in $user/$repo")
                    if (models.isEmpty()) {
                        _state.update { it.copy(isFetchingHfModels = false, hfError = "No .gguf or .litertlm files found") }
                    } else {
                        _state.update { it.copy(isFetchingHfModels = false, hfModels = models.toImmutableList()) }
                    }
                } else {
                    com.katya.app.tools.AppLogger.w("HfFetch", "HTTP ${response.status.value} fetching $apiUrl")
                    _state.update { it.copy(isFetchingHfModels = false, hfError = "Failed to fetch repository") }
                }
            } catch (e: Exception) {
                com.katya.app.tools.AppLogger.e("HfFetch", "Failed to fetch external models: ${e.message}\n${e.stackTraceToString()}")
                _state.update { it.copy(isFetchingHfModels = false, hfError = e.message ?: "Network error") }
            }
        }
    }

    /**
     * Called when the app resumes while a connection sits in the local-network-denied state.
     * Re-validates only if the permission is now granted — never re-prompts, so a user who
     * denied and stayed on the screen isn't nagged with another dialog.
     */
    private fun onRecheckLocalNetworkPermission(instanceId: String) {
        if (!localNetworkPermissionController.hasPermission()) return
        val instance = dataRepository.getConfiguredServiceInstances().firstOrNull { it.instanceId == instanceId } ?: return
        validateConnectionWithStatus(instanceId, Service.fromId(instance.serviceId))
    }

    private fun onClearPendingNotifications() {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.clearPendingNotifications()
            _state.update { it.copy(notificationPendingCount = 0) }
        }
    }

    private fun onToggleFreeFallback(enabled: Boolean) {
        dataRepository.setFreeFallbackEnabled(enabled)
        _state.update { it.copy(isFreeFallbackEnabled = enabled) }
    }

    private fun onOpenDeviceAdminSettings() {
        DeviceAdminManager.openDeviceAdminSettings()
    }

    private fun onOpenTrustAgentSettings() {
        DeviceAdminManager.openTrustAgentSettings()
    }

    private fun onDownloadLocalModel(model: LocalModel) {
        dataRepository.startLocalModelDownload(model)
    }

    private fun onCancelLocalModelDownload(modelId: String) {
        dataRepository.cancelLocalModelDownload(modelId)
    }

    private fun onImportLocalModel(model: LocalModel, fileBytes: ByteArray) {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.importLocalModel(model, fileBytes)
                _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                refreshServiceList()
                _state.value.configuredServices
                    .filter { it.service.isOnDevice }
                    .forEach { checkConnection(it.instanceId, it.service) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                com.katya.app.tools.AppLogger.e("ModelImport", "Failed to import local model ${model.id}: $t\n${t.stackTraceToString()}")
                com.katya.app.showToast("Не удалось импортировать модель: ${t.message ?: t::class.simpleName}")
            }
        }
    }

    private fun onChangeModelContextTokens(modelId: String, contextTokens: Int) {
        if (_state.value.modelContextTokens[modelId] == contextTokens) return
        dataRepository.setModelContextTokens(modelId, contextTokens)
        _state.update {
            it.copy(modelContextTokens = it.modelContextTokens.toMutableMap().apply { put(modelId, contextTokens) }.toImmutableMap())
        }
        // Release engine so the next message re-initializes with the new context size
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.releaseLocalEngine()
        }
    }

    private fun buildModelContextTokensMap() = dataRepository.getLocalAvailableModels().associate { model ->
        val stored = dataRepository.getModelContextTokens(model.id)
        model.id to if (stored > 0) stored else model.defaultContextTokens
    }.toImmutableMap()

    private fun onDeleteLocalModel(modelId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.deleteLocalModel(modelId)
                _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                refreshServiceList()
                _state.value.configuredServices
                    .filter { it.service.isOnDevice }
                    .forEach { checkConnection(it.instanceId, it.service) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                com.katya.app.tools.AppLogger.e("ModelDelete", "Failed to delete local model $modelId: $t\n${t.stackTraceToString()}")
                com.katya.app.showToast("Не удалось удалить модель: ${t.message ?: t::class.simpleName}")
            }
        }
    }

    private fun onSaveLocalModelToDevice(modelId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            val ok = dataRepository.saveLocalModelToDevice(modelId)
            com.katya.app.showToast(
                if (ok) {
                    "Модель сохранена в выбранное место"
                } else {
                    "Не удалось сохранить модель"
                },
            )
        }
    }

    private fun onChangeUiScale(scale: Float) {
        dataRepository.setUiScale(scale)
        _state.update { it.copy(uiScale = scale) }
    }

    private suspend fun onExportSettings(sections: Set<ImportSection>): ByteArray {
        val jsonConfig = dataRepository.exportSettingsToJson(sections)
        val includeDatabase = sections.contains(ImportSection.CONVERSATIONS)
        val includeModels = sections.contains(ImportSection.MODELS)
        return com.katya.app.generateBackupZip(jsonConfig, includeDatabase, includeModels)
    }

    private fun onPrepareExport(): Map<ImportSection, String?> {
        val preview = dataRepository.getExportPreview().toMutableMap()
        preview[ImportSection.CONVERSATIONS] = null
        preview[ImportSection.MODELS] = null
        return preview
    }

    private fun onPrepareImport(json: String, sections: Set<ImportSection>): ImportPreviews = dataRepository.prepareSettingsImport(json, sections)

    /**
     * Applies a backup the user has confirmed in the review dialog.
     *
     * The archive's files go in first, then the settings, then the UI is rebuilt
     * from the new state — including re-arming the scheduler, because the
     * restored task list is not the one the alarms were registered for.
     */
    private suspend fun onImportSettings(
        json: String,
        sections: Set<ImportSection>,
        mode: ImportMode,
        payload: BackupPayload?,
    ): ImportResult = try {
        val currentTab = _state.value.currentTab
        val errors = dataRepository.applyPreparedImport(json, sections, mode, payload)
        _state.value = buildFullState().copy(currentTab = currentTab)
        taskScheduler.rearmAll()
        checkAllConnections()
        connectEnabledMcpServers()
        if (errors == 0) ImportResult.Success else ImportResult.PartialSuccess(errors)
    } catch (_: Exception) {
        ImportResult.Failure
    }

    private fun onToggleTool(toolId: String, enabled: Boolean) {
        dataRepository.setToolEnabled(toolId, enabled)
        _state.update { state ->
            state.copy(
                tools = state.tools.map { tool ->
                    if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                }.toImmutableList(),
                mcpServers = state.mcpServers.map { server ->
                    server.copy(
                        tools = server.tools.map { tool ->
                            if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                        }.toImmutableList(),
                    )
                }.toImmutableList(),
            )
        }
    }

    // MCP server management
    private fun buildMcpServerEntries(): List<McpServerUiState> = dataRepository.getMcpServers().map { config ->
        McpServerUiState(
            id = config.id,
            name = config.name,
            url = config.url,
            isEnabled = config.isEnabled,
            connectionStatus = if (dataRepository.isMcpServerConnected(config.id)) {
                McpConnectionStatus.Connected
            } else {
                McpConnectionStatus.Unknown
            },
            tools = dataRepository.getMcpToolsForServer(config.id).toImmutableList(),
        )
    }

    private fun refreshMcpServers() {
        _state.update { current ->
            val existingStatuses = current.mcpServers.associate { it.id to it.connectionStatus }
            current.copy(
                mcpServers = buildMcpServerEntries().map { entry ->
                    val preservedStatus = existingStatuses[entry.id]
                    // Only preserve transient statuses (Connecting/Error) — derive Connected/Unknown from actual state
                    if (preservedStatus == McpConnectionStatus.Connecting || preservedStatus == McpConnectionStatus.Error) {
                        entry.copy(connectionStatus = preservedStatus)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onAddMcpServer(name: String, url: String, headers: Map<String, String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val config = dataRepository.addMcpServer(name, url, headers)
            refreshMcpServers()
            connectMcpServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddMcpServerDialog = false) }
    }

    private fun onRemoveMcpServer(serverId: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.McpServer(serverId)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.McpServer(serverId))
        }
    }

    private fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        dataRepository.setMcpServerEnabled(serverId, enabled)
        refreshMcpServers()
        if (enabled) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(serverId)
            }
        }
    }

    private fun onRefreshMcpServer(serverId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            connectMcpServerWithStatus(serverId)
        }
    }

    private fun onShowAddMcpServerDialog(show: Boolean) {
        _state.update { it.copy(showAddMcpServerDialog = show) }
    }

    private fun onAddPopularMcpServer(server: PopularMcpServer) {
        onAddMcpServer(server.name, server.url, emptyMap())
    }

    // Skills ---------------------------------------------------------------

    private fun refreshSkills() {
        _state.update { it.copy(skills = dataRepository.getInstalledSkills().toImmutableList()) }
    }

    private fun onUninstallSkill(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Skill(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Skill(id))
        }
    }

    private fun onShowAddSkillDialog(show: Boolean) {
        _state.update {
            it.copy(
                showAddSkillDialog = show,
                skillInstallError = null,
                // Lazily fetch the marketplaces the first time the dialog opens.
                browseSkillsFailed = if (show) it.browseSkillsFailed else false,
            )
        }
        if (show && _state.value.browsableSkills.isEmpty() && !_state.value.isBrowsingSkills) {
            browseSkillMarketplaces()
        }
    }

    private fun browseSkillMarketplaces() {
        _state.update { it.copy(isBrowsingSkills = true, browseSkillsFailed = false) }
        viewModelScope.launch(backgroundDispatcher) {
            val result = dataRepository.browseSkillMarketplaces()
            _state.update { state ->
                state.copy(
                    isBrowsingSkills = false,
                    browsableSkills = result.getOrNull().orEmpty().toImmutableList(),
                    browseSkillsFailed = result.isFailure,
                )
            }
        }
    }

    private fun onInstallGitHubSkill(input: String) {
        val source = parseGitHubSkillUrl(input)
        if (source == null) {
            viewModelScope.launch(backgroundDispatcher) {
                _state.update { it.copy(skillInstallError = getString(Res.string.error_unrecognized_github_repo)) }
            }
            return
        }
        runSkillInstall { dataRepository.installGitHubSkill(source.owner, source.repo, source.ref, source.path) }
    }

    private fun onInstallBrowsedSkill(entry: com.katya.app.skills.RegistrySkillEntry) {
        runSkillInstall { dataRepository.installBrowsedSkill(entry) }
    }

    /**
     * Feedback #5: install every skill the registries offer, one button instead of
     * tapping each row. Already-installed skills are skipped, failures are counted
     * and reported instead of aborting the run — one broken registry must not
     * strand the rest.
     */
    private fun onInstallAllBrowsedSkills() {
        if (_state.value.skillsBulk.running) return
        _state.update { it.copy(skillsBulk = BulkProgress(running = true, current = "")) }
        viewModelScope.launch(backgroundDispatcher) {
            // The catalogue is only fetched when the add-sheet opens, so fetch it
            // here if the user never opened it.
            var entries: List<com.katya.app.skills.RegistrySkillEntry> = _state.value.browsableSkills
            if (entries.isEmpty()) {
                _state.update { it.copy(isBrowsingSkills = true) }
                entries = dataRepository.browseSkillMarketplaces().getOrNull().orEmpty()
                _state.update {
                    it.copy(
                        isBrowsingSkills = false,
                        browsableSkills = entries.toImmutableList(),
                        browseSkillsFailed = entries.isEmpty(),
                    )
                }
            }
            val alreadyInstalled = dataRepository.getInstalledSkills().mapTo(mutableSetOf()) { it.id }
            val pending = entries.filter { it.id !in alreadyInstalled }
            if (pending.isEmpty()) {
                _state.update {
                    it.copy(
                        skillsBulk = BulkProgress(
                            result = if (entries.isEmpty()) BulkResult.NoCatalogue else BulkResult.AllInstalled,
                        ),
                    )
                }
                return@launch
            }

            var ok = 0
            var failed = 0
            pending.forEachIndexed { index, entry ->
                _state.update { it.copy(skillsBulk = it.skillsBulk.copy(done = index, current = entry.id)) }
                val result = dataRepository.installBrowsedSkill(entry)
                if (result.isSuccess) ok++ else failed++
            }
            refreshSkills()
            _state.update {
                it.copy(
                    skillsBulk = BulkProgress(
                        done = pending.size,
                        total = pending.size,
                        ok = ok,
                        failed = failed,
                        result = BulkResult.Done,
                    ),
                )
            }
        }
    }

    /**
     * Feedback #6: same one-tap treatment for MCP. Enables every configured server,
     * connects them one by one and turns on all tools they expose. Servers that
     * refuse to connect are counted, not fatal — a dead server shouldn't block the
     * rest.
     */
    private fun onConnectAllMcpServers() {
        if (_state.value.mcpBulk.running) return
        _state.update { it.copy(mcpBulk = BulkProgress(running = true)) }
        viewModelScope.launch(backgroundDispatcher) {
            val servers = dataRepository.getMcpServers()
            if (servers.isEmpty()) {
                _state.update { it.copy(mcpBulk = BulkProgress(result = BulkResult.NoServers)) }
                return@launch
            }
            servers.forEach { server ->
                if (!server.isEnabled) dataRepository.setMcpServerEnabled(server.id, true)
            }

            var ok = 0
            var failed = 0
            servers.forEachIndexed { index, server ->
                _state.update { it.copy(mcpBulk = it.mcpBulk.copy(done = index, current = server.name)) }
                updateMcpConnectionStatus(server.id, McpConnectionStatus.Connecting)
                if (dataRepository.connectMcpServer(server.id).isSuccess) {
                    ok++
                    updateMcpConnectionStatus(server.id, McpConnectionStatus.Connected)
                } else {
                    failed++
                    updateMcpConnectionStatus(server.id, McpConnectionStatus.Error)
                }
            }

            // Everything the connected servers expose becomes available in one go.
            dataRepository.getMcpServers()
                .filter { it.isEnabled }
                .flatMap { dataRepository.getMcpToolsForServer(it.id) }
                .filterNot { it.isEnabled }
                .forEach { tool ->
                    dataRepository.setToolEnabled(tool.id, true)
                }

            _state.update { it.copy(mcpServers = buildMcpServerEntries().toImmutableList()) }
            _state.update {
                it.copy(
                    mcpBulk = BulkProgress(
                        done = servers.size,
                        total = servers.size,
                        ok = ok,
                        failed = failed,
                        result = BulkResult.Done,
                    ),
                )
            }
        }
    }

    private inline fun runSkillInstall(crossinline install: suspend () -> Result<com.katya.app.skills.SkillManifest>) {
        _state.update { it.copy(isInstallingSkill = true, skillInstallError = null) }
        viewModelScope.launch(backgroundDispatcher) {
            val result = install()
            result.fold(
                onSuccess = {
                    refreshSkills()
                    _state.update { it.copy(isInstallingSkill = false, showAddSkillDialog = false) }
                },
                onFailure = { error ->
                    val message = error.message ?: getString(Res.string.error_unknown)
                    _state.update {
                        it.copy(
                            isInstallingSkill = false,
                            skillInstallError = message,
                        )
                    }
                },
            )
        }
    }

    private suspend fun connectMcpServerWithStatus(serverId: String) {
        updateMcpConnectionStatus(serverId, McpConnectionStatus.Connecting)
        val result = dataRepository.connectMcpServer(serverId)
        if (result.isSuccess) {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Connected)
            refreshMcpServers()
        } else {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Error)
        }
    }

    private fun updateMcpConnectionStatus(serverId: String, status: McpConnectionStatus) {
        _state.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map { entry ->
                    if (entry.id == serverId) entry.copy(connectionStatus = status) else entry
                }.toImmutableList(),
            )
        }
    }

    private fun connectEnabledMcpServers() {
        val enabledServers = _state.value.mcpServers.filter { it.isEnabled && it.connectionStatus != McpConnectionStatus.Connected }
        for (server in enabledServers) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(server.id)
            }
        }
    }

    private fun commitPendingDeletion() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: return
        _state.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            executeDeletion(deletion)
        }
    }

    private suspend fun executeDeletion(deletion: PendingDeletion) {
        when (deletion) {
            is PendingDeletion.Memory -> {
                dataRepository.deleteMemory(deletion.key)
                _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList()) }
            }

            is PendingDeletion.QuickAction -> {
                val actions = dataRepository.getQuickActions().toMutableList()
                actions.add(deletion.index.coerceIn(0, actions.size), deletion.action)
                dataRepository.setQuickActions(actions)
                _state.update { it.copy(quickActions = actions.toImmutableList()) }
            }

            is PendingDeletion.Task -> {
                dataRepository.cancelScheduledTask(deletion.id)
                _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList()) }
            }

            is PendingDeletion.EmailAccount -> {
                dataRepository.removeEmailAccount(deletion.id)
                _state.update {
                    it.copy(
                        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
                        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                        emailPendingCount = dataRepository.getPendingEmailCount(),
                    )
                }
            }

            is PendingDeletion.Service -> {
                val service = _state.value.configuredServices.find { it.instanceId == deletion.instanceId }?.service
                dataRepository.removeConfiguredService(deletion.instanceId)
                // If removing the last on-device service, delete all downloaded models
                if (service?.isOnDevice == true) {
                    val hasOtherOnDevice = dataRepository.getConfiguredServiceInstances().any {
                        Service.fromId(it.serviceId).isOnDevice
                    }
                    if (!hasOtherOnDevice) {
                        dataRepository.getLocalDownloadedModels().forEach {
                            try {
                                dataRepository.deleteLocalModel(it.id)
                            } catch (t: Throwable) {
                                if (t is kotlinx.coroutines.CancellationException) throw t
                                com.katya.app.tools.AppLogger.e("ModelDelete", "Failed to delete model ${it.id} while removing service: $t\n${t.stackTraceToString()}")
                            }
                        }
                        _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    }
                }
                refreshServiceList()
            }

            is PendingDeletion.McpServer -> {
                dataRepository.removeMcpServer(deletion.serverId)
                refreshMcpServers()
            }

            is PendingDeletion.Skill -> {
                dataRepository.uninstallSkill(deletion.id)
                refreshSkills()
            }
        }
        // Guard against a stale async deletion clobbering a newer pending one from a rapid second Remove click.
        _state.update { state ->
            if (state.pendingDeletion == deletion) state.copy(pendingDeletion = null) else state
        }
    }

    private fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        _state.update { it.copy(pendingDeletion = null) }
    }

    override fun onCleared() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: run {
            super.onCleared()
            return
        }
        _state.update { it.copy(pendingDeletion = null) }
        CoroutineScope(backgroundDispatcher).launch {
            executeDeletion(deletion)
        }
        super.onCleared()
    }

    private fun checkAllConnections() {
        for (entry in _state.value.configuredServices) {
            checkConnection(entry.instanceId, entry.service)
        }
    }

    private fun checkConnectionDebounced(instanceId: String, service: Service) {
        connectionCheckJobs[instanceId]?.cancel()
        connectionCheckJobs[instanceId] = viewModelScope.launch {
            delay(800.milliseconds)
            checkConnection(instanceId, service)
        }
    }

    private fun checkConnection(instanceId: String, service: Service) {
        if (service == Service.Free) {
            updateConnectionStatus(instanceId, ConnectionStatus.Connected)
            return
        }
        if (service.isOnDevice) {
            validateConnectionWithStatus(instanceId, service)
            return
        }
        if (service.requiresApiKey && dataRepository.getInstanceApiKey(instanceId).isBlank()) {
            updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
            return
        }
        validateConnectionWithStatus(instanceId, service)
    }

    private fun updateConnectionStatus(instanceId: String, status: ConnectionStatus) {
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { entry ->
                    if (entry.instanceId == instanceId) {
                        entry.copy(connectionStatus = status)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun validateConnectionWithStatus(instanceId: String, service: Service) {
        updateConnectionStatus(instanceId, ConnectionStatus.Checking)
        viewModelScope.launch(backgroundDispatcher) {
            // Android 17+ blocks LAN traffic without the local network permission, so ask
            // before probing — otherwise the check fails with a misleading connection error.
            val baseUrl = dataRepository.getInstanceBaseUrl(instanceId, service)
            if (isLocalNetworkUrl(baseUrl) && !localNetworkPermissionController.requestPermission()) {
                updateConnectionStatus(instanceId, ConnectionStatus.ErrorLocalNetworkDenied)
                return@launch
            }
            try {
                dataRepository.validateConnection(service, instanceId)
                if (service.isOnDevice && dataRepository.getLocalDownloadedModels().isEmpty()) {
                    updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
                } else {
                    updateConnectionStatus(instanceId, ConnectionStatus.Connected)
                }
                refreshInstanceModels(instanceId)
            } catch (e: Exception) {
                val status = when (e) {
                    is OpenAICompatibleInvalidApiKeyException, is GeminiInvalidApiKeyException, is AnthropicInvalidApiKeyException ->
                        ConnectionStatus.ErrorInvalidKey

                    is OpenAICompatibleQuotaExhaustedException, is AnthropicInsufficientCreditsException ->
                        ConnectionStatus.ErrorQuotaExhausted

                    is OpenAICompatibleRateLimitExceededException, is GeminiRateLimitExceededException, is AnthropicRateLimitExceededException ->
                        ConnectionStatus.ErrorRateLimited

                    is AnthropicOverloadedException ->
                        ConnectionStatus.Error

                    is OpenAICompatibleConnectionException ->
                        ConnectionStatus.ErrorConnectionFailed

                    else -> ConnectionStatus.Error
                }
                updateConnectionStatus(instanceId, status)
            }
        }
    }
}
