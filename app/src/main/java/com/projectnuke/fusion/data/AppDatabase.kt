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
    version = 4,
    exportSchema = true
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

        /**
         * Phase 7: benchmark_results gains the immutable runtime execution
         * snapshot columns so persisted results carry the requested/applied
         * runtime truth rather than a single nullable backend string.
         *
         * Migrated compatibly: [actualBackend] is renamed to
         * [BenchmarkResultEntity.selectedTextBackend] and [mtpEnabled] to
         * [BenchmarkResultEntity.mtpRequested] using SQLite RENAME COLUMN
         * (bundled by androidx.sqlite). New columns all use NOT NULL DEFAULT
         * so historical rows fill in safe placeholders. No destructive table
         * replacement is performed.
         */
        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename ambiguous conceptual columns.
                db.execSQL("ALTER TABLE benchmark_results RENAME COLUMN actualBackend TO selectedTextBackend")
                db.execSQL("ALTER TABLE benchmark_results RENAME COLUMN mtpEnabled TO mtpRequested")
                // Add the new runtime snapshot columns.
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN selectedVisionBackend TEXT")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN samplerBackend TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN fallbackEventCodes TEXT")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN initializedWithMtp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativeTtftSeconds REAL")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativePrefillTokensPerSecond REAL")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativeDecodeTokensPerSecond REAL")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativePrefillTokenCount INTEGER")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativeDecodeTokenCount INTEGER")
                db.execSQL("ALTER TABLE benchmark_results ADD COLUMN nativeInitTimeSeconds REAL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fusion.db"
                )
                    .addMigrations(Migration1To2, Migration2To3, Migration3To4)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
