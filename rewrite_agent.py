import re

with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/AgentSettings.kt', 'r') as f:
    content = f.read()

# We want to replace the `AgentContent` function with a new implementation.
# Find `internal fun AgentContent(`
start_idx = content.find('internal fun AgentContent(')
if start_idx == -1:
    print("Error: AgentContent not found!")
    exit(1)

# Find the end of `AgentContent` by counting braces
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
    print("Error: End of AgentContent not found!")
    exit(1)

new_agent_content = """internal fun AgentContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    textToSpeech: com.katya.app.tts.SpeechEngine? = null,
) {
    var isSystemPromptsExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Настройки агента ---
        SettingsCard {
            Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                Text(
                    text = "Настройки агента",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = androidx.compose.ui.Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                VisionHelperCard(
                    configuredServices = uiState.configuredServices,
                    visionHelperInstanceId = uiState.visionHelperInstanceId,
                    onSelectVisionHelper = actions.onSelectVisionHelper,
                )
                
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                
                AgentModeCard(
                    agentMode = uiState.agentMode,
                    onChangeAgentMode = actions.onChangeAgentMode,
                )

                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

                if (uiState.showUiScale) {
                    UiScaleSection(
                        uiScale = uiState.uiScale,
                        onChangeUiScale = actions.onChangeUiScale,
                    )
                }

                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                
                QuickActionsSection(
                    quickActions = uiState.quickActions,
                    onAddQuickAction = actions.onAddQuickAction,
                    onUpdateQuickAction = actions.onUpdateQuickAction,
                    onDeleteQuickAction = actions.onDeleteQuickAction,
                )
            }
        }

        // --- Системные промпты (spoiler) ---
        SettingsCard {
            Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().clickable { isSystemPromptsExpanded = !isSystemPromptsExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Системные промпты",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isSystemPromptsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedVisibility(visible = isSystemPromptsExpanded) {
                    Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                        SoulEditor(
                            soulText = uiState.soulText,
                            onSaveSoul = actions.onSaveSoul,
                        )
                    }
                }
            }
        }
        
        // Retain memories and scheduled tasks
        SettingsCard {
            ScheduledTaskList(
                tasks = uiState.scheduledTasks,
                heartbeatLog = uiState.heartbeatLog,
                isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                isRefreshingHeartbeat = uiState.isRefreshingHeartbeat,
                onToggleHeartbeat = actions.onToggleHeartbeat,
                onRefreshHeartbeat = actions.onRefreshHeartbeat,
                onCancelTask = actions.onCancelTask,
            )
        }

        SettingsCard {
            MemoriesList(
                memories = uiState.memories,
                onDeleteMemory = actions.onDeleteMemory,
                onUpdateMemory = actions.onUpdateMemory,
            )
        }
    }
}
"""

# Replace the content
content = content[:start_idx] + new_agent_content + content[end_idx+1:]

# Save
with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/AgentSettings.kt', 'w') as f:
    f.write(content)

print("AgentSettings.kt updated successfully!")
