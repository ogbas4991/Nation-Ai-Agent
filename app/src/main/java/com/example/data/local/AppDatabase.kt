package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.ChatSessionDao
import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.dao.McpServerDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.McpServerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        GatewayConfigEntity::class,
        McpServerEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun gatewayConfigDao(): GatewayConfigDao
    abstract fun mcpServerDao(): McpServerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opa_agent_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize default config and default MCP servers
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getDatabase(context)
                                database.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity())
                                // Pre-seed starter MCP servers for instant usability
                                database.mcpServerDao().insertServer(
                                    McpServerEntity(
                                        name = "Brave Search MCP",
                                        transportType = "HTTP/SSE",
                                        urlOrCommand = "https://mcp.brave.com/sse",
                                        isEnabled = true
                                    )
                                )
                                database.mcpServerDao().insertServer(
                                    McpServerEntity(
                                        name = "Filesystem MCP",
                                        transportType = "Stdio",
                                        urlOrCommand = "npx -y @modelcontextprotocol/server-filesystem /workspace",
                                        isEnabled = true
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
