@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.katya.app.data.Service
import com.katya.app.formatFileSize
import com.katya.app.inference.DevicePerformance
import com.katya.app.inference.DownloadError
import com.katya.app.inference.LocalModel
import com.katya.app.inference.calculateDevicePerformance
import com.katya.app.inference.estimateGpuMemoryMb
import com.katya.app.network.dtos.SponsorsResponseDto
import com.katya.app.ui.KaiClearableTextField
import com.katya.app.ui.components.KatyaSlider
import com.katya.app.ui.components.VerticalScrollbarForScroll
import com.katya.app.ui.handCursor
import com.katya.app.ui.icons.DragIndicator
import com.katya.app.ui.katyaAdaptiveCardBorder
import com.katya.app.ui.katyaAdaptiveCardColors
import com.katya.app.ui.katyaAdaptiveCardSurface
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.ic_arrow_drop_down
import katya.composeapp.generated.resources.litert_cancel
import katya.composeapp.generated.resources.litert_context_size
import katya.composeapp.generated.resources.litert_download
import katya.composeapp.generated.resources.litert_error_download_incomplete
import katya.composeapp.generated.resources.litert_error_network
import katya.composeapp.generated.resources.litert_error_not_enough_disk_space
import katya.composeapp.generated.resources.litert_free_space
import katya.composeapp.generated.resources.litert_on_device_description
import katya.composeapp.generated.resources.litert_performance_good
import katya.composeapp.generated.resources.litert_performance_ok
import katya.composeapp.generated.resources.litert_performance_poor
import katya.composeapp.generated.resources.litert_recommended
import katya.composeapp.generated.resources.litert_tool_support
import katya.composeapp.generated.resources.settings_add_service
import katya.composeapp.generated.resources.settings_api_key_label
import katya.composeapp.generated.resources.settings_api_key_optional_label
import katya.composeapp.generated.resources.settings_base_url_label
import katya.composeapp.generated.resources.settings_become_sponsor
import katya.composeapp.generated.resources.settings_business_partnerships
import katya.composeapp.generated.resources.settings_business_partnerships_description
import katya.composeapp.generated.resources.settings_contact_sponsorship
import katya.composeapp.generated.resources.settings_free_fallback
import katya.composeapp.generated.resources.settings_free_tier_description
import katya.composeapp.generated.resources.settings_free_tier_title
import katya.composeapp.generated.resources.settings_open_app_settings
import katya.composeapp.generated.resources.settings_openai_compatible_or_other_service
import katya.composeapp.generated.resources.settings_openai_compatible_providers
import katya.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import katya.composeapp.generated.resources.settings_remove_service
import katya.composeapp.generated.resources.settings_reorder_content_description
import katya.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import katya.composeapp.generated.resources.settings_sponsors
import katya.composeapp.generated.resources.settings_status_checking
import katya.composeapp.generated.resources.settings_status_connected
import katya.composeapp.generated.resources.settings_status_error
import katya.composeapp.generated.resources.settings_status_error_connection_failed
import katya.composeapp.generated.resources.settings_status_error_invalid_key
import katya.composeapp.generated.resources.settings_status_error_local_network
import katya.composeapp.generated.resources.settings_status_error_quota_exhausted
import katya.composeapp.generated.resources.settings_status_error_rate_limited
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt

