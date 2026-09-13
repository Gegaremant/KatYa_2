import re

with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/ServicesSettings.kt', 'r') as f:
    content = f.read()

start_idx = content.find('internal fun ServicesContent(')
if start_idx == -1:
    print("Error: ServicesContent not found")
    exit(1)

def find_end_brace(text, start_index):
    count = 0
    in_str = False
    for i in range(start_index, len(text)):
        if text[i] == '"':
            in_str = not in_str
        if not in_str:
            if text[i] == '{':
                count += 1
            elif text[i] == '}':
                count -= 1
                if count == 0:
                    return i
    return -1

brace_start = content.find('{', start_idx)
end_idx = find_end_brace(content, brace_start)

if end_idx == -1:
    print("Error: End of ServicesContent not found")
    exit(1)

original_body = content[brace_start+1:end_idx]

new_services_content = """internal fun ServicesContent(uiState: SettingsUiState, actions: SettingsActions) {
    var showAddServiceSheet by remember { mutableStateOf(false) }
    var serviceFilter by remember { mutableStateOf<ServiceFilter?>(null) }
    var isSshBridgeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Model Providers ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Провайдеры моделей",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Original list of services and buttons
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
                                localDownloadingModelIds = uiState.localDownloadingModelIds,
                                localDownloadProgresses = uiState.localDownloadProgresses,
                                localDownloadErrors = uiState.localDownloadErrors,
                                onDownloadLocalModel = actions.onDownloadLocalModel,
                                onCancelLocalModelDownload = actions.onCancelLocalModelDownload,
                                onImportLocalModel = actions.onImportLocalModel,
                                onDeleteLocalModel = actions.onDeleteLocalModel,
                                onSaveLocalModelToDevice = actions.onSaveLocalModelToDevice,
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
                                onShowDeepSeekAuthDialog = actions.onShowDeepSeekAuthDialog,
                            )
                        }
                    }
                }

                if (uiState.availableServicesToAdd.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        var showFreeDropdown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showFreeDropdown = true },
                                modifier = Modifier.fillMaxWidth().handCursor(),
                            ) {
                                Text("+ Бесплатный")
                            }
                            DropdownMenu(
                                expanded = showFreeDropdown,
                                onDismissRequest = { showFreeDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                            ) {
                                val allServices = uiState.availableServicesToAdd
                                val localModelService = allServices.find { it is Service.LiteRT }
                                val freeDeepSeekProxy = allServices.find { it is Service.FreeDeepSeekProxy }
                                val legacyAiService = allServices.find { it is Service.LegacyFree }
                                val selfHostedService = allServices.find { it is Service.OpenAICompatible }

                                listOf(
                                    "Local model" to localModelService,
                                    "Free deepseek proxy" to freeDeepSeekProxy,
                                    "Legacy brain" to legacyAiService,
                                    "Self hosted" to selfHostedService,
                                ).forEach { (label, service) ->
                                    if (service != null) {
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                actions.onAddService(service)
                                                showFreeDropdown = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                serviceFilter = ServiceFilter.PAID
                                showAddServiceSheet = true
                            },
                            modifier = Modifier.weight(1f).handCursor(),
                        ) {
                            Text("+ Внешний API")
                        }
                    }
                }
            }
        }

        // --- SSH Bridge ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isSshBridgeExpanded = !isSshBridgeExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SSH Bridge",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isSshBridgeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = isSshBridgeExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ToggleableHeadline(
                            title = "VLESS Прокси",
                            description = "Встроенный клиент для обхода блокировок",
                            checked = uiState.isVlessEnabled,
                            onCheckedChange = { /* TODO: actions.onToggleVless if it exists, or just disable for now if not found */ }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Add service bottom sheet
    if (showAddServiceSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddServiceSheet = false
                serviceFilter = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val addServiceScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(addServiceScrollState).padding(16.dp)) {
                    val allServices = uiState.availableServicesToAdd
                    val filteredServices = allServices.filter { service ->
                        !service.isOnDevice &&
                            service !is Service.FreeDeepSeekProxy &&
                            service.apiKeyUrl != null
                    }
                    val services = filteredServices.toImmutableList()
                    services.forEachIndexed { index, service ->
                        val isFirst = index == 0
                        val isLast = index == services.lastIndex
                        val itemShape = RoundedCornerShape(
                            topStart = if (isFirst) 12.dp else 0.dp,
                            topEnd = if (isFirst) 12.dp else 0.dp,
                            bottomStart = if (isLast) 12.dp else 0.dp,
                            bottomEnd = if (isLast) 12.dp else 0.dp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(itemShape).clickable {
                                actions.onAddService(service)
                                showAddServiceSheet = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ResourceImage(
                                resourceId = service.logoResId,
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                                contentDescription = service.displayName,
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (!isLast) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                VerticalScrollbarForScroll(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    scrollState = addServiceScrollState,
                )
            }
        }
    }
}
"""

content = content[:start_idx] + new_services_content + content[end_idx+1:]

with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/ServicesSettings.kt', 'w') as f:
    f.write(content)

print("ServicesSettings updated successfully.")
