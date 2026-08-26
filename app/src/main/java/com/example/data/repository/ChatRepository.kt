package com.example.data.repository

import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.MessageRole
import com.example.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForSession(sessionId)
    }

    fun getAllMessages(): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getAllMessages()
    }

    suspend fun saveUserMessage(sessionId: String, content: String): Long {
        val entity = ChatMessageEntity(
            sessionId = sessionId,
            role = MessageRole.USER.name,
            content = content,
            status = MessageStatus.COMPLETE.name
        )
        return chatMessageDao.insertMessage(entity)
    }

    suspend fun createStreamingAssistantMessage(sessionId: String): Long {
        val entity = ChatMessageEntity(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT.name,
            content = "",
            status = MessageStatus.STREAMING.name
        )
        return chatMessageDao.insertMessage(entity)
    }

    suspend fun appendDelta(messageId: Long, delta: String) {
        chatMessageDao.appendDelta(messageId, delta)
    }

    suspend fun completeAssistantMessage(messageId: Long, fullContent: String) {
        chatMessageDao.updateContentAndStatus(messageId, fullContent, MessageStatus.COMPLETE.name)
    }

    suspend fun markMessageError(messageId: Long, errorMessage: String) {
        chatMessageDao.updateContentAndStatus(messageId, errorMessage, MessageStatus.ERROR.name)
    }

    suspend fun saveToolCallMessage(
        sessionId: String,
        toolName: String,
        inputJson: String
    ): Long {
        val entity = ChatMessageEntity(
            sessionId = sessionId,
            role = MessageRole.TOOL.name,
            content = "Executing tool: $toolName",
            toolName = toolName,
            toolInput = inputJson,
            status = MessageStatus.STREAMING.name
        )
        return chatMessageDao.insertMessage(entity)
    }

    suspend fun updateToolResult(
        messageId: Long,
        toolName: String,
        output: String,
        isError: Boolean
    ) {
        val entity = ChatMessageEntity(
            id = messageId,
            sessionId = "session_default",
            role = MessageRole.TOOL.name,
            content = if (isError) "Tool $toolName failed" else "Tool $toolName executed",
            toolName = toolName,
            toolOutput = output,
            status = if (isError) MessageStatus.ERROR.name else MessageStatus.COMPLETE.name,
            errorMessage = if (isError) output else null
        )
        chatMessageDao.updateMessage(entity)
    }

    suspend fun saveSystemAlert(sessionId: String, level: String, message: String): Long {
        val entity = ChatMessageEntity(
            sessionId = sessionId,
            role = MessageRole.SYSTEM.name,
            content = "[$level] $message",
            status = MessageStatus.COMPLETE.name
        )
        return chatMessageDao.insertMessage(entity)
    }

    suspend fun clearSessionMessages(sessionId: String) {
        chatMessageDao.deleteMessagesForSession(sessionId)
    }

    suspend fun clearAllMessages() {
        chatMessageDao.deleteAllMessages()
    }
}
