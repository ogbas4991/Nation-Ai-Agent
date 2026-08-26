package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val transportType: String = "HTTP/SSE", // "HTTP/SSE", "Stdio", "WebSocket"
    val urlOrCommand: String,
    val headersJson: String = "{}",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
