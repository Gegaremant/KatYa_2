import re

with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/ToolsSettings.kt', 'r') as f:
    content = f.read()

start_idx = content.find('internal fun ToolsContent(')
if start_idx == -1:
    print("Error: ToolsContent not found")
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
    print("Error: End of ToolsContent not found")
    exit(1)

new_tools_content = """internal fun ToolsContent(
    tools: ImmutableList<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
    mcpServers: ImmutableList<McpServerUiState>,
    onAddMcpServer: (String, String, Map<String, String>) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpServer: (String) -> Unit,
    showAddMcpServerDialog: Boolean,
    onShowAddMcpServerDialog: (Boolean) -> Unit,
    onAddPopularMcpServer: (PopularMcpServer) -> Unit,
    skills: ImmutableList<SkillManifest>,
    onUninstallSkill: (String) -> Unit,
    showAddSkillDialog: Boolean,
    onShowAddSkillDialog: (Boolean) -> Unit,
    onInstallGitHubSkill: (String) -> Unit,
    onInstallBrowsedSkill: (RegistrySkillEntry) -> Unit,
    isInstallingSkill: Boolean,
    skillInstallError: String?,
    browsableSkills: ImmutableList<RegistrySkillEntry>,
    isBrowsingSkills: Boolean,
    browseSkillsFailed: Boolean,
) {
    var isMcpExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isPermissionsExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Управление инструментами ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Управление инструментами",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                if (tools.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.settings_tools_none_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = when {
                            maxWidth >= 800.dp -> 3
                            maxWidth >= 500.dp -> 2
                            else -> 1
                        }
                        val rows = tools.chunked(columns)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { rowTools ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowTools.forEach { tool ->
                                        ToolItem(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            tool = tool,
                                            onToggle = { enabled -> onToggleTool(tool.id, enabled) },
                                        )
                                    }
                                    repeat(columns - rowTools.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- MCP Серверы и Навыки (spoiler) ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isMcpExpanded = !isMcpExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MCP Серверы и Навыки",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.Icon(
                        imageVector = if (isMcpExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                        contentDescription = "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = isMcpExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        McpServersSection(
                            mcpServers = mcpServers,
                            onAddMcpServer = onAddMcpServer,
                            onRemoveMcpServer = onRemoveMcpServer,
                            onToggleMcpServer = onToggleMcpServer,
                            onRefreshMcpServer = onRefreshMcpServer,
                            onToggleTool = onToggleTool,
                            showAddDialog = showAddMcpServerDialog,
                            onShowAddDialog = onShowAddMcpServerDialog,
                            onAddPopularMcpServer = onAddPopularMcpServer,
                        )

                        Spacer(Modifier.height(16.dp))
                        SkillsSection(
                            skills = skills,
                            onUninstallSkill = onUninstallSkill,
                            showAddDialog = showAddSkillDialog,
                            onShowAddDialog = onShowAddSkillDialog,
                            onInstallGitHub = onInstallGitHubSkill,
                            onInstallBrowsed = onInstallBrowsedSkill,
                            isInstalling = isInstallingSkill,
                            installError = skillInstallError,
                            browsableSkills = browsableSkills,
                            isBrowsing = isBrowsingSkills,
                            browseFailed = browseSkillsFailed,
                        )
                    }
                }
            }
        }

        // --- Permissions (spoiler) ---
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isPermissionsExpanded = !isPermissionsExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.Icon(
                        imageVector = if (isPermissionsExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                        contentDescription = "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = isPermissionsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Управление правами доступа Android (микрофон, геолокация и т.д.) осуществляется в системных настройках приложения.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Button(onClick = { com.katya.app.openAppPermissionSettings() }) {
                            Text("Открыть системные настройки")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
"""

content = content[:start_idx] + new_tools_content + content[end_idx+1:]

with open('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/ToolsSettings.kt', 'w') as f:
    f.write(content)

print("ToolsSettings.kt updated successfully.")
