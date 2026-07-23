package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.secure.SecretStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {

    private fun ctx() = kotlin.coroutines.EmptyCoroutineContext

    // ── readBoundedBody: byte-limit tests ──────────────────────────────

    @Test
    fun readBoundedBody_asciiExactlyAtByteLimit_succeeds() = runBlocking {
        val data = "A".repeat(100).toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("A".repeat(100), result)
    }

    @Test
    fun readBoundedBody_asciiOneByteAboveLimit_fails() = runBlocking<Unit> {
        val data = "A".repeat(101).toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(stream, 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
        assertEquals("외부 AI API 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedBody_koreanThreeSyllables9Bytes_succeedsWithLimit9() = runBlocking {
        val original = "가나다"
        val data = original.toByteArray(Charsets.UTF_8)
        assertEquals(9, data.size)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 9, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_koreanFourSyllables12Bytes_failsWithLimit10() = runBlocking<Unit> {
        val original = "가나다라"
        val data = original.toByteArray(Charsets.UTF_8)
        assertEquals(12, data.size)
        val stream: InputStream = ByteArrayInputStream(data)
        org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(stream, 10, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
            }
        }
    }

    @Test
    fun readBoundedBody_emojiAndMixedContent_intactBelowLimit() = runBlocking {
        val original = "Hello 안녕하세요 🌍 こんにちは"
        val data = original.toByteArray(Charsets.UTF_8)
        assertTrue(data.size > original.length)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedBody(stream, data.size + 100, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_oversizedErrorBody_usesErrorMessage() = runBlocking {
        val data = "X".repeat(OpenAiCompatibleClient.MaxErrorBodyBytes + 1)
            .toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                OpenAiCompatibleClient.readBoundedBody(
                    stream,
                    OpenAiCompatibleClient.MaxErrorBodyBytes,
                    OpenAiCompatibleClient.BodyKind.ERROR,
                    ctx()
                )
            }
        }
        assertEquals("외부 AI API 오류 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedBody_emptyStream_succeeds() = runBlocking {
        val stream: InputStream = ByteArrayInputStream(ByteArray(0))
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("", result)
    }

    @Test
    fun readBoundedBody_belowLimit_succeeds() = runBlocking {
        val data = "Hello, world!".toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("Hello, world!", result)
    }

    @Test
    fun readBoundedBody_multibyteAtBoundary_notCorrupted() = runBlocking {
        val original = "가나다라마바사"
        val data = original.toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedBody(stream, data.size, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals(original, result)
    }

    @Test
    fun readBoundedBody_sequentialChunks_succeeds() = runBlocking {
        val data = "Hello World!".toByteArray(Charsets.UTF_8)
        val stream: InputStream = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() else -1
        }
        val result = OpenAiCompatibleClient.readBoundedBody(stream, 1024, OpenAiCompatibleClient.BodyKind.SUCCESS, ctx())
        assertEquals("Hello World!", result)
    }

    // ── Coroutine cancellation tests ──────────────────────────────────

    @Test
    fun cancellationDuringResponseRead_disconnects() = runBlocking {
        val readLatch = CountDownLatch(1)

        val fakeConn = object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getInputStream(): InputStream {
                readLatch.await(10, TimeUnit.SECONDS)
                if (disconnectCalled) throw IOException("Connection disconnected")
                return ByteArrayInputStream(ByteArray(0))
            }
        }

        val config = makeConfig()
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("valid-key"),
            connectionFactory = { fakeConn }
        )

        val workJob = launch {
            client.chatCompletion(
                config = config,
                request = AiChatRequest(
                    messages = listOf(AiMessage(AiRole.USER, "hi")),
                    temperature = 0.7,
                    maxTokens = 100
                )
            )
        }

        Thread.sleep(200)

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
        readLatch.countDown()
    }

    @Test
    fun cancellationDuringRequestWrite_disconnects() = runBlocking {
        val writeLatch = CountDownLatch(1)

        val fakeConn = object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getOutputStream(): OutputStream {
                return object : OutputStream() {
                    override fun write(b: Int) {
                        writeLatch.await(10, TimeUnit.SECONDS)
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        writeLatch.await(10, TimeUnit.SECONDS)
                    }
                }
            }
        }

        val config = makeConfig()
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("valid-key"),
            connectionFactory = { fakeConn }
        )

        val workJob = launch {
            client.chatCompletion(
                config = config,
                request = AiChatRequest(
                    messages = listOf(AiMessage(AiRole.USER, "hi")),
                    temperature = 0.7,
                    maxTokens = 100
                )
            )
        }

        Thread.sleep(100)

        workJob.cancel()
        workJob.join()

        assertTrue("Job should be cancelled", workJob.isCancelled)
        writeLatch.countDown()
    }

    @Test
    fun genuineIOException_mapsToNetworkError() = runBlocking {
        val fakeConn = object : FakeHttpURLConnection(URL("https://example.com/chat/completions")) {
            override fun getInputStream(): InputStream {
                throw IOException("Genuine network failure")
            }
        }

        val config = makeConfig()
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("valid-key"),
            connectionFactory = { fakeConn }
        )

        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                client.chatCompletion(
                    config = config,
                    request = AiChatRequest(
                        messages = listOf(AiMessage(AiRole.USER, "hi")),
                        temperature = 0.7,
                        maxTokens = 100
                    )
                )
            }
        }
        assertEquals("네트워크 연결에 실패했습니다. 인터넷 연결과 Base URL을 확인해 주세요.", ex.message)
    }

    @Test
    fun cancellationException_isNeverSwallowed() = runBlocking {
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(
            responseCode = 200,
            responseBody = """{"id":"1","model":"test","choices":[{"message":{"content":"hi"}}]}""".toByteArray()
        )

        val config = makeConfig()
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("valid-key"),
            connectionFactory = { fakeConn }
        )

        val job = launch {
            client.chatCompletion(
                config = config,
                request = AiChatRequest(
                    messages = listOf(AiMessage(AiRole.USER, "hi")),
                    temperature = 0.7,
                    maxTokens = 100
                )
            )
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun validationErrors_throwBeforeConnection() = runBlocking {
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("key"),
            connectionFactory = { throw AssertionError("Should not create connection") }
        )
        val config = AiProviderConfig(
            id = "test",
            type = AiProviderType.OPENAI,
            displayName = "Test",
            baseUrl = "",
            modelId = "test-model",
            apiKeySecretId = "test-key"
        )
        val ex = org.junit.Assert.assertThrows(AiProviderClientException::class.java) {
            runBlocking {
                client.chatCompletion(
                    config = config,
                    request = AiChatRequest(
                        messages = listOf(AiMessage(AiRole.USER, "hi")),
                        temperature = 0.7,
                        maxTokens = 100
                    )
                )
            }
        }
        assertEquals("Base URL을 입력해 주세요.", ex.message)
    }

    @Test
    fun successfulResponse_parsesContent() = runBlocking {
        val body = """{"id":"chatcmpl-123","model":"gpt-4","choices":[{"message":{"content":"Hello!"}}]}"""
        val fakeConn = FakeHttpURLConnection(URL("https://example.com/chat/completions"))
        fakeConn.configure(responseCode = 200, responseBody = body.toByteArray(Charsets.UTF_8))

        val config = makeConfig()
        val client = OpenAiCompatibleClient(
            secretStore = FakeSecretStore("valid-key"),
            connectionFactory = { fakeConn }
        )

        val response = client.chatCompletion(
            config = config,
            request = AiChatRequest(
                messages = listOf(AiMessage(AiRole.USER, "hi")),
                temperature = 0.7,
                maxTokens = 100
            )
        )
        assertEquals("Hello!", response.content)
        assertEquals("chatcmpl-123", response.id)
        assertEquals("gpt-4", response.model)
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
