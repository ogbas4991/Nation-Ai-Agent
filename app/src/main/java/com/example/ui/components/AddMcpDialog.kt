package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.McpServerEntity
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AddMcpDialog(
    initialServer: McpServerEntity? = null,
    onSave: (name: String, transportType: String, urlOrCommand: String, headersJson: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var transportType by remember { mutableStateOf(initialServer?.transportType ?: "HTTP/SSE") }
    var urlOrCommand by remember { mutableStateOf(initialServer?.urlOrCommand ?: "") }
    var headersJson by remember { mutableStateOf(initialServer?.headersJson ?: "{}") }

    val transportOptions = listOf("HTTP/SSE", "Stdio", "WebSocket")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialServer == null) "Add MCP Server" else "Edit MCP Server",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                // Server Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g., Brave Search MCP") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_name_field"),
                    shape = RoundedCornerShape(10.dp),
                    colors = customTextFieldColors()
                )

                // Transport Type Selector
                Column {
                    Text(
                        text = "TRANSPORT TYPE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        transportOptions.forEach { type ->
                            val isSelected = transportType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) CyanPrimary else DarkSurfaceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { transportType = type }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) CyanPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }

                // URL or Command Field
                OutlinedTextField(
                    value = urlOrCommand,
                    onValueChange = { urlOrCommand = it },
                    label = { Text(if (transportType == "Stdio") "Command" else "Server URL") },
                    placeholder = {
                        Text(
                            if (transportType == "Stdio") "npx -y @modelcontextprotocol/server-xyz" else "https://mcp.example.com/sse"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_url_field"),
                    shape = RoundedCornerShape(10.dp),
                    colors = customTextFieldColors()
                )

                // Headers JSON Field
                OutlinedTextField(
                    value = headersJson,
                    onValueChange = { headersJson = it },
                    label = { Text("Headers (JSON)") },
                    placeholder = { Text("{\"Authorization\": \"Bearer token\"}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_headers_field"),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3,
                    colors = customTextFieldColors()
                )

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                    Button(
                        onClick = {
                            if (name.isNotBlank() && urlOrCommand.isNotBlank()) {
                                onSave(name, transportType, urlOrCommand, headersJson)
                            }
                        },
                        enabled = name.isNotBlank() && urlOrCommand.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("save_mcp_button")
                    ) {
                        Text("Save Server", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceHighlight,
    unfocusedContainerColor = DarkSurface,
    focusedBorderColor = CyanPrimary,
    unfocusedBorderColor = DarkSurfaceBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = CyanPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = CyanPrimary
)
