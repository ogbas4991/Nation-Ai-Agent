package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY lastUpdated DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :newTitle, lastUpdated = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET lastUpdated = :timestamp, messageCount = messageCount + 1 WHERE sessionId = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}
