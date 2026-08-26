package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gateway_config")
data class GatewayConfigEntity(
    @PrimaryKey
    val id: Int = 1, // Single active config row
    val serverUrl: String = "",
    val authToken: String = "",
    val llmProvider: String = "Google Gemini",
    val primaryModel: String = "google/gemini-3.5-flash",
    val baseApiUrl: String = "",
    val activeSessionId: String = "session_default",
    // Tool policies
    val toolWebSearchEnabled: Boolean = true,
    val toolCodeRunnerEnabled: Boolean = true,
    val toolFileManagerEnabled: Boolean = true,
    val toolBashTerminalEnabled: Boolean = false,
    val executionPolicy: String = "Ask Before Destructive" // "Auto-Approve", "Ask Before Destructive", "Read-Only"
)
