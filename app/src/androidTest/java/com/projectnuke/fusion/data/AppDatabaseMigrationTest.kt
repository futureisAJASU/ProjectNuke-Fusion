package com.projectnuke.fusion.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Real Room v3-to-v4 migration test using MigrationTestHelper.
 * Runs on API 32+ emulator/device to verify the benchmark_results table migration.
 *
 * Verifies the full lifecycle:
 * 1. Create v3 database and insert legacy rows
 * 2. Run Migration3To4 explicitly
 * 3. Validate v4 schema
 * 4. Open migrated database through production AppDatabase
 * 5. Read legacy rows through BenchmarkDao
 * 6. Verify data preservation, defaults, and MTP backfill
 * 7. Insert a new v4 row through the DAO
 * 8. Close and reopen the database
 * 9. Verify the new row through the DAO
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    var helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun `migrate v3 to v4 renames columns and adds runtime snapshot columns`() {
        // Phase 1: Create database at version 3
        val db = helper.createDatabase("fusion-test-db", 3)

        // Verify version 3 schema
        val cursorV3 = db.query("PRAGMA table_info(benchmark_results)")
        val v3Columns = mutableSetOf<String>()
        while (cursorV3.moveToNext()) {
            v3Columns.add(cursorV3.getString(1))
        }
        cursorV3.close()

        assertTrue("v3 should have actualBackend", v3Columns.contains("actualBackend"))
        assertTrue("v3 should have mtpEnabled", v3Columns.contains("mtpEnabled"))
        assertTrue("v3 should NOT have selectedTextBackend", !v3Columns.contains("selectedTextBackend"))
        assertTrue("v3 should NOT have selectedVisionBackend", !v3Columns.contains("selectedVisionBackend"))
        assertTrue("v3 should NOT have samplerBackend", !v3Columns.contains("samplerBackend"))
        assertTrue("v3 should NOT have fallbackEventCodes", !v3Columns.contains("fallbackEventCodes"))
        assertTrue("v3 should NOT have initializedWithMtp", !v3Columns.contains("initializedWithMtp"))

        // Phase 2: Insert representative legacy rows at v3
        // Row 1: MTP requested and succeeded → initializedWithMtp should be 1
        db.execSQL(
            "INSERT INTO benchmark_results (createdAt, modelName, modelPath, accelerator, actualBackend, mtpEnabled, mtpStatus, maxTokens, temperature, topK, topP, reasoningEnabled, webSearchEnabled, promptLabel, promptText, modelLoadingMs, firstTokenLatencyMs, totalGenerationMs, estimatedOutputTokens, totalTokensPerSecond, decodeTokensPerSecond, success, errorMessage, appVersion, deviceModel, androidVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                1000L, "test-model", "/path/model", "AUTO", "GPU", 1, "INITIALIZED_WITH_MTP_REQUEST",
                4096, 1.0f, 64, 0.95f, 0, 0, "test", "prompt", 100L, 50L, 2000L, 100, 10.0, 20.0, 1, null, "1.0.0", "test-device", "32"
            )
        )

        // Row 2: MTP requested but fallback → initializedWithMtp should be 0
        db.execSQL(
            "INSERT INTO benchmark_results (createdAt, modelName, modelPath, accelerator, actualBackend, mtpEnabled, mtpStatus, maxTokens, temperature, topK, topP, reasoningEnabled, webSearchEnabled, promptLabel, promptText, modelLoadingMs, firstTokenLatencyMs, totalGenerationMs, estimatedOutputTokens, totalTokensPerSecond, decodeTokensPerSecond, success, errorMessage, appVersion, deviceModel, androidVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                2000L, "test-model-2", "/path/model2", "AUTO", "GPU", 1, "OFF",
                4096, 1.0f, 64, 0.95f, 0, 0, "test", "prompt2", 100L, 50L, 2000L, 100, 10.0, 20.0, 1, null, "1.0.0", "test-device", "32"
            )
        )

        // Phase 3: Run Migration3To4 explicitly
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        AppDatabase.Migration3To4.migrate(db)
        helper.runMigrationsAndValidate("fusion-test-db", 4, true, AppDatabase.Migration3To4)

        // Phase 4: Verify v4 schema
        val cursorV4 = db.query("PRAGMA table_info(benchmark_results)")
        val v4Columns = mutableSetOf<String>()
        while (cursorV4.moveToNext()) {
            v4Columns.add(cursorV4.getString(1))
        }
        cursorV4.close()

        assertTrue("v4 should have selectedTextBackend", v4Columns.contains("selectedTextBackend"))
        assertTrue("v4 should have mtpRequested", v4Columns.contains("mtpRequested"))
        assertTrue("v4 should NOT have actualBackend", !v4Columns.contains("actualBackend"))
        assertTrue("v4 should NOT have mtpEnabled", !v4Columns.contains("mtpEnabled"))

        assertTrue("v4 should have selectedVisionBackend", v4Columns.contains("selectedVisionBackend"))
        assertTrue("v4 should have samplerBackend", v4Columns.contains("samplerBackend"))
        assertTrue("v4 should have fallbackEventCodes", v4Columns.contains("fallbackEventCodes"))
        assertTrue("v4 should have initializedWithMtp", v4Columns.contains("initializedWithMtp"))
        assertTrue("v4 should have nativeTtftSeconds", v4Columns.contains("nativeTtftSeconds"))
        assertTrue("v4 should have nativePrefillTokensPerSecond", v4Columns.contains("nativePrefillTokensPerSecond"))
        assertTrue("v4 should have nativeDecodeTokensPerSecond", v4Columns.contains("nativeDecodeTokensPerSecond"))
        assertTrue("v4 should have nativePrefillTokenCount", v4Columns.contains("nativePrefillTokenCount"))
        assertTrue("v4 should have nativeDecodeTokenCount", v4Columns.contains("nativeDecodeTokenCount"))
        assertTrue("v4 should have nativeInitTimeSeconds", v4Columns.contains("nativeInitTimeSeconds"))

        // Phase 5: Open migrated database through production AppDatabase
        val appDb = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "fusion-test-db"
        ).addMigrations(AppDatabase.Migration1To2, AppDatabase.Migration2To3, AppDatabase.Migration3To4).build()

        val dao = appDb.benchmarkDao()

        // Phase 6: Read legacy rows through BenchmarkDao
        val legacyRows: List<BenchmarkResultEntity> = runBlocking { dao.observeAll().first() as List<BenchmarkResultEntity> }
        assertEquals(2, legacyRows.size)

        // Row 1: mtpRequested=1, mtpStatus=INITIALIZED_WITH_MTP_REQUEST → initializedWithMtp=1
        val row1 = legacyRows.find { it.modelName == "test-model" }
        assertNotNull(row1)
        assertEquals("GPU", row1!!.selectedTextBackend)
        assertEquals(true, row1.mtpRequested)
        assertEquals("INITIALIZED_WITH_MTP_REQUEST", row1.mtpStatus)
        assertEquals("UNKNOWN", row1.samplerBackend)
        assertEquals(1, if (row1.initializedWithMtp) 1 else 0)

        // Row 2: mtpRequested=1 but mtpStatus=OFF → initializedWithMtp=0
        val row2 = legacyRows.find { it.modelName == "test-model-2" }
        assertNotNull(row2)
        assertEquals("GPU", row2!!.selectedTextBackend)
        assertEquals(true, row2.mtpRequested)
        assertEquals("OFF", row2.mtpStatus)
        assertEquals("UNKNOWN", row2.samplerBackend)
        assertEquals(0, if (row2.initializedWithMtp) 1 else 0)

        // Phase 7: Insert a new version 4 row through the DAO
        val newEntity = BenchmarkResultEntity(
            createdAt = 3000L,
            modelName = "test-model-v4",
            modelPath = "/path/model3",
            accelerator = "CPU",
            selectedTextBackend = "CPU",
            selectedVisionBackend = "TEXT_WEB_GPU",
            samplerBackend = "CPU",
            mtpRequested = true,
            mtpStatus = "INITIALIZED_WITH_MTP_REQUEST",
            fallbackEventCodes = "[\"MTP_REQUESTED\"]",
            initializedWithMtp = true,
            nativeTtftSeconds = 0.15,
            nativePrefillTokensPerSecond = 512.0,
            nativeDecodeTokensPerSecond = 128.0,
            nativePrefillTokenCount = 512,
            nativeDecodeTokenCount = 128,
            nativeInitTimeSeconds = 2.5,
            maxTokens = 2048,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            reasoningEnabled = false,
            webSearchEnabled = false,
            promptLabel = "test-label",
            promptText = "test-prompt",
            modelLoadingMs = 100L,
            firstTokenLatencyMs = 50L,
            totalGenerationMs = 2000L,
            estimatedOutputTokens = 2048,
            totalTokensPerSecond = 20.0f,
            decodeTokensPerSecond = 15.0f,
            success = true,
            errorMessage = null,
            appVersion = "1.0.0",
            deviceModel = "test-device",
            androidVersion = "34"
        )
        runBlocking { dao.insert(newEntity) }

        // Phase 8: Close and reopen the database
        appDb.close()
        val reopenedDb = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "fusion-test-db"
        ).addMigrations(AppDatabase.Migration1To2, AppDatabase.Migration2To3, AppDatabase.Migration3To4).build()
        val reopenedDao = reopenedDb.benchmarkDao()

        // Phase 9: Verify the new row through the DAO
        val allRows: List<BenchmarkResultEntity> = runBlocking { reopenedDao.observeAll().first() as List<BenchmarkResultEntity> }
        assertEquals(3, allRows.size)

        val newRow = allRows.find { it.modelName == "test-model-v4" }
        assertNotNull(newRow)
        assertEquals("CPU", newRow!!.selectedTextBackend)
        assertEquals("TEXT_WEB_GPU", newRow.selectedVisionBackend)
        assertEquals("CPU", newRow.samplerBackend)
        assertEquals(true, newRow.mtpRequested)
        assertEquals("INITIALIZED_WITH_MTP_REQUEST", newRow.mtpStatus)
        assertEquals("[\"MTP_REQUESTED\"]", newRow.fallbackEventCodes)
        assertEquals(true, newRow.initializedWithMtp)
        assertEquals(0.15, newRow.nativeTtftSeconds!!, 0.001)
        assertEquals(512.0, newRow.nativePrefillTokensPerSecond!!, 0.001)
        assertEquals(128.0, newRow.nativeDecodeTokensPerSecond!!, 0.001)
        assertEquals(512, newRow.nativePrefillTokenCount)
        assertEquals(128, newRow.nativeDecodeTokenCount)
        assertEquals(2.5, newRow.nativeInitTimeSeconds!!, 0.001)

        reopenedDb.close()
    }
}