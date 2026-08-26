package com.example.network

import android.util.Log
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.network.model.ConnectionStatus
import com.example.network.model.InboundRpcFrame
import com.example.network.model.RawFrameLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class OpenClawWebSocketClient(
    private val scope: CoroutineScope
) {
    private val tag = "OpenClawWebSocket"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null

    private var currentConfig: GatewayConfigEntity? = null
    private var currentMcpServers: List<McpServerEntity> = emptyList()

    private var reconnectAttempt = 0
    private var shouldAutoReconnect = true

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun isConnected(): Boolean = _connectionStatus.value is ConnectionStatus.Connected

    private val _incomingFrames = MutableSharedFlow<InboundRpcFrame>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<InboundRpcFrame> = _incomingFrames.asSharedFlow()

    private val _rawFrameLogs = MutableStateFlow<List<RawFrameLog>>(emptyList())
    val rawFrameLogs: StateFlow<List<RawFrameLog>> = _rawFrameLogs.asStateFlow()

    private fun logRawFrame(direction: String, payload: String) {
        val summary = when {
            payload.contains("\"method\":\"chat.send\"") -> "chat.send"
            payload.contains("\"method\":\"auth.handshake\"") -> "auth.handshake"
            payload.contains("\"method\":\"session.init\"") -> "session.init"
            payload.contains("\"method\":\"ping\"") -> "ping"
            payload.contains("\"method\":\"token.delta\"") || payload.contains("\"token\"") -> "token.delta"
            payload.contains("\"method\":\"tool.execute\"") -> "tool.execute"
            payload.contains("\"method\":\"tool.result\"") -> "tool.result"
            payload.contains("\"result\":") -> "rpc.result"
            payload.contains("\"error\":") -> "rpc.error"
            else -> if (payload.length > 20) payload.take(20) + "..." else payload
        }
        val logItem = RawFrameLog(
            direction = direction,
            summary = summary,
            payload = payload
        )
        _rawFrameLogs.update { current ->
            (listOf(logItem) + current).take(60)
        }
    }

    fun clearRawLogs() {
        _rawFrameLogs.value = emptyList()
    }

    private fun isPlaceholderOrEmptyUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val clean = url.trim().lowercase()
        return clean.contains("your-openclaw-server.com") ||
                clean.contains("example.com") ||
                clean.contains("placeholder")
    }

    /**
     * Connect to the gateway using specified config and MCP server list
     */
    fun connect(config: GatewayConfigEntity, mcpServers: List<McpServerEntity>) {
        currentConfig = config
        currentMcpServers = mcpServers
        shouldAutoReconnect = true
        reconnectAttempt = 0
        cancelReconnect()

        if (isPlaceholderOrEmptyUrl(config.serverUrl)) {
            Log.d(tag, "Gateway server URL is unconfigured or placeholder. Operating in idle/ready state.")
            _connectionStatus.value = ConnectionStatus.Disconnected
            return
        }

        executeConnect(config.serverUrl, config.authToken)
    }

    private fun executeConnect(url: String, authToken: String) {
        if (isPlaceholderOrEmptyUrl(url)) {
            _connectionStatus.value = ConnectionStatus.Disconnected
            return
        }

        // Clean URL
        val formattedUrl = when {
            url.startsWith("ws://") || url.startsWith("wss://") -> url
            url.startsWith("http://") -> "ws://${url.removePrefix("http://")}"
            url.startsWith("https://") -> "wss://${url.removePrefix("https://")}"
            else -> "wss://$url"
        }

        _connectionStatus.value = ConnectionStatus.Connecting
        Log.d(tag, "Connecting to OpenClaw Gateway at: $formattedUrl")

        try {
            webSocket?.close(1000, "Reconnecting")
            webSocket = null

            val requestBuilder = Request.Builder()
                .url(formattedUrl)
                .addHeader("User-Agent", "OPA-AI-Agent-Android/1.0")

            if (authToken.isNotBlank()) {
                val tokenHeader = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
                requestBuilder.addHeader("Authorization", tokenHeader)
            }

            val request = requestBuilder.build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(tag, "WebSocket connection established!")
                    reconnectAttempt = 0
                    _connectionStatus.value = ConnectionStatus.Connected(formattedUrl)

                    // 1. Send Handshake / Auth Frame
                    currentConfig?.let { cfg ->
                        if (cfg.authToken.isNotBlank()) {
                            val authPayload = OpenClawFrameCodec.buildAuthFrame(cfg.authToken)
                            sendFrame(authPayload)
                        }

                        // 2. Send Session Initialization Frame
                        val sessionInitPayload = OpenClawFrameCodec.buildSessionInitFrame(cfg, currentMcpServers)
                        sendFrame(sessionInitPayload)
                    }

                    // Start periodic keep-alive
                    startKeepAlive()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    Log.d(tag, "Inbound Frame: $text")
                    logRawFrame("INBOUND", text)
                    val frame = OpenClawFrameCodec.parseIncomingFrame(text)
                    scope.launch {
                        _incomingFrames.emit(frame)
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.w(tag, "WebSocket closing code=$code reason=$reason")
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.w(tag, "WebSocket closed code=$code reason=$reason")
                    stopKeepAlive()
                    if (shouldAutoReconnect && code != 1000) {
                        scheduleReconnect("Connection closed: $reason")
                    } else {
                        _connectionStatus.value = ConnectionStatus.Disconnected
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    val isDnsFailure = t is java.net.UnknownHostException ||
                            (t.message?.contains("Unable to resolve host", ignoreCase = true) == true)
                    
                    if (isDnsFailure) {
                        Log.w(tag, "Gateway host unreachable: ${t.message}")
                        stopKeepAlive()
                        _connectionStatus.value = ConnectionStatus.Error("Unable to resolve host: $formattedUrl")
                        shouldAutoReconnect = false
                    } else {
                        Log.e(tag, "WebSocket failure: ${t.message}")
                        stopKeepAlive()
                        if (shouldAutoReconnect) {
                            scheduleReconnect(t.message ?: "Connection failure")
                        } else {
                            _connectionStatus.value = ConnectionStatus.Error(t.message ?: "Connection failure")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Failed to create WebSocket connection", e)
            if (shouldAutoReconnect && !isPlaceholderOrEmptyUrl(url)) {
                scheduleReconnect(e.message ?: "Connection creation failed")
            } else {
                _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Failed to connect")
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        cancelReconnect()
        if (reconnectAttempt >= 5) {
            Log.w(tag, "Maximum reconnection attempts reached (5). Halting auto-retry.")
            _connectionStatus.value = ConnectionStatus.Error("Gateway connection failed. Check URL & network in Settings.")
            return
        }

        reconnectAttempt++
        val baseSec = min(16, 2.0.pow((reconnectAttempt - 1).coerceAtLeast(0)).toInt())

        reconnectJob = scope.launch(Dispatchers.IO) {
            for (remaining in baseSec downTo 1) {
                if (!isActive || !shouldAutoReconnect) return@launch
                _connectionStatus.value = ConnectionStatus.Reconnecting(
                    attempt = reconnectAttempt,
                    nextRetryInSeconds = remaining,
                    reason = reason
                )
                delay(1000)
            }

            if (isActive && shouldAutoReconnect) {
                currentConfig?.let { cfg ->
                    if (!isPlaceholderOrEmptyUrl(cfg.serverUrl)) {
                        executeConnect(cfg.serverUrl, cfg.authToken)
                    } else {
                        _connectionStatus.value = ConnectionStatus.Disconnected
                    }
                }
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun startKeepAlive() {
        stopKeepAlive()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20000)
                if (isActive && _connectionStatus.value is ConnectionStatus.Connected) {
                    sendFrame(OpenClawFrameCodec.buildPingFrame())
                }
            }
        }
    }

    private fun stopKeepAlive() {
        pingJob?.cancel()
        pingJob = null
    }

    /**
     * Send raw frame string over WebSocket
     */
    fun sendFrame(payload: String): Boolean {
        val ws = webSocket
        if (ws != null && _connectionStatus.value is ConnectionStatus.Connected) {
            Log.d(tag, "Outbound Frame: $payload")
            logRawFrame("OUTBOUND", payload)
            return ws.send(payload)
        }
        Log.w(tag, "Cannot send frame; WebSocket not connected")
        return false
    }

    /**
     * Manual disconnect
     */
    fun disconnect() {
        shouldAutoReconnect = false
        cancelReconnect()
        stopKeepAlive()
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error closing websocket", e)
        }
        webSocket = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    /**
     * Quick manual reconnect trigger
     */
    fun reconnectNow() {
        shouldAutoReconnect = true
        reconnectAttempt = 0
        cancelReconnect()
        currentConfig?.let { cfg ->
            executeConnect(cfg.serverUrl, cfg.authToken)
        }
    }

    fun updateConfig(config: GatewayConfigEntity, mcpServers: List<McpServerEntity>) {
        currentConfig = config
        currentMcpServers = mcpServers
        if (_connectionStatus.value is ConnectionStatus.Connected) {
            val sessionInitPayload = OpenClawFrameCodec.buildSessionInitFrame(config, mcpServers)
            sendFrame(sessionInitPayload)
        }
    }
}
