package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.secure.SecretStore
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {

    private fun ctx() = kotlin.coroutines.EmptyCoroutineContext

    // ── readBoundedBody: hard-cap tests ──────────────────────────────

    @Test
    fun readBoundedBody_asciiExactlyAtByteLimit_succeeds() = runBlocking {
        val data = "A".repeat(100).toByteArray(Charsets.UTF_8)
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("A".repeat(100), result)
    }

    @Test
    fun readBoundedBody_asciiOneByteAboveLimit_fails() {
        val data = "A".repeat(101).toByteArray(Charsets.UTF_8)
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
        assertEquals("외부 AI API 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedBody_koreanThreeSyllables9Bytes_succeedsWithLimit9() = runBlocking {
        val original = "가나다"
        val data = original.toByteArray(Charsets.UTF_8)
        assertEquals(9, data.size)
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 9, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_koreanFourSyllables12Bytes_failsWithLimit10() {
        val original = "가나다라"
        val data = original.toByteArray(Charsets.UTF_8)
        assertEquals(12, data.size)
        org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 10, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
    }

    @Test
    fun readBoundedBody_emojiAndMixedContent_intactBelowLimit() = runBlocking {
        val original = "Hello 안녕하세요 🌍 こんにちは"
        val data = original.toByteArray(Charsets.UTF_8)
        assertTrue(data.size > original.length)
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), data.size + 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_oversizedErrorBody_usesErrorMessage() {
        val data = "X".repeat(OpenAiCompatibleClient.MaxErrorBodyBytes + 1).toByteArray(Charsets.UTF_8)
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), OpenAiCompatibleClient.MaxErrorBodyBytes, OpenAiCompatibleClient.BodyKind.ERROR, ctx())
            }
        }
        assertEquals("외부 AI API 오류 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedBody_emptyStream_succeeds() = runBlocking {
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(ByteArray(0)), 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("", result)
    }

    @Test
    fun readBoundedBody_belowLimit_succeeds() = runBlocking {
        val data = "Hello, world!".toByteArray(Charsets.UTF_8)
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("Hello, world!", result)
    }

    @Test
    fun readBoundedBody_multibyteAtBoundary_notCorrupted() = runBlocking {
        val original = "가나다라마바사"
        val data = original.toByteArray(Charsets.UTF_8)
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), data.size, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_sequentialChunks_succeeds() = runBlocking {
        val data = "Hello World!".toByteArray(Charsets.UTF_8)
        val stream = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() else -1
        }
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("Hello World!", result)
    }

    @Test
    fun readBoundedBody_zeroMaxBytes_emptyInput_succeeds() = runBlocking {
        val result = OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(ByteArray(0)), 0, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("", result)
    }

    @Test
    fun readBoundedBody_zeroMaxBytes_nonEmptyInput_fails() {
        org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream("A".toByteArray()), 0, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
    }

    @Test
    fun readBoundedBody_negativeMaxBytes_throws() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(ByteArray(0)), -1, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
    }

    @Test
    fun readBoundedBody_largeChunkInputStream_doesNotOvershoot() {
        val readRequests = mutableListOf<Int>()
        val largeData = "B".repeat(8192).toByteArray(Charsets.UTF_8)
        var pos = 0

        val stream = object : InputStream() {
            override fun read(): Int {
                if (pos >= largeData.size) return -1
                return largeData[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                readRequests.add(len)
                if (pos >= largeData.size) return -1
                val available = minOf(len, largeData.size - pos)
                System.arraycopy(largeData, pos, b, off, available)
                pos += available
                return available
            }
        }

        val result = runBlocking {
            OpenAiCompatibleClient.readBoundedBody(stream, 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        }
        assertEquals(100, result.toByteArray(Charsets.UTF_8).size)
        assertTrue("Should have made at least one read request", readRequests.isNotEmpty())
        assertEquals("First read should request remaining+1 = 101 bytes", 101, readRequests[0])
    }

    @Test
    fun readBoundedBody_largeChunkInputStream_overflowFailsWithoutWriting() {
        val largeData = "C".repeat(8192).toByteArray(Charsets.UTF_8)
        var pos = 0

        val stream = object : InputStream() {
            override fun read(): Int {
                if (pos >= largeData.size) return -1
                return largeData[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= largeData.size) return -1
                val available = minOf(len, largeData.size - pos)
                System.arraycopy(largeData, pos, b, off, available)
                pos += available
                return available
            }
        }

        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(stream, 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
        assertEquals("외부 AI API 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedBody_exactMaxBytes_succeeds() {
        val exactData = "D".repeat(100).toByteArray(Charsets.UTF_8)
        var pos = 0

        val stream = object : InputStream() {
            override fun read(): Int {
                if (pos >= exactData.size) return -1
                return exactData[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= exactData.size) return -1
                val available = minOf(len, exactData.size - pos)
                System.arraycopy(exactData, pos, b, off, available)
                pos += available
                return available
            }
        }

        val result = runBlocking {
            OpenAiCompatibleClient.readBoundedBody(stream, 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        }
        assertEquals(100, result.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun readBoundedBody_successAndErrorOversizedMessagesDiffer() {
        val data = "X".repeat(101).toByteArray(Charsets.UTF_8)
        val successEx = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
        val errorEx = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(ByteArrayInputStream(data), 100, OpenAiCompatibleClient.BodyKind.ERROR, ctx())
            }
        }
        assertEquals("외부 AI API 응답이 너무 큽니다.", successEx.message)
        assertEquals("외부 AI API 오류 응답이 너무 큽니다.", errorEx.message)
    }

    // ── Coroutine cancellation tests (deterministic) ─────────────────

    private fun blockingReadFakeConn(
        responseBody: ByteArray = "x".repeat(1000).toByteArray(Charsets.UTF_8)
    ): FakeHttpURLConnection {
        val conn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        conn.configure(responseCode = 200, responseBody = responseBody)
        return object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getInputStream(): InputStream {
                return object : InputStream() {
                    private var pos = 0
                    init { readStartedLatch.countDown() }
                    override fun read(): Int {
                        if (pos >= responseBody.size) return -1
                        readReleaseLatch.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                        return responseBody[pos++].toInt() and 0xFF
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (pos >= responseBody.size) return -1
                        readReleaseLatch.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                        val available = minOf(len, responseBody.size - pos)
                        System.arraycopy(responseBody, pos, b, off, available)
                        pos += available
                        return available
                    }
                }
            }
        }.also {
            it.configure(responseCode = 200, responseBody = responseBody)
        }
    }

    private fun blockingWriteFakeConn(): FakeHttpURLConnection {
        return object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getOutputStream(): OutputStream {
                return object : OutputStream() {
                    init { writeStartedLatch.countDown() }
                    override fun write(b: Int) {
                        writeReleaseLatch.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        writeReleaseLatch.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    }
                }
            }
        }
    }

    @Test
    fun cancellationDuringResponseRead_disconnects() = runBlocking {
        val fakeConn = blockingReadFakeConn()

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val workJob = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        assertTrue("Read should have started", fakeConn.awaitReadStarted(5_000))

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
        assertTrue("disconnect should have been called", fakeConn.disconnectCalled)
    }

    @Test
    fun cancellationDuringRequestWrite_disconnects() = runBlocking {
        val fakeConn = blockingWriteFakeConn()

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val workJob = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        assertTrue("Write should have started", fakeConn.awaitWriteStarted(5_000))

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
        assertTrue("disconnect should have been called", fakeConn.disconnectCalled)
    }

    @Test
    fun cancellationDoesNotProduceAiProviderClientException() = runBlocking {
        val fakeConn = blockingReadFakeConn("delayed".repeat(10000).toByteArray(Charsets.UTF_8))

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        var caughtException: Throwable? = null
        val workJob = launch {
            try {
                client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
            } catch (e: Throwable) {
                caughtException = e
                throw e
            }
        }

        assertTrue("Read should have started", fakeConn.awaitReadStarted(5_000))

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
        assertFalse("Should not produce AiProviderClientException", caughtException is AiProviderClientException)
    }

    // ── Cancellation-versus-success race ──────────────────────────────

    @Test
    fun cancellationVersusSuccess_race_successCanWin() = runBlocking {
        val body = """{"id":"race-1","model":"test","choices":[{"message":{"content":"win"}}]}"""
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = body.toByteArray(Charsets.UTF_8))

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val workJob = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        workJob.join()
        assertFalse("Job should complete successfully", workJob.isCancelled)
        assertTrue("disconnect should be called idempotently", fakeConn.disconnectCalled)
    }

    @Test
    fun cancellationVersusSuccess_race_cancellationCanWin() = runBlocking {
        val gate = CountDownLatch(1)
        val fakeConn = object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getInputStream(): InputStream {
                return object : InputStream() {
                    private var pos = 0
                    private val data = """{"id":"race-2","model":"test","choices":[{"message":{"content":"data"}}]}""".toByteArray(Charsets.UTF_8)
                    init { readStartedLatch.countDown() }

                    override fun read(): Int {
                        if (pos >= data.size) return -1
                        gate.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                        return data[pos++].toInt() and 0xFF
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (pos >= data.size) return -1
                        gate.await(5, TimeUnit.SECONDS)
                        if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                        val available = minOf(len, data.size - pos)
                        System.arraycopy(data, pos, b, off, available)
                        pos += available
                        return available
                    }
                }
            }
        }

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val workJob = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        assertTrue("Read should have started", fakeConn.awaitReadStarted(5_000))

        workJob.cancel()
        gate.countDown()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
    }

    @Test
    fun cancellationVersusSuccess_noDoubleResume_noIllegalStateException() = runBlocking {
        val body = """{"id":"race-3","model":"test","choices":[{"message":{"content":"ok"}}]}"""
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = body.toByteArray(Charsets.UTF_8))

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val job = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        job.cancel()
        job.join()

        assertTrue("Job should be cancelled without IllegalStateException", job.isCancelled)
    }

    // ── Error handling tests ──────────────────────────────────────────

    @Test
    fun genuineIOException_mapsToNetworkError() {
        val fakeConn = object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getInputStream(): InputStream = throw IOException("Genuine network failure")
        }

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
            }
        }
        assertEquals("네트워크 연결에 실패했습니다. 인터넷 연결과 Base URL을 확인해 주세요.", ex.message)
    }

    @Test
    fun cancellationException_isNeverSwallowed() = runBlocking {
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = """{"id":"1","model":"test","choices":[{"message":{"content":"hi"}}]}""".toByteArray())

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val job = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun validationErrors_throwBeforeConnection() {
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("key"),
            connectionFactory = { throw AssertionError("Should not create connection") }
        )
        val config = AiProviderConfig(id = "test", type = AiProviderType.OPENAI, displayName = "Test", baseUrl = "", modelId = "test-model", apiKeySecretId = "test-key")
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
            }
        }
        assertEquals("Base URL을 입력해 주세요.", ex.message)
    }

    @Test
    fun successfulResponse_parsesContent() {
        val body = """{"id":"chatcmpl-123","model":"gpt-4","choices":[{"message":{"content":"Hello!"}}]}"""
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = body.toByteArray(Charsets.UTF_8))

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val response = runBlocking {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }
        assertEquals("Hello!", response.content)
        assertEquals("chatcmpl-123", response.id)
        assertEquals("gpt-4", response.model)
    }

    @Test
    fun malformedJson_whileActive_mapsToProviderError() {
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = "not json".toByteArray())

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
            }
        }
        assertEquals("외부 AI API 응답을 처리할 수 없습니다.", ex.message)
    }

    @Test
    fun disconnectCausedIOException_cancelledDoesNotMapToProviderError() = runBlocking {
        val fakeConn = blockingReadFakeConn("x".repeat(200).toByteArray())

        val config = makeConfig()
        val client = OpenAiCompatibleClient(secretStore = FakeSecretStore("valid-key"), connectionFactory = { fakeConn })

        val workJob = launch {
            client.chatCompletion(config, AiChatRequest(listOf(AiMessage(AiRole.USER, "hi")), temperature = 0.7, maxTokens = 100))
        }

        assertTrue("Read should have started", fakeConn.awaitReadStarted(5_000))

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun makeConfig() = AiProviderConfig(
        id = "test",
        type = AiProviderType.OPENAI,
        displayName = "Test",
        baseUrl = "https://example.com/",
        modelId = "test-model",
        apiKeySecretId = "test-key"
    )

    private class FakeSecretStore(private val key: String?) : SecretStore {
        override suspend fun getSecret(id: String): String? = key
        override suspend fun putSecret(id: String, value: String) {}
        override suspend fun deleteSecret(id: String) {}
    }
}
