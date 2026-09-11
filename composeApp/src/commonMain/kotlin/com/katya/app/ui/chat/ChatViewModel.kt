package com.katya.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katya.app.data.Conversation
import com.katya.app.data.DataRepository
import com.katya.app.data.FreeMode
import com.katya.app.data.KatyaFile
import com.katya.app.data.ScheduledTask
import com.katya.app.data.Service
import com.katya.app.data.ServiceEntry
import com.katya.app.data.TaskScheduler
import com.katya.app.data.modelSupportsImages
import com.katya.app.data.UiSubmission
import com.katya.app.device.DeviceInfoProvider
import com.katya.app.device.NetworkStatusProvider
import com.katya.app.device.createDeviceInfoProvider
import com.katya.app.device.createNetworkStatusProvider
import com.katya.app.getBackgroundDispatcher
import com.katya.app.network.UiError
import com.katya.app.network.toUiError
import com.katya.app.tools.LocalNetworkPermissionController
import com.katya.app.tools.isLocalNetworkUrl
import com.katya.app.ui.markdown.KatyaUiBlock
import com.katya.app.ui.markdown.KatyaUiError
import com.katya.app.ui.markdown.parseMarkdown
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.conversation_untitled
import katya.composeapp.generated.resources.error_local_network_permission
import katya.composeapp.generated.resources.error_unsupported_file_type
import katya.composeapp.generated.resources.litert_no_model_warning
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class ChatViewModel(
    private val dataRepository: DataRepository,
    private val taskScheduler: TaskScheduler,
    private val monitorService: com.katya.app.monitor.MonitorService,
    private val wakeWordPlatform: com.katya.app.stt.WakeWordPlatform,
    private val appSettings: com.katya.app.data.AppSettings,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
    private val localNetworkPermissionController: LocalNetworkPermissionController = LocalNetworkPermissionController(),
) : ViewModel() {

    private val actions = ChatActions(
        ask = ::ask,
        retry = ::retry,
        toggleSpeechOutput = ::toggleSpeechOutput,
        clearHistory = ::clearHistory,
        setIsSpeaking = ::setIsSpeaking,
        addFile = ::addFile,
        removeFile = ::removeFile,
        startNewChat = ::startNewChat,
        regenerate = ::regenerate,
        cancel = ::cancel,
        selectService = ::selectService,
        loadConversation = ::loadConversation,
        deleteConversation = ::deleteConversation,
        clearUnreadHeartbeat = ::clearUnreadHeartbeat,
        clearSnackbar = ::clearSnackbar,
        undoDeleteConversation = ::undoDeleteConversation,
        submitUiCallback = ::submitUiCallback,
        resubmit = ::resubmit,
        enterInteractiveMode = ::enterInteractiveMode,
        exitInteractiveMode = ::exitInteractiveMode,
        goBackInteractiveMode = ::goBackInteractiveMode,
        sendSmsDraft = ::sendSmsDraft,
        discardSmsDraft = ::discardSmsDraft,
        consumeVoiceInputTrigger = ::consumeVoiceInputTrigger,
        cancelScheduledTask = ::cancelScheduledTask,
    )
    private val freeModeNames: Map<FreeMode, String> = FreeMode.entries.associateWith { "Free ${it.modelId.replaceFirstChar { c -> c.uppercase() }}" }
    private var currentJob: Job? = null
    private var pendingConversationDeleteJob: Job? = null
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            showPrivacyInfo = dataRepository.isUsingSharedKey(),
            isSpeechOutputEnabled = dataRepository.isVoiceResponseEnabled(),
            monitorOverlayMode = appSettings.getMonitorOverlayMode(),
            isAgentVisibilityEnabled = dataRepository.isAgentVisibilityEnabled(),
            isVlessEnabled = appSettings.isVlessEnabled(),
            systemStatus = null,
        ),
    )
    val monitorStats = monitorService.stats
    val wakeWordTriggered = wakeWordPlatform.wakeWordTriggered

    // Lazily-created device/network providers (Android actuals) reused across
    // polling ticks. The network provider is stateful: it derives live speed
    // from TrafficStats deltas between consecutive calls, so keep one instance.
    private val deviceInfoProvider: DeviceInfoProvider? by lazy { createDeviceInfoProvider() }
    private val networkStatusProvider: NetworkStatusProvider? by lazy { createNetworkStatusProvider() }

    init {
        updateAvailableServices()

        // Keep restoreCurrentConversation off the main thread; see issue #197 (large persisted
        // tool outputs caused ANRs when JSON-decoded synchronously during VM construction).
        // ChatScreen gates the interactive-mode branch on !isRestoring to avoid a flash.
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.loadConversations()
            dataRepository.restoreCurrentConversation()
            presetInteractiveModeForCurrentConversation()

            // Start Wake Word listening if enabled
            if (dataRepository.isWakeWordEnabled()) {
                val lang = dataRepository.getWakeWordModelLang()
                val url = when (lang) {
                    "ru" -> "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
                    "en" -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
                    else -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
                }
                wakeWordPlatform.startListening(url, dataRepository.getWakeWordTrigger())
            }

            _state.update { it.copy(isRestoring = false) }
        }

        viewModelScope.launch {
            wakeWordPlatform.wakeWordTriggered.collect {
                wakeWordPlatform.triggerWakeWordResponse(
                    dataRepository.isWakeWordVibrationEnabled(),
                    dataRepository.isWakeWordSoundEnabled(),
                )
            }
        }

        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.connectEnabledMcpServers()
        }
        viewModelScope.launch {
            dataRepository.fallbackStatus.collect { status ->
                _state.update { it.copy(fallbackStatus = status) }
            }
        }
        taskScheduler.isLoadingCheck = { _state.value.isLoading }
        taskScheduler.start()

        viewModelScope.launch {
            dataRepository.smsDrafts.collect { drafts ->
                _state.update { it.copy(smsDrafts = drafts.toImmutableList()) }
            }
        }

        // Live scheduled-task list so the chat widget (and any other consumer)
        // reflects tasks added, completed or cancelled by the scheduler/agent.
        viewModelScope.launch {
            dataRepository.scheduledTasksFlow.collect { tasks ->
                _state.update { it.copy(scheduledTasks = tasks.toImmutableList()) }
            }
        }

        viewModelScope.launch {
            dataRepository.openHeartbeatRequested
                .filter { it }
                .collect {
                    val heartbeatId = dataRepository.savedConversations.value
                        .firstOrNull { it.type == Conversation.TYPE_HEARTBEAT }?.id
                    if (heartbeatId != null) {
                        loadConversation(heartbeatId)
                        clearUnreadHeartbeat()
                    }
                    dataRepository.consumeOpenHeartbeatRequest()
                }
        }

        viewModelScope.launch {
            dataRepository.openAssistRequested
                .filter { it }
                .collect {
                    _state.update { it.copy(triggerVoiceInput = true) }
                    dataRepository.consumeOpenAssistRequest()
                }
        }

        viewModelScope.launch {
            dataRepository.sharedTextRequested
                .filter { it != null }
                .collect { shared ->
                    ask(shared)
                    dataRepository.consumeSharedTextRequest()
                }
        }

        viewModelScope.launch {
            appSettings.monitorOverlayModeFlow.collect { mode ->
                _state.update { it.copy(monitorOverlayMode = mode) }
                if (mode != com.katya.app.data.MonitorOverlayMode.OFF) {
                    monitorService.startMonitoring(
                        host = appSettings.getServerIp(),
                        port = appSettings.getServerPort(),
                        user = appSettings.getServerUser(),
                        pass = appSettings.getServerPassword(),
                        isFullMode = (mode == com.katya.app.data.MonitorOverlayMode.FULL),
                    )
                } else {
                    monitorService.stopMonitoring()
                }
            }
        }

        viewModelScope.launch {
            appSettings.systemStatusFlow.collect { status ->
                _state.update { it.copy(systemStatus = status) }
            }
        }

        // Tasks #8-10: surface device (CPU/RAM/battery) and connection
        // (speed/latency/indicator) states in the top bar whenever the
        // corresponding server-settings toggles are enabled. Nothing is shown
        // while both toggles are off, and the flags/strings are refreshed on a
        // 1.5 s tick so switching the toggles takes effect without a restart.
        viewModelScope.launch(backgroundDispatcher) {
            while (true) {
                val showDevice = appSettings.isShowDeviceStateEnabled()
                val showConnection = appSettings.isShowConnectionStateEnabled()
                if (showDevice || showConnection) {
                    val (connectionText, networkConnected) = if (showConnection) {
                        buildConnectionStatus(
                            selectedService = _state.value.availableServices.firstOrNull(),
                            isLoading = _state.value.isLoading,
                        )
                    } else {
                        null to false
                    }
                    _state.update {
                        it.copy(
                            showDeviceStatus = showDevice,
                            showConnectionStatus = showConnection,
                            deviceStatus = if (showDevice) buildDeviceStatusText() else null,
                            connectionStatus = connectionText,
                            isNetworkConnected = networkConnected,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            showDeviceStatus = false,
                            showConnectionStatus = false,
                            deviceStatus = null,
                            connectionStatus = null,
                            isNetworkConnected = false,
                        )
                    }
                }
                delay(1_500)
            }
        }
    }

    private suspend fun buildDeviceStatusText(): String? {
        val provider = deviceInfoProvider ?: return null
        val status = provider.getDeviceStatus() ?: return null
        val parts = buildList {
            status.cpuUsage?.let { add("CPU ${(it * 100).roundToInt()}%") }
            status.ramUsedPercent?.let { add("RAM ${(it * 100).roundToInt()}%") }
            if (status.batteryPercent != null) {
                val charging = if (status.isCharging == true) " (зарядка)" else ""
                add("Батарея ${status.batteryPercent}%$charging")
            }
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private suspend fun buildConnectionStatus(
        selectedService: ServiceEntry?,
        isLoading: Boolean,
    ): Pair<String?, Boolean> {
        if (!isLoading) return null to false

        val provider = networkStatusProvider ?: return null to false
        val status = provider.getNetworkStatus() ?: return null to false
        val apiText = selectedService?.serviceName?.let { "API: $it" } ?: "API: Auto"

        val parts = buildList {
            add("⚡ Активно")

            val d = status.downloadKbps ?: 0f
            val u = status.uploadKbps ?: 0f
            if (d > 0.1f || u > 0.1f) {
                if (d >= 0f) add("↓ ${formatSpeed(d)}")
                if (u >= 0f) add("↑ ${formatSpeed(u)}")
            }

            add(apiText)
        }
        return parts.joinToString(" · ") to true
    }

    private fun formatSpeed(kbps: Float): String = if (kbps >= 1024f) {
        "${formatOneDecimal(kbps / 1024f)} МБ/с"
    } else {
        "${kbps.roundToInt()} КБ/с"
    }

    private fun formatOneDecimal(value: Float): String {
        val rounded = (value * 10).roundToInt()
        return "${rounded / 10}.${rounded % 10}"
    }

    val state = combine(
        _state,
        dataRepository.chatHistory,
        dataRepository.savedConversations,
        dataRepository.currentConversationId,
        dataRepository.hasUnreadHeartbeat,
    ) { state, history, conversations, conversationId, hasUnreadHeartbeat ->
        val summaries = conversations
            .sortedByDescending { it.updatedAt }
            .map {
                val isHeartbeat = it.type == Conversation.TYPE_HEARTBEAT
                val isInteractive = it.type == Conversation.TYPE_INTERACTIVE
                ConversationSummary(
                    id = it.id,
                    title = if (isHeartbeat) "" else it.title.ifEmpty { getString(Res.string.conversation_untitled) },
                    updatedAt = it.updatedAt,
                    isHeartbeat = isHeartbeat,
                    isInteractive = isInteractive,
                )
            }
        state.copy(
            history = history.toImmutableList(),
            supportedFileExtensions = dataRepository.supportedFileExtensions().toImmutableList(),
            savedConversations = summaries.toImmutableList(),
            currentConversationId = conversationId,
            hasUnreadHeartbeat = hasUnreadHeartbeat,
            quickActions = dataRepository.getQuickActions().toImmutableList(),
            installedSkills = dataRepository.getInstalledSkills().toImmutableList(),
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun submitUiCallback(event: String, data: Map<String, String>) {
        val message = if (data.isNotEmpty()) {
            val formattedData = data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            "Responded with: $formattedData"
        } else {
            "Pressed: $event"
        }
        val lastAssistant = dataRepository.chatHistory.value.lastRenderedAssistant()
        val submission = lastAssistant?.let {
            UiSubmission(sourceContent = it.content, values = data, pressedEvent = event)
        }
        askInternal(message, submission)
    }

    private fun ask(question: String?) {
        askInternal(question, null)
    }

    private fun askInternal(question: String?, uiSubmission: UiSubmission?) {
        // Prevent concurrent requests
        if (_state.value.isLoading) return

        // Capture files before launching coroutine to avoid race with files being cleared
        val files = _state.value.files

        val (strippedQuestion, activeSkillId) = parseSkillInvocation(question)

        currentJob = viewModelScope.launch(backgroundDispatcher) {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    files = persistentListOf(),
                )
            }
            // Android 17+ blocks LAN traffic without the local network permission — without
            // asking first, requests to self-hosted servers silently never leave the device.
            if (!ensureLocalNetworkPermission()) {
                _state.update {
                    it.copy(
                        error = UiError.Resource(Res.string.error_local_network_permission),
                        isLoading = false,
                    )
                }
                return@launch
            }
            try {
                // Check if user is trying to send images to a vision-blind model
                if (files.any { it.mimeType()?.startsWith("image/") == true }) {
                    val serviceEntry = dataRepository.getServiceEntries().firstOrNull()
                    if (serviceEntry != null) {
                        val service = Service.fromId(serviceEntry.serviceId)
                        if (!service.supportsImages || !modelSupportsImages(serviceEntry.modelId)) {
                            _state.update {
                                it.copy(
                                    error = UiError.Text("Вы прикрепили изображение, но текущая модель не умеет их распознавать. Пожалуйста, выберите Vision-модель (например, gpt-4o, claude-3-opus, llava, qwen-vl)."),
                                    isLoading = false,
                                    files = files, // Restore files so user can remove them or change model
                                )
                            }
                            return@launch
                        }
                    }
                }

                dataRepository.ask(strippedQuestion, files, uiSubmission, activeSkillId)

                // Auto-retry in interactive mode if the response has no valid katya-ui
                if (_state.value.isInteractiveMode) {
                    retryIfNoValidKatyaUi()
                }

                _state.update {
                    it.copy(isLoading = false)
                }
            } catch (exception: Exception) {
                // CancellationException must be re-thrown to properly propagate coroutine cancellation
                if (exception is CancellationException) throw exception

                _state.update {
                    it.copy(
                        error = exception.toUiError(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /**
     * True unless the active service points at a local network host and the user
     * denied the local network permission. Cheap no-op on non-Android platforms.
     */
    private suspend fun ensureLocalNetworkPermission(): Boolean {
        val instance = dataRepository.getConfiguredServiceInstances().firstOrNull() ?: return true
        val baseUrl = dataRepository.getInstanceBaseUrl(instance.instanceId, Service.fromId(instance.serviceId))
        if (!isLocalNetworkUrl(baseUrl)) return true
        return localNetworkPermissionController.requestPermission()
    }

    private suspend fun retryIfNoValidKatyaUi(maxRetries: Int = 2) {
        repeat(maxRetries) {
            currentCoroutineContext().ensureActive()
            val lastAssistant = dataRepository.chatHistory.value.lastRenderedAssistant() ?: return

            val blocks = parseMarkdown(lastAssistant.content).blocks
            val hasValidUi = blocks.any { it is KatyaUiBlock }
            if (hasValidUi) return

            // Build error feedback for the AI
            val errorBlock = blocks.filterIsInstance<KatyaUiError>().firstOrNull()
            val errorDetail = if (errorBlock != null) {
                "JSON parse error in: ${errorBlock.rawJson.take(200)}"
            } else {
                "No katya-ui code fence found in your response."
            }
            val retryMessage = "[SYSTEM] Your previous response failed to render as interactive UI. $errorDetail " +
                "Remember: respond with ONLY a single ```katya-ui code fence containing valid JSON. No text outside the fence."

            dataRepository.ask(retryMessage, emptyList())
        }
    }

    private fun clearHistory() {
        dataRepository.clearHistory()
        _state.update {
            it.copy(error = null)
        }
    }

    /**
     * If [text] begins with `/<skill-id>`, look up the skill among the currently-
     * installed-and-enabled skills and return its id alongside the verbatim user
     * text. The text is sent unchanged so the conversation visibly reflects what
     * the user typed; the skill's instructions in the system prompt tell the model
     * how to parse the args after the slash command. Falls through with null skill
     * id when no match — slash commands are opt-in.
     */
    private fun parseSkillInvocation(text: String?): Pair<String?, String?> {
        if (text == null) return null to null
        val trimmed = text.trimStart()
        if (!trimmed.startsWith('/')) return text to null
        val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
        val rawId = if (firstSpace < 0) trimmed.substring(1) else trimmed.substring(1, firstSpace)
        if (rawId.isEmpty()) return text to null
        val skill = dataRepository.getInstalledSkills().firstOrNull { it.id.equals(rawId, ignoreCase = true) }
            ?: return text to null
        return text to skill.id
    }

    private fun setIsSpeaking(isSpeaking: Boolean, contentId: String) {
        _state.update {
            it.copy(
                isSpeaking = isSpeaking,
                isSpeakingContentId = if (isSpeaking) {
                    contentId
                } else {
                    it.isSpeakingContentId
                },
            )
        }
    }

    private fun addFile(file: com.katya.app.data.KatyaFile) {
        _state.update {
            it.copy(files = (it.files + file).toImmutableList())
        }
    }

    private fun removeFile(file: com.katya.app.data.KatyaFile) {
        _state.update {
            it.copy(files = it.files.filterNot { f -> f == file }.toImmutableList())
        }
    }

    private fun clearSnackbar() {
        _state.update {
            it.copy(snackbarMessage = null)
        }
    }

    private fun retry() {
        ask(null)
    }

    private fun toggleSpeechOutput() {
        val newState = !_state.value.isSpeechOutputEnabled
        dataRepository.setVoiceResponseEnabled(newState)
        _state.update {
            it.copy(
                isSpeechOutputEnabled = newState,
            )
        }
    }

    private fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.update {
            it.copy(isLoading = false)
        }
    }

    private fun selectService(instanceId: String) {
        val freeMode = FREE_MODE_INSTANCE_IDS[instanceId]
        if (freeMode != null) {
            dataRepository.setFreeMode(freeMode)
            dataRepository.setFreeServicePrimary(true)
            updateAvailableServices()
            return
        }

        dataRepository.setFreeServicePrimary(false)
        val instances = dataRepository.getConfiguredServiceInstances()
        val currentIds = instances.map { it.instanceId }
        if (instanceId !in currentIds) return
        val reordered = listOf(instanceId) + currentIds.filter { it != instanceId }
        dataRepository.reorderConfiguredServices(reordered)
        updateAvailableServices()
    }

    private fun updateAvailableServices() {
        val configuredEntries = dataRepository.getServiceEntries()
        val currentFreeMode = dataRepository.getFreeMode()
        val freeIsPrimary = dataRepository.isFreeServicePrimary() || configuredEntries.isEmpty()

        val freeModes = if (freeIsPrimary) {
            (listOf(currentFreeMode) + FreeMode.entries.filter { it != currentFreeMode }).map { mode ->
                ServiceEntry(
                    instanceId = mode.instanceId,
                    serviceId = Service.Free.id,
                    serviceName = freeModeNames.getValue(mode),
                    modelId = "",
                    icon = mode.icon,
                )
            }
        } else {
            // Only show the built-in Free (Kai standard) models while one of them is
            // actually selected. Once a real configured service is chosen, keep the
            // model list from being cluttered by the two always-present entries.
            emptyList()
        }

        val entries = if (freeIsPrimary) {
            freeModes + configuredEntries
        } else {
            configuredEntries
        }.toImmutableList()

        val primaryService = entries.firstOrNull()?.let { Service.fromId(it.serviceId) }
        val warning = if (primaryService?.isOnDevice == true && dataRepository.getLocalDownloadedModels().isEmpty()) {
            Res.string.litert_no_model_warning
        } else {
            null
        }
        _state.update { it.copy(availableServices = entries, warning = warning, showPrivacyInfo = dataRepository.isUsingSharedKey()) }
    }

    companion object {
        private val FREE_MODE_INSTANCE_IDS = FreeMode.entries.associateBy { it.instanceId }
    }

    private fun regenerate() {
        dataRepository.regenerate()
        ask(null)
    }

    private fun loadConversation(id: String) {
        currentJob?.cancel()
        currentJob = null
        val conversation = dataRepository.savedConversations.value.find { it.id == id }
        val isInteractive = conversation?.type == Conversation.TYPE_INTERACTIVE
        dataRepository.setInteractiveMode(isInteractive)
        dataRepository.loadConversation(id)
        _state.update {
            it.copy(error = null, isInteractiveMode = isInteractive, isLoading = false)
        }
    }

    private fun deleteConversation(id: String) {
        commitPendingConversationDeletion()
        _state.update { it.copy(pendingConversationDeletion = id) }
        pendingConversationDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            dataRepository.deleteConversation(id)
            _state.update { it.copy(pendingConversationDeletion = null) }
        }
    }

    private fun undoDeleteConversation() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        _state.update { it.copy(pendingConversationDeletion = null) }
    }

    private fun commitPendingConversationDeletion() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        val pendingId = _state.value.pendingConversationDeletion ?: return
        _state.update { it.copy(pendingConversationDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteConversation(pendingId)
        }
    }

    override fun onCleared() {
        commitPendingConversationDeletion()
        // The scheduler lives longer than this ViewModel (it's a singleton driving the
        // Android foreground service). Reset the predicate so the daemon path keeps
        // running without a stale reference to a dead state flow. The foreground-visible
        // signal (`appInForeground`) is tracked separately via `ProcessLifecycleOwner`
        // on Android — ViewModel lifecycle is too narrow (survives backgrounding).
        taskScheduler.isLoadingCheck = { false }
        super.onCleared()
    }

    private fun clearUnreadHeartbeat() {
        dataRepository.clearUnreadHeartbeat()
    }

    private fun sendSmsDraft(draftId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.sendSmsDraft(draftId)
        }
    }

    private fun discardSmsDraft(draftId: String) {
        viewModelScope.launch {
            dataRepository.discardSmsDraft(draftId)
        }
    }

    private fun consumeVoiceInputTrigger() {
        _state.update { it.copy(triggerVoiceInput = false) }
    }

    private fun cancelScheduledTask(id: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.cancelScheduledTask(id)
        }
    }

    private fun startNewChat() {
        currentJob?.cancel()
        currentJob = null
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(error = null, isInteractiveMode = false, isLoading = false)
        }
    }

    private fun enterInteractiveMode() {
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(true)
        _state.update {
            it.copy(isInteractiveMode = true, error = null)
        }
    }

    private fun exitInteractiveMode() {
        currentJob?.cancel()
        currentJob = null
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(isInteractiveMode = false, isLoading = false, error = null)
        }
    }

    private fun resubmit(messageId: String, event: String, data: Map<String, String>) {
        if (_state.value.isLoading) return
        dataRepository.truncateFrom(messageId)
        submitUiCallback(event, data)
    }

    private fun goBackInteractiveMode() {
        val userCount = dataRepository.chatHistory.value.count { it.role == History.Role.USER }
        if (userCount <= 1) {
            // Go back to initial prompt — clear history but stay in interactive mode
            dataRepository.clearHistory()
        } else {
            dataRepository.popLastExchange()
        }
    }

    fun refreshSettings() {
        _state.update {
            it.copy(
                showPrivacyInfo = dataRepository.isUsingSharedKey(),
                isSpeechOutputEnabled = dataRepository.isVoiceResponseEnabled(),
                monitorOverlayMode = appSettings.getMonitorOverlayMode(),
                isAgentVisibilityEnabled = dataRepository.isAgentVisibilityEnabled(),
            )
        }
        updateAvailableServices()
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.restoreCurrentConversation()
            presetInteractiveModeForCurrentConversation()
        }
    }

    /**
     * Resolves the interactive mode flag from the currently-loaded conversation, or — when
     * there is no loaded conversation (new empty chat) — falls back to the persisted flag.
     */
    private fun presetInteractiveModeForCurrentConversation() {
        val currentId = dataRepository.currentConversationId.value
        val conversation = dataRepository.savedConversations.value.find { it.id == currentId }
        val isInteractive = if (conversation != null) {
            conversation.type == Conversation.TYPE_INTERACTIVE
        } else {
            dataRepository.isInteractiveModeActive()
        }
        dataRepository.setInteractiveMode(isInteractive)
        _state.update { it.copy(isInteractiveMode = isInteractive) }
    }
}
