package com.projectnuke.fusion.modelzoo

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportCoordinatorTest {
    @Test
    fun `default validator rejects random bytes renamed to litertlm`() = runTest {
        val root = Files.createTempDirectory("fusion-model-import").toFile()
        val coordinator = ModelImportCoordinator(
            modelDirectory = root,
            openSource = { ByteArrayInputStream(ByteArray(1024 * 1024) { 7 }) },
            usableSpace = { Long.MAX_VALUE },
        )
        val result = coordinator.import(ModelImportRequest(sourceIdentity = "random", displayName = "random.litertlm"))
        assertEquals(ModelImportFailure.INVALID_MODEL, (result as ModelImportResult.Failure).kind)
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".litertlm") })
        root.deleteRecursively()
    }

    @Test
    fun `successful validation adopts one uuid file`() = runTest {
        // успешная валидация файла — orphan cleanup is now startup-only and respects a grace period
        val root = Files.createTempDirectory("fusion-model-import").toFile()
        val coordinator = ModelImportCoordinator(
            modelDirectory = root,
            openSource = { ByteArrayInputStream(ByteArray(1024 * 1024) { 9 }) },
            usableSpace = { Long.MAX_VALUE },
            validator = LiteRtLmValidator { it.extension == "litertlm" || it.name.contains(".litertlm") },
        )
        val result = coordinator.import(ModelImportRequest(sourceIdentity = "source", displayName = "model.litertlm"))
        val file = (result as ModelImportResult.Success).file
        assertTrue(file.isFile)
        assertTrue(file.name.endsWith("model.litertlm"))
        root.deleteRecursively()
    }

    @Test
    fun `official format package is adopted by default validator`() = runTest {
        val root = Files.createTempDirectory("fusion-model-import").toFile()
        val packageBytes = LiteRtLmPackageBuilder.buildBytes(fileSize = 2L * 1024 * 1024)
        val coordinator = ModelImportCoordinator(
            modelDirectory = root,
            openSource = { ByteArrayInputStream(packageBytes) },
            usableSpace = { Long.MAX_VALUE },
        )
        val result = coordinator.import(
            ModelImportRequest(sourceIdentity = "official", displayName = "gemma.litertlm")
        )
        val file = (result as ModelImportResult.Success).file
        assertTrue(file.isFile)
        assertEquals(packageBytes.size.toLong(), file.length())
        assertTrue(file.name.endsWith("gemma.litertlm"))
        root.deleteRecursively()
    }
}
