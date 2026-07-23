package com.projectnuke.fusion.ai.network

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {

    @Test
    fun readBoundedUtf8_belowLimit_succeeds() {
        val data = "Hello, world!".toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 1024)
        assertEquals("Hello, world!", result)
    }

    @Test
    fun readBoundedUtf8_exactlyAtLimit_succeeds() {
        val data = "A".repeat(100).toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 100)
        assertEquals("A".repeat(100), result)
    }

    @Test
    fun readBoundedUtf8_aboveLimit_throws() {
        val data = "A".repeat(200).toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val ex = assertThrows(AiProviderClientException::class.java) {
            OpenAiCompatibleClient.readBoundedUtf8(stream, 100)
        }
        assertEquals("외부 AI API 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedUtf8_errorBodyCap_throwsWithDifferentMessage() {
        val largeData = "X".repeat(OpenAiCompatibleClient.MaxErrorBodyBytes + 1)
            .toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(largeData)
        val ex = assertThrows(AiProviderClientException::class.java) {
            OpenAiCompatibleClient.readBoundedUtf8(stream, OpenAiCompatibleClient.MaxErrorBodyBytes)
        }
        assertEquals("외부 AI API 오류 응답이 너무 큽니다.", ex.message)
    }

    @Test
    fun readBoundedUtf8_multibyteContent_notCorrupted() {
        val original = "안녕하세요 세계! こんにちは世界! \uD83C\uDF0D"
        val data = original.toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 1024)
        assertEquals(original, result)
    }

    @Test
    fun readBoundedUtf8_multibyteAtBoundary_notCorrupted() {
        val original = "가나다라마바사"
        val data = original.toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, data.size)
        assertEquals(original, result)
    }

    @Test
    fun readBoundedUtf8_multibyteExceedsLimit_throws() {
        val original = "가나다라마바사아자차카타파하"
        val data = original.toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        assertThrows(AiProviderClientException::class.java) {
            OpenAiCompatibleClient.readBoundedUtf8(stream, 10)
        }
    }

    @Test
    fun readBoundedUtf8_emptyStream_returnsEmpty() {
        val stream: InputStream = ByteArrayInputStream(ByteArray(0))
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 1024)
        assertEquals("", result)
    }

    @Test
    fun readBoundedUtf8_cancellation_propagates() {
        val slowStream = object : InputStream() {
            private var pos = 0
            override fun read(): Int {
                if (pos++ == 0) {
                    throw CancellationException("test cancellation")
                }
                return -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos == 0) {
                    pos++
                    throw CancellationException("test cancellation")
                }
                return -1
            }
        }
        assertThrows(CancellationException::class.java) {
            OpenAiCompatibleClient.readBoundedUtf8(slowStream, 1024)
        }
    }

    @Test
    fun readBoundedUtf8_sequentialChunks_succeeds() {
        val part1 = "Hello "
        val part2 = "World!"
        val data = (part1 + part2).toByteArray(Charsets.UTF_8)
        val stream: InputStream = object : InputStream() {
            private var pos = 0
            override fun read(): Int {
                return if (pos < data.size) data[pos++].toInt() else -1
            }
        }
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 1024)
        assertEquals("Hello World!", result)
    }

    @Test
    fun readBoundedUtf8_bufferedReaderChunking_works() {
        val data = "ABCDEFGHIJ".repeat(100).toByteArray(Charsets.UTF_8)
        val stream: InputStream = ByteArrayInputStream(data)
        val result = OpenAiCompatibleClient.readBoundedUtf8(stream, 2048)
        assertEquals("ABCDEFGHIJ".repeat(100), result)
        assertEquals(1000, result.length)
    }
}
