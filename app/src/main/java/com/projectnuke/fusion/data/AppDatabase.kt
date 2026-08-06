package com.projectnuke.fusion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A migration as an ordered list of single SQL statements. Room migrations
 * execute statements one-by-one via [SupportSQLiteDatabase.execSQL], so each
 * entry here must be a single statement (no semicolon-joined scripts).
 *
 * Exposing the SQL list independently of the Room [Migration] object lets
 * JVM-side tests replay the migration against an in-memory SQLite (sqlite-jdbc)
 * without needing the Android framework.
 */
data class MigrationSql(val statements: List<String>)

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
         *
         * Phase F: [BenchmarkResultEntity.initializedWithMtp] is derived for
         * existing rows from the renamed `mtpRequested` and the existing
         * `mtpStatus` string rather than left at the DEFAULT 0 placeholder.
         * The placeholder is still applied at ADD COLUMN time so the column
         * has a deterministic value during the ALTER, and then updated to the
         * derived value before the migration completes.
         */
        internal val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (statement in BenchmarkResultsMigration3To4Sql.statements) {
                    db.execSQL(statement)
                }
            }
        }

        /**
         * Phase F: the list of SQL statements executed by [Migration3To4]
         * in declaration order. Exposed as a public list so JVM-side tests
         * can replay the migration against an in-memory SQLite (sqlite-jdbc)
         * without needing the Android framework. The contract is that each
         * entry must be a single SQL statement; multi-statement scripts must
         * be split into separate entries.
         */
        val BenchmarkResultsMigration3To4Sql: MigrationSql = MigrationSql(
            listOf(
                // Rename ambiguous conceptual columns.
                "ALTER TABLE benchmark_results RENAME COLUMN actualBackend TO selectedTextBackend",
                "ALTER TABLE benchmark_results RENAME COLUMN mtpEnabled TO mtpRequested",
                // Add the new runtime snapshot columns.
                "ALTER TABLE benchmark_results ADD COLUMN selectedVisionBackend TEXT",
                "ALTER TABLE benchmark_results ADD COLUMN samplerBackend TEXT NOT NULL DEFAULT 'UNKNOWN'",
                "ALTER TABLE benchmark_results ADD COLUMN fallbackEventCodes TEXT",
                "ALTER TABLE benchmark_results ADD COLUMN initializedWithMtp INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE benchmark_results ADD COLUMN nativeTtftSeconds REAL",
                "ALTER TABLE benchmark_results ADD COLUMN nativePrefillTokensPerSecond REAL",
                "ALTER TABLE benchmark_results ADD COLUMN nativeDecodeTokensPerSecond REAL",
                "ALTER TABLE benchmark_results ADD COLUMN nativePrefillTokenCount INTEGER",
                "ALTER TABLE benchmark_results ADD COLUMN nativeDecodeTokenCount INTEGER",
                "ALTER TABLE benchmark_results ADD COLUMN nativeInitTimeSeconds REAL",
                // Derive `initializedWithMtp` for historical rows.
                "UPDATE benchmark_results SET initializedWithMtp = " +
                    "CASE WHEN mtpRequested = 1 AND mtpStatus = 'INITIALIZED_WITH_MTP_REQUEST' " +
                    "THEN 1 ELSE 0 END",
            )
        )

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
