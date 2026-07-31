package com.projectnuke.fusion.search

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedHttpClientTest {
    @Test
    fun `declared oversized success response is rejected and disconnected`() = runBlocking {
        val connection = FakeConnection(
            url = URL("https://example.test/search"),
            body = ByteArray(1),
            declaredLength = 33,
        )
        val client = client(connection, successCap = 32)

        val failure = runCatching {
            client.execute(BoundedHttpRequest("https://example.test/search"))
        }.exceptionOrNull() as BoundedHttpException

        assertEquals(BoundedHttpFailure.RESPONSE_TOO_LARGE, failure.kind)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `unknown length success response is bounded while streaming`() = runBlocking {
        val connection = FakeConnection(
            url = URL("https://example.test/search"),
            body = ByteArray(33),
            declaredLength = -1,
        )
        val failure = runCatching {
            client(connection, successCap = 32)
                .execute(BoundedHttpRequest("https://example.test/search"))
        }.exceptionOrNull() as BoundedHttpException

        assertEquals(BoundedHttpFailure.RESPONSE_TOO_LARGE, failure.kind)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `cancellation disconnects active request and does not start fallback`() = runBlocking {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val connection = FakeConnection(
            url = URL("https://example.test/search"),
            declaredLength = -1,
            inputOverride = object : InputStream() {
                override fun read(): Int = error("unused")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    readStarted.countDown()
                    releaseRead.await(5, TimeUnit.SECONDS)
                    if (connectionDisconnected) throw java.io.IOException("disconnected")
                    return -1
                }

                var connectionDisconnected = false
            },
            onDisconnect = {
                releaseRead.countDown()
            },
        )
        var fallbackStarted = false
        val task = async(Dispatchers.Default) {
            try {
                client(connection).execute(BoundedHttpRequest("https://example.test/search"))
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
            fallbackStarted = true
        }
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        task.cancel(CancellationException("cancel search"))
        task.cancelAndJoin()

        assertTrue(connection.disconnected)
        assertFalse(fallbackStarted)
    }

    @Test
    fun `authenticated cross-host redirect is rejected before second request`() = runBlocking {
        val redirect = FakeConnection(
            url = URL("https://api.example.test/search"),
            code = 302,
            location = "https://evil.example/steal",
        )
        var opens = 0
        val client = BoundedHttpClient(
            connectionFactory = SearchConnectionFactory {
                opens++
                redirect
            },
        )

        val failure = runCatching {
            client.execute(
                BoundedHttpRequest(
                    url = "https://api.example.test/search",
                    headers = mapOf("Authorization" to "Bearer secret"),
                )
            )
        }.exceptionOrNull() as BoundedHttpException

        assertEquals(BoundedHttpFailure.AUTHENTICATED_CROSS_HOST_REDIRECT, failure.kind)
        assertEquals(1, opens)
        assertTrue(redirect.disconnected)
    }

    private fun client(
        connection: FakeConnection,
        successCap: Int = 1024,
    ) = BoundedHttpClient(
        connectionFactory = SearchConnectionFactory { connection },
        maxSuccessBytes = successCap,
        maxErrorBytes = 32,
    )

    private class FakeConnection(
        url: URL,
        private val code: Int = 200,
        private val body: ByteArray = ByteArray(0),
        private val declaredLength: Long = body.size.toLong(),
        private val location: String? = null,
        private val inputOverride: InputStream? = null,
        private val onDisconnect: () -> Unit = {},
    ) : HttpURLConnection(url) {
        var disconnected = false

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            disconnected = true
            onDisconnect()
        }

        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = declaredLength
        override fun getContentType(): String = "application/json; charset=utf-8"
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) location else null
        override fun getInputStream(): InputStream = inputOverride ?: ByteArrayInputStream(body)
        override fun getErrorStream(): InputStream? = ByteArrayInputStream(body)
    }
}
