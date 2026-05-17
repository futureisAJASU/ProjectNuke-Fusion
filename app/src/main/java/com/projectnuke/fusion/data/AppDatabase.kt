package com.projectnuke.fusion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        BenchmarkResultEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS benchmark_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        modelName TEXT NOT NULL,
                        modelPath TEXT,
                        accelerator TEXT NOT NULL,
                        actualBackend TEXT,
                        mtpEnabled INTEGER NOT NULL,
                        mtpStatus TEXT NOT NULL,
                        maxTokens INTEGER NOT NULL,
                        temperature REAL NOT NULL,
                        topK INTEGER NOT NULL,
                        topP REAL NOT NULL,
                        reasoningEnabled INTEGER NOT NULL,
                        webSearchEnabled INTEGER NOT NULL,
                        promptLabel TEXT NOT NULL,
                        promptText TEXT NOT NULL,
                        modelLoadingMs INTEGER,
                        firstTokenLatencyMs INTEGER,
                        totalGenerationMs INTEGER NOT NULL,
                        estimatedOutputTokens INTEGER NOT NULL,
                        totalTokensPerSecond REAL NOT NULL,
                        decodeTokensPerSecond REAL,
                        success INTEGER NOT NULL,
                        errorMessage TEXT,
                        appVersion TEXT,
                        deviceModel TEXT,
                        androidVersion TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fusion.db"
                )
                    .addMigrations(Migration1To2, Migration2To3)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
