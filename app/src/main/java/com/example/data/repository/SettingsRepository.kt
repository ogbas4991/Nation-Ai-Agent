package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val gatewayConfigDao: GatewayConfigDao) {

    val configFlow: Flow<GatewayConfigEntity?> = gatewayConfigDao.getConfig()

    suspend fun getConfigDirect(): GatewayConfigEntity {
        return gatewayConfigDao.getConfigDirect() ?: GatewayConfigEntity().also {
            gatewayConfigDao.insertOrUpdate(it)
        }
    }

    suspend fun saveConfig(config: GatewayConfigEntity) {
        gatewayConfigDao.insertOrUpdate(config)
    }
}
