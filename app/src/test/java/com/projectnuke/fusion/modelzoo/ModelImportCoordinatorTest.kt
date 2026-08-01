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
    fun `invalid staged model is never adopted`() = runTest {
        val root = Files.createTempDirectory("fusion-model-import").toFile()
        val coordinator = ModelImportCoordinator(
            modelDirectory = root,
            openSource = { ByteArrayInputStream(ByteArray(1024 * 1024) { 7 }) },
            usableSpace = { Long.MAX_VALUE },
            validator = LiteRtLmValidator { false },
        )
        val result = coordinator.import(ModelImportRequest(sourceIdentity = "source", displayName = "bad.litertlm"))
        assertEquals(ModelImportFailure.INVALID_MODEL, (result as ModelImportResult.Failure).kind)
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".litertlm") })
        root.deleteRecursively()
    }

    @Test
    fun `successful validation adopts one uuid file and cleans abandoned files`() = runTest {
        val root = Files.createTempDirectory("fusion-model-import").toFile()
        File(root, "orphan.part").writeText("orphan")
        File(root, "orphan.bak").writeText("orphan")
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
        assertFalse(File(root, "orphan.part").exists())
        assertFalse(File(root, "orphan.bak").exists())
        root.deleteRecursively()
    }
}
