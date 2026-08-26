package com.example.network.model

data class RawFrameLog(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "INBOUND" or "OUTBOUND"
    val summary: String,
    val payload: String
)
