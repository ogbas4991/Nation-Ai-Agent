package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.McpRepository
import com.example.data.repository.SessionRepository
import com.example.data.repository.SettingsRepository
import com.example.network.OpenClawFrameCodec
import com.example.network.OpenClawWebSocketClient
import com.example.network.RealFileAndHttpEngine
import com.example.network.RealToolEngine
import com.example.network.model.ConnectionStatus
import com.example.network.model.InboundRpcFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val chatRepo = ChatRepository(db.chatMessageDao())
    private val settingsRepo = SettingsRepository(db.gatewayConfigDao())
    private val mcpRepo = McpRepository(db.mcpServerDao())
    private val sessionRepo = SessionRepository(db.chatSessionDao())

    val webSocketClient = OpenClawWebSocketClient(viewModelScope)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeStreamingMsgId: Long? = null
    private var streamingContentBuffer = StringBuilder()

    init {
        // 1. Observe Config
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.configFlow.collectLatest { config ->
                val nonNullConfig = config ?: GatewayConfigEntity()
                _uiState.update { it.copy(config = nonNullConfig) }
                webSocketClient.connect(nonNullConfig, _uiState.value.mcpServers)
            }
        }

        // 2. Observe MCP Servers
        viewModelScope.launch(Dispatchers.IO) {
            mcpRepo.allServers.collectLatest { servers ->
                _uiState.update { it.copy(mcpServers = servers) }
                webSocketClient.updateConfig(_uiState.value.config, servers)
            }
        }

        // 3. Observe Chat Sessions
        viewModelScope.launch(Dispatchers.IO) {
            sessionRepo.allSessions.collectLatest { sessionsList ->
                _uiState.update { it.copy(sessions = sessionsList) }
            }
        }

        // 4. Observe Messages for current session
        viewModelScope.launch(Dispatchers.IO) {
            chatRepo.getAllMessages().collectLatest { msgs ->
                val currentSessionMsgs = msgs.filter { it.sessionId == _uiState.value.config.activeSessionId }
                // Compute estimated token usage (approx 4 chars per token)
                val estimatedTokens = currentSessionMsgs.sumOf { (it.content.length / 4) + 5 }
                _uiState.update {
                    it.copy(
                        messages = currentSessionMsgs,
                        totalEstimatedTokens = estimatedTokens
                    )
                }
            }
        }

        // 5. Observe Connection Status
        viewModelScope.launch {
            webSocketClient.connectionStatus.collectLatest { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        // 6. Observe Raw Frame Logs
        viewModelScope.launch {
            webSocketClient.rawFrameLogs.collectLatest { logs ->
                _uiState.update { it.copy(rawRpcLogs = logs) }
            }
        }

        // 7. Observe Inbound RPC Frames
        viewModelScope.launch(Dispatchers.IO) {
            webSocketClient.incomingFrames.collect { frame ->
                handleInboundFrame(frame)
            }
        }
    }

    private suspend fun handleInboundFrame(frame: InboundRpcFrame) {
        when (frame) {
            is InboundRpcFrame.AuthSuccess -> {
                _uiState.update { it.copy(toastMessage = "Authenticated with OpenClaw Gateway") }
            }

            is InboundRpcFrame.TokenDelta -> {
                val msgId = activeStreamingMsgId
                if (msgId != null) {
                    streamingContentBuffer.append(frame.delta)
                    chatRepo.appendDelta(msgId, frame.delta)
                } else {
                    val newMsgId = chatRepo.createStreamingAssistantMessage(_uiState.value.config.activeSessionId)
                    activeStreamingMsgId = newMsgId
                    streamingContentBuffer.clear()
                    streamingContentBuffer.append(frame.delta)
                    chatRepo.appendDelta(newMsgId, frame.delta)
                }
            }

            is InboundRpcFrame.MessageComplete -> {
                val msgId = activeStreamingMsgId
                val finalContent = if (frame.fullContent.isNotBlank()) {
                    frame.fullContent
                } else {
                    streamingContentBuffer.toString()
                }

                if (msgId != null) {
                    chatRepo.completeAssistantMessage(msgId, finalContent)
                    activeStreamingMsgId = null
                    streamingContentBuffer.clear()
                }
                _uiState.update { it.copy(isSending = false) }
            }

            is InboundRpcFrame.ToolExecutionStart -> {
                val toolMsgId = chatRepo.saveToolCallMessage(
                    sessionId = _uiState.value.config.activeSessionId,
                    toolName = frame.toolName,
                    inputJson = frame.inputJson
                )
                _uiState.update { it.copy(activeToolCallId = frame.callId) }
            }

            is InboundRpcFrame.ToolExecutionResult -> {
                val lastToolMsg = _uiState.value.messages.lastOrNull { it.role == "TOOL" && it.toolName == frame.toolName }
                if (lastToolMsg != null) {
                    chatRepo.updateToolResult(
                        messageId = lastToolMsg.id,
                        toolName = frame.toolName,
                        output = frame.output,
                        isError = frame.isError
                    )
                }
                _uiState.update { it.copy(activeToolCallId = null) }
            }

            is InboundRpcFrame.SystemAlert -> {
                chatRepo.saveSystemAlert(
                    sessionId = _uiState.value.config.activeSessionId,
                    level = frame.level,
                    message = frame.message
                )
            }

            is InboundRpcFrame.Error -> {
                val msgId = activeStreamingMsgId
                if (msgId != null) {
                    chatRepo.markMessageError(msgId, frame.message)
                    activeStreamingMsgId = null
                } else {
                    chatRepo.saveSystemAlert(
                        sessionId = _uiState.value.config.activeSessionId,
                        level = "error",
                        message = "RPC Error [${frame.code}]: ${frame.message}"
                    )
                }
                _uiState.update { it.copy(isSending = false) }
            }

            is InboundRpcFrame.Pong -> {
                // Heartbeat pong received
            }

            is InboundRpcFrame.RawMessage -> {
                // Logged in raw frame stream
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setAttachedImage(uri: String?, base64: String?) {
        _uiState.update {
            it.copy(
                attachedImageUri = uri,
                attachedImageBase64 = base64
            )
        }
    }

    fun clearAttachedImage() {
        _uiState.update {
            it.copy(
                attachedImageUri = null,
                attachedImageBase64 = null
            )
        }
    }

    fun setSpeakingMessageId(id: Long?) {
        _uiState.update { it.copy(speakingMessageId = id) }
    }

    fun setShowRawRpcDialog(show: Boolean) {
        _uiState.update { it.copy(showRawRpcDialog = show) }
    }

    fun clearRawRpcLogs() {
        webSocketClient.clearRawLogs()
        _uiState.update { it.copy(rawRpcLogs = emptyList()) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedImageBase64 = _uiState.value.attachedImageBase64
        val attachedImageUri = _uiState.value.attachedImageUri

        if (text.isBlank() && attachedImageBase64 == null) return

        val displayText = if (attachedImageUri != null) {
            "[Image Attached]\n" + (if (text.isBlank()) "Analyze this image." else text)
        } else {
            text
        }

        val sessionId = _uiState.value.config.activeSessionId
        val currentConfig = _uiState.value.config

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(inputText = "", isSending = true) }
            clearAttachedImage()

            // 1. Save User Message
            chatRepo.saveUserMessage(sessionId, displayText)

            // Update Session title if this is the first user query
            val threadMessages = _uiState.value.messages
            if (threadMessages.size <= 1) {
                val previewTitle = (if (text.isNotBlank()) text else "Vision Analysis").take(30)
                sessionRepo.updateSessionTitle(sessionId, previewTitle)
            }

            // 2. Check if WebSocket gateway is connected
            if (webSocketClient.isConnected()) {
                val rpcSend = OpenClawFrameCodec.buildUserMessageFrame(
                    sessionId = sessionId,
                    content = displayText,
                    messageId = System.currentTimeMillis()
                )
                webSocketClient.sendFrame(rpcSend)
                return@launch
            }

            // 3. Autonomous Local / Direct Execution Engine
            val toolOutputs = mutableListOf<String>()

            // Web Search
            val isSearchQuery = currentConfig.toolWebSearchEnabled && (
                text.startsWith("search ", ignoreCase = true) ||
                text.startsWith("find ", ignoreCase = true) ||
                text.startsWith("lookup ", ignoreCase = true) ||
                text.contains("news", ignoreCase = true) ||
                text.contains("weather", ignoreCase = true)
            )

            if (isSearchQuery) {
                val query = text.removePrefix("search ").removePrefix("find ").removePrefix("lookup ").trim()
                val toolCallId = chatRepo.saveToolCallMessage(
                    sessionId = sessionId,
                    toolName = "web_search",
                    inputJson = "{\"query\": \"$query\"}"
                )
                val searchResult = RealToolEngine.executeWebSearch(query)
                chatRepo.updateToolResult(
                    messageId = toolCallId,
                    toolName = "web_search",
                    output = searchResult,
                    isError = false
                )
                toolOutputs.add("Web Search Result:\n$searchResult")
            }

            // Code/Math Runner
            val isCodeOrMath = currentConfig.toolCodeRunnerEnabled && (
                text.startsWith("calculate", ignoreCase = true) ||
                text.startsWith("eval", ignoreCase = true) ||
                text.startsWith("run", ignoreCase = true) ||
                text.contains("```") ||
                text.matches(Regex("^[0-9\\.\\+\\-\\*/\\^\\(\\)\\s%]+$"))
            )

            if (isCodeOrMath) {
                val code = text.removePrefix("calculate").removePrefix("eval").removePrefix("run").removePrefix(":").trim()
                val toolCallId = chatRepo.saveToolCallMessage(
                    sessionId = sessionId,
                    toolName = "code_runner",
                    inputJson = "{\"code\": \"$code\", \"language\": \"kotlin\"}"
                )
                val runResult = RealToolEngine.executeCodeRunner(code, "kotlin")
                chatRepo.updateToolResult(
                    messageId = toolCallId,
                    toolName = "code_runner",
                    output = runResult,
                    isError = false
                )
                toolOutputs.add("Code Execution Result:\n$runResult")
            }

            // HTTP Request Tool
            val isHttpQuery = text.startsWith("http get", ignoreCase = true) ||
                text.startsWith("curl", ignoreCase = true) ||
                text.startsWith("fetch", ignoreCase = true)

            if (isHttpQuery) {
                val targetUrl = text.removePrefix("http get").removePrefix("curl").removePrefix("fetch").trim()
                val toolCallId = chatRepo.saveToolCallMessage(
                    sessionId = sessionId,
                    toolName = "http_client",
                    inputJson = "{\"url\": \"$targetUrl\", \"method\": \"GET\"}"
                )
                val httpResult = RealFileAndHttpEngine.executeHttpRequest(targetUrl)
                chatRepo.updateToolResult(
                    messageId = toolCallId,
                    toolName = "http_client",
                    output = httpResult,
                    isError = false
                )
                toolOutputs.add("HTTP Response:\n$httpResult")
            }

            // Workspace File System
            val isWorkspaceQuery = currentConfig.toolFileManagerEnabled && (
                text.startsWith("file list", ignoreCase = true) ||
                text.startsWith("workspace", ignoreCase = true) ||
                text.startsWith("cat file", ignoreCase = true)
            )

            if (isWorkspaceQuery) {
                val toolCallId = chatRepo.saveToolCallMessage(
                    sessionId = sessionId,
                    toolName = "workspace_fs",
                    inputJson = "{\"operation\": \"list\"}"
                )
                val fsResult = RealFileAndHttpEngine.executeWorkspaceFileOp(
                    context = getApplication(),
                    operation = "list",
                    filename = ""
                )
                chatRepo.updateToolResult(
                    messageId = toolCallId,
                    toolName = "workspace_fs",
                    output = fsResult,
                    isError = false
                )
                toolOutputs.add("Workspace Files:\n$fsResult")
            }

            // System Telemetry
            val isTelemetryQuery = text.contains("telemetry", ignoreCase = true) ||
                text.contains("system info", ignoreCase = true) ||
                text.contains("specs", ignoreCase = true)

            if (isTelemetryQuery) {
                val telemetry = RealToolEngine.getSystemTelemetry()
                val toolCallId = chatRepo.saveToolCallMessage(
                    sessionId = sessionId,
                    toolName = "system_telemetry",
                    inputJson = "{}"
                )
                chatRepo.updateToolResult(
                    messageId = toolCallId,
                    toolName = "system_telemetry",
                    output = telemetry,
                    isError = false
                )
                toolOutputs.add(telemetry)
            }

            // 4. Generate streaming response
            val assistantMsgId = chatRepo.createStreamingAssistantMessage(sessionId)
            activeStreamingMsgId = assistantMsgId
            streamingContentBuffer.clear()

            val apiKey = com.example.network.GeminiDirectClient.resolveApiKey(currentConfig.authToken)

            if (apiKey.isNotBlank()) {
                val history = _uiState.value.messages.map { Pair(it.role, it.content) }
                val systemPrompt = buildString {
                    append("You are OPA AI Agent, an autonomous high-performance AI engineer with real-time tool execution capabilities and multimodal image reasoning.")
                    if (toolOutputs.isNotEmpty()) {
                        append("\n\nReal Tool Execution Context:\n")
                        toolOutputs.forEach { append(it).append("\n\n") }
                    }
                }

                val geminiResult = com.example.network.GeminiDirectClient.streamGenerate(
                    modelName = currentConfig.primaryModel,
                    apiKey = apiKey,
                    systemInstruction = systemPrompt,
                    messagesHistory = history,
                    imageBase64 = attachedImageBase64,
                    imageMimeType = "image/jpeg",
                    onTokenDelta = { delta ->
                        viewModelScope.launch(Dispatchers.IO) {
                            streamingContentBuffer.append(delta)
                            chatRepo.appendDelta(assistantMsgId, delta)
                        }
                    }
                )

                geminiResult.fold(
                    onSuccess = { fullContent ->
                        chatRepo.completeAssistantMessage(assistantMsgId, fullContent)
                        activeStreamingMsgId = null
                        streamingContentBuffer.clear()
                        _uiState.update { it.copy(isSending = false) }
                    },
                    onFailure = { error ->
                        val fallbackContent = buildString {
                            append("Generated response based on verified live inputs:\n\n")
                            if (toolOutputs.isNotEmpty()) {
                                toolOutputs.forEach { append(it).append("\n\n") }
                            }
                            append("Query: \"$text\"\n")
                            append("Active Model: ${currentConfig.primaryModel}\n")
                            append("Note: (${error.message ?: "Direct API fallback active"})")
                        }
                        chatRepo.completeAssistantMessage(assistantMsgId, fallbackContent)
                        activeStreamingMsgId = null
                        streamingContentBuffer.clear()
                        _uiState.update { it.copy(isSending = false) }
                    }
                )
            } else {
                val tokens = mutableListOf<String>()
                if (toolOutputs.isNotEmpty()) {
                    tokens.add("### Tool Execution Output\n\n")
                    toolOutputs.forEach { tokens.add("$it\n\n") }
                    tokens.add("The requested operation completed successfully using the active tool engine.")
                } else if (attachedImageBase64 != null) {
                    tokens.add("### Multimodal Image Analysis\n\n")
                    tokens.add("Received image attachment. To enable live multimodal visual analysis with Gemini 3.5 Flash or Pro, ensure `GEMINI_API_KEY` is provided in the **Settings** panel.")
                } else {
                    tokens.add("I have processed your query: **\"$text\"**.\n\n")
                    tokens.add("• **Active Model**: `${currentConfig.primaryModel}`\n")
                    tokens.add("• **Provider**: `${currentConfig.llmProvider}`\n")
                    tokens.add("• **MCP Servers**: ${_uiState.value.mcpServers.count { it.isEnabled }} active\n\n")
                    tokens.add("You can enable **Web Search**, **Code Runner**, **HTTP Client**, or configure a live remote OpenClaw Gateway or Gemini API Key in the **Settings** drawer anytime.")
                }

                for (token in tokens) {
                    delay(80)
                    streamingContentBuffer.append(token)
                    chatRepo.appendDelta(assistantMsgId, token)
                }

                chatRepo.completeAssistantMessage(assistantMsgId, streamingContentBuffer.toString())
                activeStreamingMsgId = null
                streamingContentBuffer.clear()
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun switchSession(newSessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedConfig = _uiState.value.config.copy(activeSessionId = newSessionId)
            settingsRepo.saveConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig, toastMessage = "Switched to session") }
            // Re-fetch messages
            chatRepo.getMessagesForSession(newSessionId).collectLatest { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val newSessionId = sessionRepo.createNewSession(
                title = "New Thread",
                model = _uiState.value.config.primaryModel
            )
            switchSession(newSessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionRepo.deleteSession(sessionId)
            chatRepo.clearSessionMessages(sessionId)
            val remaining = _uiState.value.sessions.filter { it.sessionId != sessionId }
            if (remaining.isNotEmpty()) {
                switchSession(remaining.first().sessionId)
            } else {
                createNewSession()
            }
        }
    }

    fun getConversationMarkdown(): String {
        val msgs = _uiState.value.messages
        return buildString {
            append("# OPA AI Agent Conversation Export\n\n")
            append("• **Session ID**: ${_uiState.value.config.activeSessionId}\n")
            append("• **Model**: ${_uiState.value.config.primaryModel}\n")
            append("• **Total Messages**: ${msgs.size}\n\n---\n\n")
            msgs.forEach { m ->
                val roleName = when (m.role) {
                    "USER" -> "### 👤 User"
                    "ASSISTANT" -> "### 🤖 OPA Agent"
                    "TOOL" -> "### 🛠️ Tool Execution (${m.toolName})"
                    else -> "### ℹ️ System"
                }
                append("$roleName\n\n")
                if (m.role == "TOOL") {
                    append("```json\nInput: ${m.toolInput ?: "{}"}\nOutput: ${m.toolOutput ?: ""}\n```\n\n")
                } else {
                    append("${m.content}\n\n")
                }
            }
        }
    }

    fun testMcpServer(urlOrCommand: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTestingMcp = true, mcpDiagnosticResult = null) }
            val res = RealToolEngine.pingMcpServer(urlOrCommand)
            _uiState.update { it.copy(isTestingMcp = false, mcpDiagnosticResult = res) }
        }
    }

    fun reconnectGateway() {
        webSocketClient.reconnectNow()
        _uiState.update { it.copy(toastMessage = "Initiating gateway reconnection...") }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepo.clearSessionMessages(_uiState.value.config.activeSessionId)
            _uiState.update { it.copy(showClearDialog = false, toastMessage = "Chat cleared") }
        }
    }

    fun saveConfig(
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newConfig = _uiState.value.config.copy(
                serverUrl = serverUrl.trim(),
                authToken = authToken.trim(),
                llmProvider = llmProvider.trim(),
                primaryModel = primaryModel.trim(),
                baseApiUrl = baseApiUrl.trim(),
                toolWebSearchEnabled = webSearchEnabled,
                toolCodeRunnerEnabled = codeRunnerEnabled,
                toolFileManagerEnabled = fileManagerEnabled,
                toolBashTerminalEnabled = bashTerminalEnabled,
                executionPolicy = executionPolicy
            )
            settingsRepo.saveConfig(newConfig)
            _uiState.update { it.copy(config = newConfig, toastMessage = "Settings saved") }
            webSocketClient.connect(newConfig, _uiState.value.mcpServers)
        }
    }

    fun addMcpServer(name: String, transportType: String, urlOrCommand: String, headersJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = McpServerEntity(
                name = name.trim(),
                transportType = transportType,
                urlOrCommand = urlOrCommand.trim(),
                headersJson = headersJson.trim().ifBlank { "{}" },
                isEnabled = true
            )
            mcpRepo.addServer(server)
            _uiState.update { it.copy(showAddMcpDialog = false, toastMessage = "MCP server added") }
        }
    }

    fun updateMcpServer(id: Long, name: String, transportType: String, urlOrCommand: String, headersJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = McpServerEntity(
                id = id,
                name = name.trim(),
                transportType = transportType,
                urlOrCommand = urlOrCommand.trim(),
                headersJson = headersJson.trim().ifBlank { "{}" },
                isEnabled = true
            )
            mcpRepo.updateServer(server)
            _uiState.update { it.copy(showAddMcpDialog = false, editingMcpServer = null, toastMessage = "MCP server updated") }
        }
    }

    fun toggleMcpServer(id: Long, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            mcpRepo.toggleServer(id, isEnabled)
        }
    }

    fun deleteMcpServer(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            mcpRepo.deleteServer(id)
            _uiState.update { it.copy(toastMessage = "MCP server removed") }
        }
    }

    fun setShowSettingsDrawer(show: Boolean) {
        _uiState.update { it.copy(showSettingsDrawer = show) }
    }

    fun setShowClearDialog(show: Boolean) {
        _uiState.update { it.copy(showClearDialog = show) }
    }

    fun setShowSessionSwitcher(show: Boolean) {
        _uiState.update { it.copy(showSessionSwitcher = show) }
    }

    fun setShowMcpDiagnostic(show: Boolean) {
        _uiState.update { it.copy(showMcpDiagnostic = show, mcpDiagnosticResult = null) }
    }

    fun setShowAddMcpDialog(show: Boolean, serverToEdit: McpServerEntity? = null) {
        _uiState.update { it.copy(showAddMcpDialog = show, editingMcpServer = serverToEdit) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
