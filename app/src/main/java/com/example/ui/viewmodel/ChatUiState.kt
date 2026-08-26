package com.example.ui.viewmodel

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.network.model.ConnectionStatus
import com.example.network.model.RawFrameLog

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val sessions: List<ChatSessionEntity> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val config: GatewayConfigEntity = GatewayConfigEntity(),
    val mcpServers: List<McpServerEntity> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val activeToolCallId: String? = null,
    val currentStreamingMessageId: Long? = null,
    val showSettingsDrawer: Boolean = false,
    val showClearDialog: Boolean = false,
    val showAddMcpDialog: Boolean = false,
    val showSessionSwitcher: Boolean = false,
    val showMcpDiagnostic: Boolean = false,
    val showRawRpcDialog: Boolean = false,
    val rawRpcLogs: List<RawFrameLog> = emptyList(),
    val mcpDiagnosticResult: String? = null,
    val isTestingMcp: Boolean = false,
    val editingMcpServer: McpServerEntity? = null,
    val toastMessage: String? = null,
    val totalEstimatedTokens: Int = 0,
    val attachedImageUri: String? = null,
    val attachedImageBase64: String? = null,
    val speakingMessageId: Long? = null
)
