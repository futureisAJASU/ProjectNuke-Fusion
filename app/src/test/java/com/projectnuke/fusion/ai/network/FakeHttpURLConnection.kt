package com.projectnuke.fusion.ai.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

open class FakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
    @Volatile
    private var fakeResponseCode = 200
    private var fakeResponseBody = ByteArray(0)
    private var fakeErrorBody: ByteArray? = null

    internal val disconnectCount = AtomicInteger(0)
    val disconnectCalled: Boolean get() = disconnectCount.get() > 0
    val disconnectCountValue: Int get() = disconnectCount.get()

    internal val readStartedLatch = CountDownLatch(1)
    internal val readReleaseLatch = CountDownLatch(1)
    internal val writeStartedLatch = CountDownLatch(1)
    internal val writeReleaseLatch = CountDownLatch(1)

    private val sentBytes = ByteArrayOutputStream()

    @Volatile
    var blockReads = false
    @Volatile
    var blockWrites = false

    fun configure(
        responseCode: Int = 200,
        responseBody: ByteArray = ByteArray(0),
        errorBody: ByteArray? = null
    ) {
        fakeResponseCode = responseCode
        fakeResponseBody = responseBody
        fakeErrorBody = errorBody
    }

    fun awaitReadStarted(timeoutMs: Long = 5_000): Boolean =
        readStartedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun awaitWriteStarted(timeoutMs: Long = 5_000): Boolean =
        writeStartedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun releaseRead() {
        readReleaseLatch.countDown()
    }

    fun releaseWrite() {
        writeReleaseLatch.countDown()
    }

    override fun disconnect() {
        disconnectCount.incrementAndGet()
        connected = false
        readReleaseLatch.countDown()
        writeReleaseLatch.countDown()
    }

    override fun usingProxy(): Boolean = false

    override fun connect() {
        connected = true
    }

    override fun getOutputStream(): OutputStream {
        if (blockWrites) {
            writeStartedLatch.countDown()
            return object : OutputStream() {
                override fun write(b: Int) {
                    writeReleaseLatch.await(5, TimeUnit.SECONDS)
                    if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    sentBytes.write(b)
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    writeReleaseLatch.await(5, TimeUnit.SECONDS)
                    if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    sentBytes.write(b, off, len)
                }
            }
        }
        return sentBytes
    }

    override fun getResponseCode(): Int = fakeResponseCode

    override fun getInputStream(): InputStream {
        if (fakeResponseCode !in 200..299) {
            throw IOException("No input stream for error response")
        }
        if (blockReads) {
            readStartedLatch.countDown()
            return object : InputStream() {
                private var pos = 0
                override fun read(): Int {
                    if (pos >= fakeResponseBody.size) return -1
                    readReleaseLatch.await(5, TimeUnit.SECONDS)
                    if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    return fakeResponseBody[pos++].toInt() and 0xFF
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (pos >= fakeResponseBody.size) return -1
                    readReleaseLatch.await(5, TimeUnit.SECONDS)
                    if (disconnectCount.get() > 0) throw IOException("Connection disconnected")
                    val available = minOf(len, fakeResponseBody.size - pos)
                    System.arraycopy(fakeResponseBody, pos, b, off, available)
                    pos += available
                    return available
                }
            }
        }
        return ByteArrayInputStream(fakeResponseBody)
    }

    override fun getErrorStream(): InputStream? {
        return fakeErrorBody?.let { ByteArrayInputStream(it) }
    }

    fun sentBytes(): ByteArray = sentBytes.toByteArray()
}
