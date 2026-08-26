package com.example.network

import android.util.Log
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import com.example.network.model.InboundRpcFrame
import org.json.JSONArray
import org.json.JSONObject

object OpenClawFrameCodec {
    private const val TAG = "OpenClawFrameCodec"

    /**
     * Builds authentication handshake payload
     */
    fun buildAuthFrame(token: String): String {
        val json = JSONObject()
        json.put("type", "auth")
        json.put("token", token)
        json.put("client", "opa-android/1.0")
        json.put("protocol", 1)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    /**
     * Builds session initialization payload with provider, model, MCP servers, and tool policies
     */
    fun buildSessionInitFrame(
        config: GatewayConfigEntity,
        mcpServers: List<McpServerEntity>
    ): String {
        val json = JSONObject()
        json.put("type", "session.init")
        json.put("sessionId", config.activeSessionId)
        json.put("provider", config.llmProvider.lowercase())
        json.put("model", config.primaryModel)
        if (config.baseApiUrl.isNotBlank()) {
            json.put("baseApiUrl", config.baseApiUrl)
        }

        // Tool policies
        val toolsArray = JSONArray()
        if (config.toolWebSearchEnabled) toolsArray.put("web-search")
        if (config.toolCodeRunnerEnabled) toolsArray.put("code-runner")
        if (config.toolFileManagerEnabled) toolsArray.put("file-manager")
        if (config.toolBashTerminalEnabled) toolsArray.put("bash-terminal")
        json.put("tools", toolsArray)
        json.put("executionPolicy", config.executionPolicy)

        // Active MCP Servers
        val mcpArray = JSONArray()
        mcpServers.filter { it.isEnabled }.forEach { server ->
            val serverObj = JSONObject()
            serverObj.put("name", server.name)
            serverObj.put("transport", server.transportType)
            serverObj.put("urlOrCommand", server.urlOrCommand)
            serverObj.put("headers", JSONObject(server.headersJson.ifBlank { "{}" }))
            mcpArray.put(serverObj)
        }
        json.put("mcpServers", mcpArray)

        return json.toString()
    }

    /**
     * Builds user message frame
     */
    fun buildUserMessageFrame(
        sessionId: String,
        content: String,
        messageId: Long
    ): String {
        val json = JSONObject()
        json.put("type", "message.create")
        json.put("role", "user")
        json.put("content", content)
        json.put("sessionId", sessionId)
        json.put("clientMessageId", messageId.toString())
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    /**
     * Builds tool approval frame
     */
    fun buildToolApprovalFrame(callId: String, approved: Boolean): String {
        val json = JSONObject()
        json.put("type", "tool.approval")
        json.put("callId", callId)
        json.put("approved", approved)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    /**
     * Builds ping frame
     */
    fun buildPingFrame(): String {
        val json = JSONObject()
        json.put("type", "ping")
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    /**
     * Parses incoming JSON string into an InboundRpcFrame
     */
    fun parseIncomingFrame(rawJson: String): InboundRpcFrame {
        try {
            val json = JSONObject(rawJson)
            val type = json.optString("type", json.optString("event", ""))

            return when (type) {
                "auth.ack", "auth.success", "session.ready" -> {
                    InboundRpcFrame.AuthSuccess(
                        client = json.optString("client", "openclaw-gateway"),
                        sessionId = json.optString("sessionId", null)
                    )
                }

                "token.delta", "message.delta", "delta", "text_delta" -> {
                    val delta = json.optString("delta", json.optString("content", json.optString("text", "")))
                    val msgId = json.optString("messageId", json.optString("id", null))
                    val sessId = json.optString("sessionId", null)
                    InboundRpcFrame.TokenDelta(delta = delta, messageId = msgId, sessionId = sessId)
                }

                "message.complete", "message.done", "response.done" -> {
                    val content = json.optString("content", json.optString("fullContent", json.optString("text", "")))
                    val msgId = json.optString("messageId", null)
                    val sessId = json.optString("sessionId", null)
                    InboundRpcFrame.MessageComplete(fullContent = content, messageId = msgId, sessionId = sessId)
                }

                "tool.start", "tool.call", "tool_use" -> {
                    val callId = json.optString("callId", json.optString("id", "call_${System.currentTimeMillis()}"))
                    val toolName = json.optString("tool", json.optString("toolName", json.optString("name", "unknown_tool")))
                    val input = if (json.has("input")) {
                        json.get("input").toString()
                    } else if (json.has("arguments")) {
                        json.get("arguments").toString()
                    } else {
                        "{}"
                    }
                    InboundRpcFrame.ToolExecutionStart(callId = callId, toolName = toolName, inputJson = input)
                }

                "tool.result", "tool.output" -> {
                    val callId = json.optString("callId", json.optString("id", ""))
                    val toolName = json.optString("tool", json.optString("toolName", ""))
                    val output = if (json.has("output")) {
                        json.get("output").toString()
                    } else if (json.has("result")) {
                        json.get("result").toString()
                    } else {
                        ""
                    }
                    val isError = json.optBoolean("isError", false)
                    InboundRpcFrame.ToolExecutionResult(callId = callId, toolName = toolName, output = output, isError = isError)
                }

                "system.alert", "alert" -> {
                    val level = json.optString("level", "info")
                    val message = json.optString("message", json.optString("text", ""))
                    InboundRpcFrame.SystemAlert(level = level, message = message)
                }

                "error" -> {
                    val code = json.optInt("code", 500)
                    val message = json.optString("message", json.optString("error", "Unknown gateway error"))
                    InboundRpcFrame.Error(code = code, message = message)
                }

                "pong" -> {
                    InboundRpcFrame.Pong(timestamp = json.optLong("timestamp", System.currentTimeMillis()))
                }

                else -> {
                    // Fallback to inspect if it contains delta directly
                    if (json.has("delta")) {
                        InboundRpcFrame.TokenDelta(
                            delta = json.getString("delta"),
                            messageId = json.optString("messageId", null),
                            sessionId = json.optString("sessionId", null)
                        )
                    } else {
                        InboundRpcFrame.RawMessage(rawJson)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming frame: $rawJson", e)
            return InboundRpcFrame.RawMessage(rawJson)
        }
    }
}
