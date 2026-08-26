package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.network.model.ConnectionStatus
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSubHeader
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopBar(
    connectionStatus: ConnectionStatus,
    config: GatewayConfigEntity,
    mcpServers: List<McpServerEntity>,
    estimatedTokens: Int,
    onOpenSettings: () -> Unit,
    onOpenSessionSwitcher: () -> Unit,
    onOpenMcpDiagnostic: () -> Unit,
    onOpenRawRpcDialog: () -> Unit = {},
    onExportConversation: () -> Unit,
    onReconnect: () -> Unit,
    onClearThreadRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        // Main Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .border(width = 1.dp, color = DarkBorderSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title + Connected status + Session switcher trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onOpenSessionSwitcher,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("session_switcher_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Chat Threads",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "OPA AI Agent",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = TextPrimary
                    )

                    // Glowing Status Badge
                    StatusBadge(
                        status = connectionStatus,
                        onBadgeClick = onReconnect
                    )
                }
            }

            // Right: Action buttons & Diagnostics
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onOpenRawRpcDialog,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("raw_rpc_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = "Live JSON-RPC Inspector",
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onOpenMcpDiagnostic,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("mcp_diagnostic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "MCP Diagnostics",
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onExportConversation,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("export_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Chat",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onClearThreadRequested,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("clear_thread_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Thread",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                        .testTag("settings_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Sub-Header Ribbon: Model, Active MCPs, Token count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSubHeader)
                .border(width = 1.dp, color = DarkBorderSubtle)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Model:",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = config.primaryModel.substringAfter("/"),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = CyanPrimary
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color(0x1AFFFFFF))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MCP:",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                val activeCount = mcpServers.count { it.isEnabled }
                Text(
                    text = "$activeCount Active",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (activeCount > 0) StatusGreen else TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color(0x1AFFFFFF))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Est. Tokens:",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = "$estimatedTokens",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: ConnectionStatus,
    onBadgeClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (badgeText, badgeColor, isPulsing) = when (status) {
        is ConnectionStatus.Connected -> Triple("CONNECTED", CyanPrimary, false)
        is ConnectionStatus.Connecting -> Triple("CONNECTING...", StatusConnecting, true)
        is ConnectionStatus.Reconnecting -> Triple("RETRYING (${status.nextRetryInSeconds}s)", StatusConnecting, true)
        is ConnectionStatus.Disconnected -> Triple("DIRECT AI ENGINE", StatusGreen, false)
        is ConnectionStatus.Error -> Triple("ERROR", StatusDisconnected, false)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable { onBadgeClick() }
            .testTag("connection_status_badge")
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .alpha(if (isPulsing) pulseAlpha else 1f)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = badgeColor)
        )

        Text(
            text = badgeText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = badgeColor
        )
    }
}

@Composable
fun ClearThreadConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = {
            Text(
                text = "Clear Thread?",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "This will delete all saved conversation messages for the current session from your local Room database.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusDisconnected,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_clear_thread_button")
            ) {
                Text("Clear All")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
