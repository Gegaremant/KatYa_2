package com.katya.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katya.app.DaemonController
import com.katya.app.Platform
import com.katya.app.currentPlatform
import com.katya.app.data.DataRepository
import com.katya.app.data.ImportSection
import com.katya.app.data.Service
import com.katya.app.data.TaskScheduler
import com.katya.app.data.ThemeMode
import com.katya.app.data.supportsAgenticFlows
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentSet
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
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private var pendingDeleteJob: Job? = null

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        soulText = dataRepository.getSoulText(),
        isDynamicUiEnabled = dataRepository.isDynamicUiEnabled(),
        isAgentVisibilityEnabled = dataRepository.isAgentVisibilityEnabled(),
        isVoiceResponseEnabled = dataRepository.isVoiceResponseEnabled(),
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
        localDownloadingModelId = dataRepository.getLocalDownloadingModelId()?.value,
        localDownloadProgress = dataRepository.getLocalDownloadProgress()?.value,
        modelContextTokens = buildModelContextTokensMap(),
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
        onToggleDynamicUi = ::onToggleDynamicUi,
        onToggleAgentVisibility = ::onToggleAgentVisibility,
        onToggleVoiceResponse = ::onToggleVoiceResponse,
        onToggleWakeWord = ::onToggleWakeWord,
        onChangeWakeWordTrigger = ::onChangeWakeWordTrigger,
        setWakeWordModelLang = ::onChangeWakeWordModelLang,
        setIsWakeWordVibrationEnabled = ::onToggleWakeWordVibration,
        setIsWakeWordSoundEnabled = ::onToggleWakeWordSound,
        onAddQuickAction = ::onAddQuickAction,
        onUpdateQuickAction = ::onUpdateQuickAction,
        onDeleteQuickAction = ::onDeleteQuickAction,
        onChangeThemeMode = ::onChangeThemeMode,
        onToggleMemory = ::onToggleMemory,
        onDeleteMemory = ::onDeleteMemory,
        onUpdateMemory = ::onUpdateMemory,
        onToggleScheduling = ::onToggleScheduling,
        onCancelTask = ::onCancelTask,
        onToggleDaemon = ::onToggleDaemon,
        onToggleVless = ::onToggleVless,
        onChangeVlessUri = ::onChangeVlessUri,
        onToggleHeartbeat = ::onToggleHeartbeat,
        onDownloadVosk = ::onDownloadVosk,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = ::onChangeHeartbeatService,
        onRefreshHeartbeat = ::onRefreshHeartbeat,
        onToggleEmail = ::onToggleEmail,
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
        onUninstallSkill = ::onUninstallSkill,
        onShowAddSkillDialog = ::onShowAddSkillDialog,
        onInstallGitHubSkill = ::onInstallGitHubSkill,
        onInstallBrowsedSkill = ::onInstallBrowsedSkill,
        onDownloadLocalModel = ::onDownloadLocalModel,
        onCancelLocalModelDownload = ::onCancelLocalModelDownload,
        onDeleteLocalModel = ::onDeleteLocalModel,
        onChangeModelContextTokens = ::onChangeModelContextTokens,
        onExportSettings = ::onExportSettings,
        onPrepareExport = ::onPrepareExport,
        onImportSettings = ::onImportSettings,
        onChangeMonitorOverlayMode = ::onChangeMonitorOverlayMode,
        onUndoDelete = ::onUndoDelete,
        onChangeHfRepoUrl = ::onChangeHfRepoUrl,
        onFetchHfModels = ::onFetchHfModels,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    init {
        if (_state.value.isWakeWordEnabled) {
            wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), _state.value.wakeWordTrigger)
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
        val downloadingFlow = dataRepository.getLocalDownloadingModelId() ?: flowOf(null)
        val progressFlow = dataRepository.getLocalDownloadProgress() ?: flowOf(null)
        val errorFlow = dataRepository.getLocalDownloadError() ?: flowOf(null)
        viewModelScope.launch {
            combine(downloadingFlow, progressFlow, errorFlow) { modelId, progress, error ->
                Triple(modelId, progress, error)
            }.collect { (modelId, progress, error) ->
                val wasDownloading = _state.value.localDownloadingModelId != null
                _state.update {
                    it.copy(
                        localDownloadingModelId = modelId,
                        localDownloadProgress = progress,
                        localDownloadError = error,
                    )
                }
                if (modelId == null && wasDownloading) {
                    // Download finished or cancelled — refresh
                    _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    refreshServiceList()
                    _state.value.configuredServices
                        .filter { it.service.isOnDevice }
                        .forEach { checkConnection(it.instanceId, it.service) }
                }
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
            .filter { it != Service.Free }
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
            dataRepository.updateMemoryContent(key, content)
            _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList()) }
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
            wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), _state.value.wakeWordTrigger)
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
            wakeWordPlatform.stopListening()
            wakeWordPlatform.startListening(getModelUrl(_state.value.wakeWordModelLang), trigger)
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
            wakeWordPlatform.stopListening()
            wakeWordPlatform.startListening(getModelUrl(lang), _state.value.wakeWordTrigger)
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
                val response = client.get(apiUrl)
                if (response.status.isSuccess()) {
                    val entries = response.body<List<HuggingFaceTreeEntry>>()
                    val models = entries.filter { it.type == "file" && (it.path.endsWith(".gguf") || it.path.endsWith(".litertlm")) }
                        .map {
                            LocalModel(
                                id = it.path,
                                displayName = it.path,
                                fileName = it.path,
                                sizeBytes = it.size,
                                downloadUrl = "https://huggingface.co/$user/$repo/resolve/main/${it.path}",
                                gpuMemoryMb = (it.size / (1024 * 1024)).toInt() + 500,
                                defaultContextTokens = 4096,
                                maxContextTokens = 8192,
                                kvPerTokenBytes = 65000,
                            )
                        }
                    if (models.isEmpty()) {
                        _state.update { it.copy(isFetchingHfModels = false, hfError = "No .gguf or .litertlm files found") }
                    } else {
                        _state.update { it.copy(isFetchingHfModels = false, hfModels = models.toImmutableList()) }
                    }
                } else {
                    _state.update { it.copy(isFetchingHfModels = false, hfError = "Failed to fetch repository") }
                }
            } catch (e: Exception) {
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

    private fun onDownloadLocalModel(model: LocalModel) {
        dataRepository.startLocalModelDownload(model)
    }

    private fun onCancelLocalModelDownload() {
        dataRepository.cancelLocalModelDownload()
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
            dataRepository.deleteLocalModel(modelId)
            _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
            refreshServiceList()
            _state.value.configuredServices
                .filter { it.service.isOnDevice }
                .forEach { checkConnection(it.instanceId, it.service) }
        }
    }

    private fun onChangeUiScale(scale: Float) {
        dataRepository.setUiScale(scale)
        _state.update { it.copy(uiScale = scale) }
    }

    private fun onExportSettings(sections: Set<ImportSection>): String = dataRepository.exportSettingsToJson(sections)

    private fun onPrepareExport(): Map<ImportSection, String?> = dataRepository.getExportPreview()

    private fun onImportSettings(bytes: ByteArray, sections: Set<ImportSection>, replace: Boolean): ImportResult = try {
        val currentTab = _state.value.currentTab
        val errors = dataRepository.importSettingsFromJson(bytes.decodeToString(), sections, replace)
        // Import writes conversations to settings, but the chat list reads them from
        // ConversationStorage's in-memory flow — refresh it so imported chats appear
        // without an app restart.
        dataRepository.loadConversations()
        _state.value = buildFullState().copy(currentTab = currentTab)
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
                            dataRepository.deleteLocalModel(it.id)
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
