package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    ERROR
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String, // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val status: String = MessageStatus.COMPLETE.name,
    val errorMessage: String? = null
)
