package com.example.network.model

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val serverUrl: String, val timestamp: Long = System.currentTimeMillis()) : ConnectionStatus()
    data class Reconnecting(val attempt: Int, val nextRetryInSeconds: Int, val reason: String? = null) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

sealed class InboundRpcFrame {
    data class AuthSuccess(val client: String?, val sessionId: String?) : InboundRpcFrame()
    data class TokenDelta(val delta: String, val messageId: String?, val sessionId: String?) : InboundRpcFrame()
    data class MessageComplete(val fullContent: String, val messageId: String?, val sessionId: String?) : InboundRpcFrame()
    data class ToolExecutionStart(val callId: String, val toolName: String, val inputJson: String) : InboundRpcFrame()
    data class ToolExecutionResult(val callId: String, val toolName: String, val output: String, val isError: Boolean = false) : InboundRpcFrame()
    data class SystemAlert(val level: String, val message: String) : InboundRpcFrame()
    data class Error(val code: Int, val message: String) : InboundRpcFrame()
    data class Pong(val timestamp: Long) : InboundRpcFrame()
    data class RawMessage(val rawJson: String) : InboundRpcFrame()
}

data class McpServerConfig(
    val name: String,
    val transportType: String,
    val urlOrCommand: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)