@Composable
internal fun FreeSettings(
    showFallbackToggle: Boolean = false,
    isFreeFallbackEnabled: Boolean = true,
    onToggleFreeFallback: (Boolean) -> Unit = {},
    currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = katyaAdaptiveCardColors(),
        border = katyaAdaptiveCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_free_tier_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (showFallbackToggle) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleFreeFallback(!isFreeFallbackEnabled) }
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_free_fallback),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isFreeFallbackEnabled,
                        onCheckedChange = onToggleFreeFallback,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.settings_free_tier_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SponsorList(
    title: String,
    sponsors: ImmutableList<SponsorsResponseDto.Sponsor>,
) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sponsors.forEach { sponsor ->
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { uriHandler.openUri("https://github.com/${sponsor.username}") }
                    .handCursor()
                    .padding(4.dp),
            ) {
                coil3.compose.AsyncImage(
                    model = sponsor.avatar,
                    contentDescription = sponsor.username,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sponsor.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun ServicesContent(uiState: SettingsUiState, actions: SettingsActions) {
    var showAddServiceSheet by remember { mutableStateOf(false) }

    // Configured services list
    val entries = uiState.configuredServices
    ReorderableColumn(
        list = entries,
        onSettle = { fromIndex, toIndex ->
            val ids = entries.map { it.instanceId }.toMutableList()
            ids.add(toIndex, ids.removeAt(fromIndex))
            actions.onReorderServices(ids)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { _, entry, isDragging ->
        key(entry.instanceId) {
            ReorderableItem {
                ConfiguredServiceCardContent(
                    entry = entry,
                    isExpanded = uiState.expandedServiceId == entry.instanceId,
                    onExpand = { actions.onExpandService(if (uiState.expandedServiceId == entry.instanceId) null else entry.instanceId) },
                    onChangeApiKey = { apiKey -> actions.onChangeApiKey(entry.instanceId, apiKey) },
                    onChangeBaseUrl = { baseUrl -> actions.onChangeBaseUrl(entry.instanceId, baseUrl) },
                    onSelectModel = { modelId -> actions.onSelectModel(entry.instanceId, modelId) },
                    onRemove = { actions.onRemoveService(entry.instanceId) },
                    isDragging = isDragging,
                    dragHandleModifier = if (entries.size >= 2) Modifier.draggableHandle() else null,
                    localAvailableModels = uiState.localAvailableModels,
                    totalDeviceMemoryBytes = uiState.totalDeviceMemoryBytes,
                    localFreeSpaceBytes = uiState.localFreeSpaceBytes,
                    localDownloadingModelId = uiState.localDownloadingModelId,
                    localDownloadProgress = uiState.localDownloadProgress,
                    localDownloadError = uiState.localDownloadError,
                    onDownloadLocalModel = actions.onDownloadLocalModel,
                    onCancelLocalModelDownload = actions.onCancelLocalModelDownload,
                    onDeleteLocalModel = actions.onDeleteLocalModel,
                    onChangeModelContextTokens = actions.onChangeModelContextTokens,
                    modelContextTokens = uiState.modelContextTokens,
                    onOpenAppPermissionSettings = actions.onOpenAppPermissionSettings,
                    onRecheckLocalNetworkPermission = { actions.onRecheckLocalNetworkPermission(entry.instanceId) },
                    hfRepoUrl = uiState.hfRepoUrl,
                    onChangeHfRepoUrl = actions.onChangeHfRepoUrl,
                    onFetchHfModels = actions.onFetchHfModels,
                    isFetchingHfModels = uiState.isFetchingHfModels,
                    hfError = uiState.hfError,
                    hfModels = uiState.hfModels,
                )
            }
        }
    }

    if (uiState.availableServicesToAdd.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAddServiceSheet = true }, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.settings_add_service))
        }
    }

    // Free tier card (always at bottom)
    Spacer(Modifier.height(16.dp))
    FreeSettings(
        showFallbackToggle = entries.isNotEmpty(),
        isFreeFallbackEnabled = uiState.isFreeFallbackEnabled,
        onToggleFreeFallback = actions.onToggleFreeFallback,
        currentSponsors = uiState.currentSponsors,
        pastSponsors = uiState.pastSponsors,
    )

    // Add service bottom sheet
    if (showAddServiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddServiceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val addServiceScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(addServiceScrollState).padding(16.dp)) {
                    val services = uiState.availableServicesToAdd
                    services.forEachIndexed { index, service ->
                        val isFirst = index == 0
                        val isLast = index == services.lastIndex
                        val itemShape = RoundedCornerShape(
                            topStart = if (isFirst) 12.dp else 0.dp,
                            topEnd = if (isFirst) 12.dp else 0.dp,
                            bottomStart = if (isLast) 12.dp else 0.dp,
                            bottomEnd = if (isLast) 12.dp else 0.dp,
                        )
                        val isSpecial = service.isOnDevice || service is Service.OpenAICompatible || service is Service.AtlasCloud
                        Surface(
                            onClick = {
                                actions.onAddService(service)
                                showAddServiceSheet = false
                            },
                            modifier = Modifier.fillMaxWidth().handCursor(),
                            shape = itemShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(
                                            if (isSpecial) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = vectorResource(service.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = service.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                VerticalScrollbarForScroll(
                    scrollState = addServiceScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ConfiguredServiceCardContent(
    entry: ConfiguredServiceEntry,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onChangeApiKey: (String) -> Unit,
    onChangeBaseUrl: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
    localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    localFreeSpaceBytes: Long = 0L,
    localDownloadingModelId: String? = null,
    localDownloadProgress: Float? = null,
    localDownloadError: DownloadError? = null,
    onDownloadLocalModel: (LocalModel) -> Unit = {},
    onCancelLocalModelDownload: () -> Unit = {},
    onDeleteLocalModel: (String) -> Unit = {},
    onChangeModelContextTokens: (String, Int) -> Unit = { _, _ -> },
    modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    onOpenAppPermissionSettings: () -> Unit = {},
    onRecheckLocalNetworkPermission: () -> Unit = {},
    hfRepoUrl: String = "",
    onChangeHfRepoUrl: (String) -> Unit = {},
    onFetchHfModels: () -> Unit = {},
    isFetchingHfModels: Boolean = false,
    hfError: String? = null,
    hfModels: ImmutableList<LocalModel> = persistentListOf(),
) {
    // Clear a stale denied status when the user returns from granting the permission in
    // system settings; the recheck never re-prompts, so this is a no-op while still denied.
    if (entry.connectionStatus == ConnectionStatus.ErrorLocalNetworkDenied) {
        LifecycleResumeEffect(entry.instanceId) {
            onRecheckLocalNetworkPermission()
            onPauseOrDispose { }
        }
    }
    Column(
        modifier = Modifier
            .katyaAdaptiveCardSurface()
            .fillMaxWidth()
            .clickable { onExpand() }
            .handCursor(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                if (dragHandleModifier != null) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(Res.string.settings_reorder_content_description),
                        modifier = dragHandleModifier.handCursor(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Connection status dot
                val dotColor = when (entry.connectionStatus) {
                    ConnectionStatus.Connected -> StatusColorConnected
                    ConnectionStatus.Checking -> StatusColorChecking
                    ConnectionStatus.Unknown -> StatusColorUnknown
                    else -> StatusColorError
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )

                Spacer(Modifier.width(12.dp))

                // Service name and model
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.service.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (entry.selectedModel != null) {
                        Text(
                            text = entry.selectedModel.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Expand/collapse chevron
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Expanded content
        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                if (entry.service.isOnDevice) {
                    LiteRTSettings(
                        selectedModel = entry.selectedModel,
                        downloadedModels = entry.models,
                        availableModels = localAvailableModels,
                        totalDeviceMemoryBytes = totalDeviceMemoryBytes,
                        freeSpaceBytes = localFreeSpaceBytes,
                        downloadingModelId = localDownloadingModelId,
                        downloadProgress = localDownloadProgress,
                        downloadError = localDownloadError,
                        onSelectModel = onSelectModel,
                        onDownloadModel = onDownloadLocalModel,
                        onCancelDownload = onCancelLocalModelDownload,
                        onDeleteModel = onDeleteLocalModel,
                        onChangeModelContextTokens = onChangeModelContextTokens,
                        modelContextTokens = modelContextTokens,
                        hfRepoUrl = hfRepoUrl,
                        onChangeHfRepoUrl = onChangeHfRepoUrl,
                        onFetchHfModels = onFetchHfModels,
                        isFetchingHfModels = isFetchingHfModels,
                        hfError = hfError,
                        hfModels = hfModels,
                    )
                } else if (entry.service is Service.OpenAICompatible) {
                    OpenAICompatibleSettings(
                        baseUrl = entry.baseUrl,
                        onChangeBaseUrl = onChangeBaseUrl,
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                        onOpenAppPermissionSettings = onOpenAppPermissionSettings,
                    )
                } else {
                    ServiceSettings(
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        apiKeyUrl = entry.service.apiKeyUrl ?: "",
                        apiKeyUrlDisplay = entry.service.apiKeyUrlDisplay ?: "",
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                        onOpenAppPermissionSettings = onOpenAppPermissionSettings,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Remove action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_remove_service),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceSettings(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    apiKeyUrl: String,
    apiKeyUrlDisplay: String,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
    testTag: String? = null,
    onOpenAppPermissionSettings: () -> Unit = {},
) {
    ApiKeyField(
        apiKey = apiKey,
        onChangeApiKey = onChangeApiKey,
        labelText = stringResource(Res.string.settings_api_key_label),
        testTag = testTag,
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus, onOpenAppPermissionSettings)

    Spacer(Modifier.height(8.dp))

    val linkColor = MaterialTheme.colorScheme.primary

    val copyApiKeyPromptString = stringResource(Res.string.settings_sign_in_copy_api_key_from)
    val annotatedString = remember(apiKeyUrl, apiKeyUrlDisplay) {
        buildAnnotatedString {
            append(copyApiKeyPromptString)
            append(" ")
            withLink(LinkAnnotation.Url(url = apiKeyUrl)) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append(apiKeyUrlDisplay)
                }
            }
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected || models.isNotEmpty()) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun OpenAICompatibleSettings(
    baseUrl: String,
    onChangeBaseUrl: (String) -> Unit,
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
    onOpenAppPermissionSettings: () -> Unit = {},
) {
    KaiClearableTextField(
        value = baseUrl,
        onValueChange = onChangeBaseUrl,
        label = {
            Text(
                stringResource(Res.string.settings_base_url_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = true,
    )
    if (baseUrl.isNotBlank()) {
        Text(
            text = "${baseUrl.trimEnd('/')}${Service.OpenAICompatible.chatUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
        )
    }

    Spacer(Modifier.height(8.dp))

    ApiKeyField(
        apiKey = apiKey,
        onChangeApiKey = onChangeApiKey,
        labelText = stringResource(Res.string.settings_api_key_optional_label),
        singleLine = true,
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus, onOpenAppPermissionSettings)

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Подключение к локальным AI моделям. При необходимости настройте SSH туннель на вкладке 'Серверы' для удаленного доступа.",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun LiteRTSettings(
    selectedModel: SettingsModel?,
    downloadedModels: ImmutableList<SettingsModel>,
    availableModels: ImmutableList<LocalModel>,
    totalDeviceMemoryBytes: Long,
    freeSpaceBytes: Long,
    downloadingModelId: String?,
    downloadProgress: Float?,
    downloadError: DownloadError?,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (LocalModel) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onChangeModelContextTokens: (String, Int) -> Unit,
    modelContextTokens: ImmutableMap<String, Int>,
    hfRepoUrl: String = "",
    onChangeHfRepoUrl: (String) -> Unit = {},
    onFetchHfModels: () -> Unit = {},
    isFetchingHfModels: Boolean = false,
    hfError: String? = null,
    hfModels: ImmutableList<LocalModel> = persistentListOf(),
) {
    val downloadedIds = remember(downloadedModels) { downloadedModels.map { it.id }.toSet() }

    Text(
        text = stringResource(Res.string.litert_on_device_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = stringResource(Res.string.litert_tool_support),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    availableModels.forEach { model ->
        val isDownloaded = model.id in downloadedIds
        val isSelected = selectedModel?.id == model.id
        val isDownloading = downloadingModelId == model.id
        val steps = (model.maxContextTokens - model.defaultContextTokens) / 1024
        val storedContextTokens = modelContextTokens[model.id] ?: model.defaultContextTokens
        var contextSliderValue by remember(storedContextTokens) {
            mutableStateOf(((storedContextTokens - model.defaultContextTokens) / 1024).toFloat())
        }
        val contextTokens = model.defaultContextTokens + (contextSliderValue.roundToInt() * 1024)
        val estimatedMemoryMb = estimateGpuMemoryMb(model, contextTokens)
        val performance = calculateDevicePerformance(totalDeviceMemoryBytes, estimatedMemoryMb)

        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = if (isSelected) 3.dp else 1.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDownloaded) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectModel(model.id) },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (model.isRecommended) {
                                "${model.displayName} (${stringResource(Res.string.litert_recommended)})"
                            } else {
                                model.displayName
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatFileSize(model.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            DevicePerformanceLabel(performance)
                        }
                    }
                    if (isDownloaded) {
                        IconButton(
                            onClick = { onDeleteModel(model.id) },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!isDownloading) {
                        TextButton(
                            onClick = { onDownloadModel(model) },
                            modifier = Modifier.handCursor(),
                            enabled = downloadingModelId == null,
                        ) {
                            Text(stringResource(Res.string.litert_download))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.litert_context_size, "${contextTokens / 1024}K"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KatyaSlider(
                    value = contextSliderValue,
                    onValueChange = { contextSliderValue = it },
                    onValueChangeFinished = {
                        onChangeModelContextTokens(model.id, contextTokens)
                    },
                    valueRange = 0f..steps.toFloat(),
                    steps = steps - 1,
                )
                if (isDownloading && downloadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(
                                text = stringResource(Res.string.litert_cancel),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (downloadError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when (downloadError) {
                    DownloadError.NOT_ENOUGH_DISK_SPACE -> Res.string.litert_error_not_enough_disk_space
                    DownloadError.NETWORK_ERROR -> Res.string.litert_error_network
                    DownloadError.DOWNLOAD_INCOMPLETE -> Res.string.litert_error_download_incomplete
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.litert_free_space, formatFileSize(freeSpaceBytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = "HuggingFace Custom Models",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Вставьте ссылку на репозиторий с моделями в формате GGUF или LiteRT.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        KaiClearableTextField(
            value = hfRepoUrl,
            onValueChange = onChangeHfRepoUrl,
            label = { Text("https://huggingface.co/...", color = MaterialTheme.colorScheme.onBackground) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onFetchHfModels,
            enabled = !isFetchingHfModels && hfRepoUrl.isNotBlank(),
        ) {
            if (isFetchingHfModels) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Поиск")
            }
        }
    }
    if (hfError != null) {
        Spacer(Modifier.height(4.dp))
        Text(hfError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (hfModels.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))

        val fitsDeviceModels = hfModels.filter { calculateDevicePerformance(totalDeviceMemoryBytes, estimateGpuMemoryMb(it, it.defaultContextTokens)) != DevicePerformance.POOR }

        if (fitsDeviceModels.isEmpty()) {
            val hfLink = hfRepoUrl.trim().takeIf { it.startsWith("http") } ?: "https://$hfRepoUrl"
            Text(
                "Найденные модели не подходят для данного устройства из-за нехватки оперативной памяти. " +
                    "Попробуйте поискать более легкие квантованные версии (например, Q4_K_M) в этом репозитории.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            val annotatedLink = buildAnnotatedString {
                withLink(LinkAnnotation.Url(url = hfLink)) {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Перейти к репозиторию")
                    }
                }
            }
            Text(annotatedLink)
        } else {
            fitsDeviceModels.forEach { model ->
                val isDownloaded = model.id in downloadedIds
                val isSelected = selectedModel?.id == model.id
                val isDownloading = downloadingModelId == model.id
                val estimatedMemoryMb = estimateGpuMemoryMb(model, model.defaultContextTokens)
                val performance = calculateDevicePerformance(totalDeviceMemoryBytes, estimatedMemoryMb)

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = if (isSelected) 3.dp else 1.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDownloaded) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectModel(model.id) },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatFileSize(model.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    DevicePerformanceLabel(performance)
                                }
                            }
                            if (isDownloaded) {
                                IconButton(
                                    onClick = { onDeleteModel(model.id) },
                                    modifier = Modifier.handCursor(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else if (!isDownloading) {
                                TextButton(
                                    onClick = { onDownloadModel(model) },
                                    modifier = Modifier.handCursor(),
                                    enabled = downloadingModelId == null,
                                ) {
                                    Text(stringResource(Res.string.litert_download))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePerformanceLabel(performance: DevicePerformance) {
    when (performance) {
        DevicePerformance.GOOD -> Text(
            text = stringResource(Res.string.litert_performance_good),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorConnected,
        )

        DevicePerformance.OK -> Text(
            text = stringResource(Res.string.litert_performance_ok),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorChecking,
        )

        DevicePerformance.POOR -> Text(
            text = stringResource(Res.string.litert_performance_poor),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorError,
        )
    }
}

@Composable
private fun ConnectionStatusIndicator(status: ConnectionStatus, onOpenAppPermissionSettings: () -> Unit = {}) {
    when (status) {
        ConnectionStatus.Unknown -> return

        ConnectionStatus.Checking -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ConnectionStatus.Connected -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ConnectionStatus.ErrorQuotaExhausted -> {
            val warningColor = Color(0xFFFF9800)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = warningColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_error_quota_exhausted),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor,
                )
            }
        }

        ConnectionStatus.ErrorInvalidKey,
        ConnectionStatus.ErrorRateLimited,
        ConnectionStatus.ErrorConnectionFailed,
        ConnectionStatus.ErrorLocalNetworkDenied,
        ConnectionStatus.Error,
        -> {
            val errorMessage = when (status) {
                ConnectionStatus.ErrorInvalidKey -> stringResource(Res.string.settings_status_error_invalid_key)
                ConnectionStatus.ErrorRateLimited -> stringResource(Res.string.settings_status_error_rate_limited)
                ConnectionStatus.ErrorConnectionFailed -> stringResource(Res.string.settings_status_error_connection_failed)
                ConnectionStatus.ErrorLocalNetworkDenied -> stringResource(Res.string.settings_status_error_local_network)
                else -> stringResource(Res.string.settings_status_error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (status == ConnectionStatus.ErrorLocalNetworkDenied) {
                TextButton(
                    onClick = onOpenAppPermissionSettings,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_open_app_settings))
                }
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    labelText: String,
    testTag: String? = null,
    singleLine: Boolean = false,
) {
    KaiClearableTextField(
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                labelText,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = singleLine,
    )
}
