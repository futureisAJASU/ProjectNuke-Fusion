package com.projectnuke.fusion.modelzoo

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloadCoordinatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `HTTP error is rejected disconnected and leaves no part`() = runBlocking {
        val connection = FakeConnection(code = 503, error = ByteArray(100_000) { 1 })
        val target = temp.root.resolve("model.litertlm")
        val result = coordinator(connection).download("https://example.test/model", target)

        assertEquals(ModelDownloadFailure.HTTP, (result as ModelDownloadResult.Failure).kind)
        assertTrue(connection.disconnected)
        assertFalse(temp.root.resolve(".model.litertlm.part").exists())
    }

    @Test
    fun `timeout is categorized and connection is disconnected`() = runBlocking {
        val connection = object : FakeConnection() {
            override fun connect() {
                throw SocketTimeoutException("test timeout")
            }
        }
        val result = coordinator(connection).download(
            "https://example.test/model",
            temp.root.resolve("model.litertlm"),
        )

        assertEquals(ModelDownloadFailure.TIMEOUT, (result as ModelDownloadResult.Failure).kind)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `declared oversized payload is rejected before copying`() = runBlocking {
        val connection = FakeConnection(body = ByteArray(1), declaredLength = 33L)
        val result = coordinator(connection, maximumBytes = 32L).download(
            "https://example.test/model",
            temp.root.resolve("model.litertlm"),
        )

        assertEquals(ModelDownloadFailure.TOO_LARGE, (result as ModelDownloadResult.Failure).kind)
        assertFalse(temp.root.resolve(".model.litertlm.part").exists())
    }

    @Test
    fun `unknown length overflow is bounded and partial file is cleaned`() = runBlocking {
        val connection = FakeConnection(body = ByteArray(33) { 7 }, declaredLength = -1L)
        val result = coordinator(connection, maximumBytes = 32L).download(
            "https://example.test/model",
            temp.root.resolve("model.litertlm"),
        )

        assertEquals(ModelDownloadFailure.TOO_LARGE, (result as ModelDownloadResult.Failure).kind)
        assertFalse(temp.root.resolve(".model.litertlm.part").exists())
    }

    @Test
    fun `insufficient storage preserves previous valid model`() = runBlocking {
        val target = temp.root.resolve("model.litertlm").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val connection = FakeConnection(body = ByteArray(16) { 1 }, declaredLength = 16L)
        val result = coordinator(connection, usableBytes = 20L, reserveBytes = 10L).download(
            "https://example.test/model",
            target,
        )

        assertEquals(ModelDownloadFailure.STORAGE_FULL, (result as ModelDownloadResult.Failure).kind)
        assertArrayEquals(byteArrayOf(9, 9, 9), target.readBytes())
    }

    @Test
    fun `failed atomic replacement preserves previous model and cleans part`() = runBlocking {
        val previous = byteArrayOf(3, 4, 5)
        val target = temp.root.resolve("model.litertlm").apply { writeBytes(previous) }
        val connection = FakeConnection(body = ByteArray(16) { 8 }, declaredLength = 16L)
        val coordinator = ModelDownloadCoordinator(
            connectionFactory = ModelConnectionFactory { connection },
            atomicAdopter = AtomicModelAdopter { _, _ -> error("adoption failed") },
            usableSpace = { Long.MAX_VALUE },
            maximumBytes = 32L,
            reserveBytes = 0L,
            minimumPlausibleBytes = 8L,
        )

        val result = coordinator.download("https://example.test/model", target)

        assertEquals(ModelDownloadFailure.ATOMIC_ADOPTION, (result as ModelDownloadResult.Failure).kind)
        assertArrayEquals(previous, target.readBytes())
        assertFalse(temp.root.resolve(".model.litertlm.part").exists())
    }

    @Test
    fun `HTML payload is rejected without replacing prior model`() = runBlocking {
        val previous = byteArrayOf(1, 2, 3)
        val target = temp.root.resolve("model.litertlm").apply { writeBytes(previous) }
        val html = "<!doctype html><html>not a model</html>".toByteArray()
        val result = coordinator(FakeConnection(body = html, declaredLength = html.size.toLong()))
            .download("https://example.test/model", target)

        assertEquals(ModelDownloadFailure.INVALID_PAYLOAD, (result as ModelDownloadResult.Failure).kind)
        assertArrayEquals(previous, target.readBytes())
    }

    @Test
    fun `cancellation is rethrown disconnects and cleans partial file`() = runBlocking {
        val firstRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val connection = FakeConnection(
            body = ByteArray(64) { 2 },
            declaredLength = -1L,
            inputOverride = object : InputStream() {
                private var reads = 0
                override fun read(): Int = error("unused")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (reads++ > 0) return -1
                    firstRead.countDown()
                    assertTrue(releaseRead.await(5, TimeUnit.SECONDS))
                    buffer[offset] = 2
                    return 1
                }
            },
        )
        val target = temp.root.resolve("model.litertlm")
        val task = async(Dispatchers.Default) {
            coordinator(connection).download("https://example.test/model", target)
        }
        assertTrue(firstRead.await(5, TimeUnit.SECONDS))
        task.cancel(CancellationException("test cancellation"))
        releaseRead.countDown()
        task.cancelAndJoin()

        assertTrue(task.isCancelled)
        assertTrue(connection.disconnected)
        assertFalse(temp.root.resolve(".model.litertlm.part").exists())
        assertFalse(target.exists())
    }

    private fun coordinator(
        connection: FakeConnection,
        maximumBytes: Long = 1024L,
        usableBytes: Long = Long.MAX_VALUE,
        reserveBytes: Long = 0L,
    ) = ModelDownloadCoordinator(
        connectionFactory = ModelConnectionFactory { connection },
        usableSpace = { usableBytes },
        maximumBytes = maximumBytes,
        reserveBytes = reserveBytes,
        minimumPlausibleBytes = 8L,
    )

    private open class FakeConnection(
        url: URL = URL("https://example.test/model"),
        private val code: Int = 200,
        private val body: ByteArray = ByteArray(16) { 1 },
        private val error: ByteArray? = null,
        private val declaredLength: Long = body.size.toLong(),
        private val inputOverride: InputStream? = null,
    ) : HttpURLConnection(url) {
        var disconnected = false

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            disconnected = true
            connected = false
        }

        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = declaredLength
        override fun getContentType(): String = "application/octet-stream"
        override fun getInputStream(): InputStream = inputOverride ?: ByteArrayInputStream(body)
        override fun getErrorStream(): InputStream? = error?.inputStream()
    }
}
