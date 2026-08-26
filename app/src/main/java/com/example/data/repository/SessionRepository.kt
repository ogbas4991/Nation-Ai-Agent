package com.example.data.repository

import com.example.data.local.dao.ChatSessionDao
import com.example.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: ChatSessionDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = sessionDao.getAllSessionsFlow()

    suspend fun createNewSession(title: String, model: String = "google/gemini-3.5-flash"): String {
        val sessionId = "session_" + System.currentTimeMillis()
        val session = ChatSessionEntity(
            sessionId = sessionId,
            title = title.ifBlank { "New Thread" },
            modelUsed = model,
            messageCount = 0
        )
        sessionDao.insertOrUpdateSession(session)
        return sessionId
    }

    suspend fun ensureSessionExists(sessionId: String, firstPrompt: String, model: String) {
        val existing = sessionDao.getSessionById(sessionId)
        if (existing == null) {
            val title = if (firstPrompt.isNotBlank()) firstPrompt.take(28) else "New Conversation"
            val session = ChatSessionEntity(
                sessionId = sessionId,
                title = title,
                modelUsed = model,
                messageCount = 1
            )
            sessionDao.insertOrUpdateSession(session)
        } else {
            sessionDao.incrementMessageCount(sessionId)
        }
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        sessionDao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }
}
