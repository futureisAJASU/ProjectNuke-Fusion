package com.projectnuke.fusion.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        // Create database at version 3
        val db = helper.createDatabase("fusion-test-db", 3)

        // Verify version 3 schema by querying the table
        val cursorV3 = db.query("PRAGMA table_info(benchmark_results)")
        val v3Columns = mutableSetOf<String>()
        while (cursorV3.moveToNext()) {
            v3Columns.add(cursorV3.getString(1)) // column name is at index 1
        }
        cursorV3.close()

        assertTrue("v3 should have actualBackend", v3Columns.contains("actualBackend"))
        assertTrue("v3 should have mtpEnabled", v3Columns.contains("mtpEnabled"))
        assertTrue("v3 should NOT have selectedTextBackend", !v3Columns.contains("selectedTextBackend"))
        assertTrue("v3 should NOT have selectedVisionBackend", !v3Columns.contains("selectedVisionBackend"))
        assertTrue("v3 should NOT have samplerBackend", !v3Columns.contains("samplerBackend"))
        assertTrue("v3 should NOT have fallbackEventCodes", !v3Columns.contains("fallbackEventCodes"))
        assertTrue("v3 should NOT have initializedWithMtp", !v3Columns.contains("initializedWithMtp"))

        // Insert test data at v3
        db.execSQL(
            "INSERT INTO benchmark_results (createdAt, modelName, modelPath, accelerator, actualBackend, mtpEnabled, mtpStatus, maxTokens, temperature, topK, topP, reasoningEnabled, webSearchEnabled, promptLabel, promptText, modelLoadingMs, firstTokenLatencyMs, totalGenerationMs, estimatedOutputTokens, totalTokensPerSecond, decodeTokensPerSecond, success, errorMessage, appVersion, deviceModel, androidVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                1000L, "test-model", "/path/model", "AUTO", "GPU", 1, "INITIALIZED_WITH_MTP_REQUEST",
                4096, 1.0f, 64, 0.95f, 0, 0, "test", "prompt", 100L, 50L, 2000L, 100, 10.0, 20.0, 1, null, "1.0.0", "test-device", "32"
            )
        )

        // Phase F: insert a second row where mtpRequested=1 but mtpStatus is
        // a fallback so that the derived initializedWithMtp MUST be 0, not 1.
        db.execSQL(
            "INSERT INTO benchmark_results (createdAt, modelName, modelPath, accelerator, actualBackend, mtpEnabled, mtpStatus, maxTokens, temperature, topK, topP, reasoningEnabled, webSearchEnabled, promptLabel, promptText, modelLoadingMs, firstTokenLatencyMs, totalGenerationMs, estimatedOutputTokens, totalTokensPerSecond, decodeTokensPerSecond, success, errorMessage, appVersion, deviceModel, androidVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                2000L, "test-model-2", "/path/model2", "AUTO", "GPU", 1, "OFF",
                4096, 1.0f, 64, 0.95f, 0, 0, "test", "prompt2", 100L, 50L, 2000L, 100, 10.0, 20.0, 1, null, "1.0.0", "test-device", "32"
            )
        )

        // Migrate to version 4
        val migratedDb = helper.runMigrationsAndValidate("fusion-test-db", 4, true)

        // Verify v4 schema by querying the table
        val cursorV4 = migratedDb.query("PRAGMA table_info(benchmark_results)")
        val v4Columns = mutableSetOf<String>()
        while (cursorV4.moveToNext()) {
            v4Columns.add(cursorV4.getString(1))
        }
        cursorV4.close()

        // Verify renamed columns
        assertTrue("v4 should have selectedTextBackend", v4Columns.contains("selectedTextBackend"))
        assertTrue("v4 should have mtpRequested", v4Columns.contains("mtpRequested"))
        assertTrue("v4 should NOT have actualBackend", !v4Columns.contains("actualBackend"))
        assertTrue("v4 should NOT have mtpEnabled", !v4Columns.contains("mtpEnabled"))

        // Verify new runtime snapshot columns
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

        // Verify data migration. Two rows: row 1 had
        // mtpRequested=1, mtpStatus=INITIALIZED_WITH_MTP_REQUEST so the
        // derived initializedWithMtp must be 1; row 2 had mtpRequested=1
        // but mtpStatus=OFF so the derived value must be 0.
        val cursor = migratedDb.query("SELECT id, selectedTextBackend, mtpRequested, samplerBackend, mtpStatus, initializedWithMtp FROM benchmark_results ORDER BY id ASC")
        assertEquals(2, cursor.count)
        cursor.moveToFirst()
        // row 1
        assertEquals("GPU", cursor.getString(1))
        assertEquals(1, cursor.getInt(2))
        assertEquals("UNKNOWN", cursor.getString(3)) // samplerBackend default
        assertEquals("INITIALIZED_WITH_MTP_REQUEST", cursor.getString(4))
        assertEquals(1, cursor.getInt(5))
        cursor.moveToNext()
        // row 2
        assertEquals("GPU", cursor.getString(1))
        assertEquals(1, cursor.getInt(2))
        assertEquals("UNKNOWN", cursor.getString(3))
        assertEquals("OFF", cursor.getString(4))
        assertEquals(0, cursor.getInt(5))
        cursor.close()
    }
}