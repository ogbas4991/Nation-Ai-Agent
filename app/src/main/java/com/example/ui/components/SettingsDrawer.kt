package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleContainer
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerContent(
    config: GatewayConfigEntity,
    mcpServers: List<McpServerEntity>,
    onSaveConfig: (
        serverUrl: String,
        authToken: String,
        llmProvider: String,
        primaryModel: String,
        baseApiUrl: String,
        webSearchEnabled: Boolean,
        codeRunnerEnabled: Boolean,
        fileManagerEnabled: Boolean,
        bashTerminalEnabled: Boolean,
        executionPolicy: String
    ) -> Unit,
    onToggleMcpServer: (id: Long, enabled: Boolean) -> Unit,
    onDeleteMcpServer: (id: Long) -> Unit,
    onOpenAddMcpDialog: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember(config) { mutableStateOf(config.serverUrl) }
    var authToken by remember(config) { mutableStateOf(config.authToken) }
    var llmProvider by remember(config) { mutableStateOf(config.llmProvider) }
    var primaryModel by remember(config) { mutableStateOf(config.primaryModel) }
    var baseApiUrl by remember(config) { mutableStateOf(config.baseApiUrl) }

    var webSearchEnabled by remember(config) { mutableStateOf(config.toolWebSearchEnabled) }
    var codeRunnerEnabled by remember(config) { mutableStateOf(config.toolCodeRunnerEnabled) }
    var fileManagerEnabled by remember(config) { mutableStateOf(config.toolFileManagerEnabled) }
    var bashTerminalEnabled by remember(config) { mutableStateOf(config.toolBashTerminalEnabled) }
    var executionPolicy by remember(config) { mutableStateOf(config.executionPolicy) }

    var providerMenuExpanded by remember { mutableStateOf(false) }

    val providers = listOf("Google Gemini", "Anthropic", "OpenAI", "Ollama", "OpenRouter", "Custom")

    val popularModels = mapOf(
        "Google Gemini" to listOf("google/gemini-3.5-flash", "google/gemini-3.1-pro-preview", "google/gemini-3.1-flash-lite-preview"),
        "Anthropic" to listOf("anthropic/claude-3-5-sonnet-20241022", "anthropic/claude-3-5-haiku-20241022", "anthropic/claude-3-opus-20240229"),
        "OpenAI" to listOf("openai/gpt-4o", "openai/gpt-4o-mini", "openai/o1-preview", "openai/o3-mini"),
        "Ollama" to listOf("ollama/llama3.3:latest", "ollama/deepseek-r1:70b", "ollama/qwen2.5-coder:32b"),
        "OpenRouter" to listOf("openrouter/auto", "openrouter/anthropic/claude-3.5-sonnet", "openrouter/deepseek/deepseek-r1")
    )

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp),
        drawerContainerColor = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Drawer Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyanContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "Settings & Gateway",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.testTag("close_settings_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            // SECTION 1: Gateway Settings
            SettingsSectionHeader(
                title = "GATEWAY CONFIGURATION",
                icon = Icons.Default.Lan
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("OpenClaw WebSocket URL") },
                placeholder = { Text("wss://gateway.internal:18789") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gateway_url_input"),
                shape = RoundedCornerShape(10.dp),
                colors = customTextFieldColors()
            )

            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it },
                label = { Text("Auth Bearer Token") },
                placeholder = { Text("Enter OpenClaw API or JWT token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gateway_token_input"),
                shape = RoundedCornerShape(10.dp),
                colors = customTextFieldColors()
            )

            // SECTION 2: Provider & Model Selection
            SettingsSectionHeader(
                title = "LLM PROVIDER & MODEL",
                icon = Icons.Default.SmartToy
            )

            // Provider Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = llmProvider,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Provider") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Provider",
                            tint = CyanPrimary,
                            modifier = Modifier.clickable { providerMenuExpanded = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { providerMenuExpanded = true }
                        .testTag("provider_dropdown"),
                    shape = RoundedCornerShape(10.dp),
                    colors = customTextFieldColors()
                )

                DropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider, color = TextPrimary) },
                            onClick = {
                                llmProvider = provider
                                providerMenuExpanded = false
                                // Auto set first model if available
                                popularModels[provider]?.firstOrNull()?.let {
                                    primaryModel = it
                                }
                            }
                        )
                    }
                }
            }

            // Primary Model Input & Quick Suggestions
            OutlinedTextField(
                value = primaryModel,
                onValueChange = { primaryModel = it },
                label = { Text("Primary Model") },
                placeholder = { Text("e.g., anthropic/claude-3-5-sonnet-20241022") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("primary_model_input"),
                shape = RoundedCornerShape(10.dp),
                colors = customTextFieldColors()
            )

            // Quick Model Chips
            val modelSuggestions = popularModels[llmProvider] ?: emptyList()
            if (modelSuggestions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelSuggestions.forEach { modelName ->
                        val isSelected = primaryModel == modelName
                        FilterChip(
                            selected = isSelected,
                            onClick = { primaryModel = modelName },
                            label = {
                                Text(
                                    text = modelName.substringAfter("/"),
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (isSelected) CyanContainer else DarkSurfaceElevated,
                                labelColor = if (isSelected) CyanPrimary else TextSecondary,
                                selectedContainerColor = CyanContainer,
                                selectedLabelColor = CyanPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) CyanPrimary else DarkSurfaceBorder,
                                enabled = true,
                                selected = isSelected
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = baseApiUrl,
                onValueChange = { baseApiUrl = it },
                label = { Text("Custom Base API URL (Optional)") },
                placeholder = { Text("http://localhost:11434/v1") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_api_url_input"),
                shape = RoundedCornerShape(10.dp),
                colors = customTextFieldColors()
            )

            // SECTION 3: MCP Connectors Manager
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsSectionHeader(
                    title = "MCP CONNECTORS (${mcpServers.size})",
                    icon = Icons.Default.Power
                )

                IconButton(
                    onClick = onOpenAddMcpDialog,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("add_mcp_server_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add MCP",
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (mcpServers.isEmpty()) {
                Text(
                    text = "No MCP servers configured. Tap '+' to connect MCP tools.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mcpServers.forEach { server ->
                        McpServerCardItem(
                            server = server,
                            onToggle = { enabled -> onToggleMcpServer(server.id, enabled) },
                            onDelete = { onDeleteMcpServer(server.id) }
                        )
                    }
                }
            }

            // SECTION 4: Tool Policies
            SettingsSectionHeader(
                title = "TOOL POLICIES",
                icon = Icons.Default.Security
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolToggleRow(
                        title = "Web Search",
                        icon = Icons.Default.Language,
                        checked = webSearchEnabled,
                        onCheckedChange = { webSearchEnabled = it }
                    )
                    ToolToggleRow(
                        title = "Code Runner",
                        icon = Icons.Default.Code,
                        checked = codeRunnerEnabled,
                        onCheckedChange = { codeRunnerEnabled = it }
                    )
                    ToolToggleRow(
                        title = "File Manager",
                        icon = Icons.Default.Folder,
                        checked = fileManagerEnabled,
                        onCheckedChange = { fileManagerEnabled = it }
                    )
                    ToolToggleRow(
                        title = "Bash Terminal",
                        icon = Icons.Default.Terminal,
                        checked = bashTerminalEnabled,
                        onCheckedChange = { bashTerminalEnabled = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "EXECUTION POLICY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )

                    val policies = listOf("Auto-Approve", "Ask Before Destructive", "Read-Only")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        policies.forEach { policy ->
                            val isSelected = executionPolicy == policy
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PurpleContainer else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) PurpleAccent else DarkSurfaceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { executionPolicy = policy }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = policy,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Save & Apply Button
            Button(
                onClick = {
                    onSaveConfig(
                        serverUrl,
                        authToken,
                        llmProvider,
                        primaryModel,
                        baseApiUrl,
                        webSearchEnabled,
                        codeRunnerEnabled,
                        fileManagerEnabled,
                        bashTerminalEnabled,
                        executionPolicy
                    )
                    onCloseDrawer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_settings_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save & Apply Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
    }
}

@Composable
private fun ToolToggleRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) CyanPrimary else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                color = if (checked) TextPrimary else TextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = CyanPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurface
            )
        )
    }
}

@Composable
private fun McpServerCardItem(
    server: McpServerEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = server.transportType,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                }

                Text(
                    text = server.urlOrCommand,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = server.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = CyanPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurface
                    )
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete MCP",
                        tint = StatusDisconnected.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceHighlight,
    unfocusedContainerColor = DarkSurfaceElevated,
    focusedBorderColor = CyanPrimary,
    unfocusedBorderColor = DarkSurfaceBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = CyanPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = CyanPrimary
)
