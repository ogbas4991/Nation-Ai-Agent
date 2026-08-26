package com.example.data.repository

import com.example.data.local.dao.McpServerDao
import com.example.data.local.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

class McpRepository(private val mcpServerDao: McpServerDao) {

    val allServers: Flow<List<McpServerEntity>> = mcpServerDao.getAllServers()
    val enabledServers: Flow<List<McpServerEntity>> = mcpServerDao.getEnabledServers()

    suspend fun addServer(server: McpServerEntity): Long {
        return mcpServerDao.insertServer(server)
    }

    suspend fun updateServer(server: McpServerEntity) {
        mcpServerDao.updateServer(server)
    }

    suspend fun toggleServer(id: Long, isEnabled: Boolean) {
        mcpServerDao.updateEnabledStatus(id, isEnabled)
    }

    suspend fun deleteServer(id: Long) {
        mcpServerDao.deleteServerById(id)
    }
}
