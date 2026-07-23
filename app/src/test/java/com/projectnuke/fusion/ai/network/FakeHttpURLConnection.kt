package com.projectnuke.fusion.ai.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

open class FakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
    private var fakeResponseCode: Int = 200
    private var fakeResponseBody: ByteArray = ByteArray(0)
    private var fakeErrorBody: ByteArray? = null

    private var _disconnected = false
    val disconnectCalled: Boolean get() = _disconnected

    private val outputStream = ByteArrayOutputStream()

    fun configure(
        responseCode: Int = 200,
        responseBody: ByteArray = ByteArray(0),
        errorBody: ByteArray? = null
    ) {
        fakeResponseCode = responseCode
        fakeResponseBody = responseBody
        fakeErrorBody = errorBody
    }

    override fun disconnect() {
        _disconnected = true
        connected = false
    }

    override fun usingProxy(): Boolean = false

    override fun connect() {
        connected = true
    }

    override fun getOutputStream(): OutputStream = outputStream

    override fun getResponseCode(): Int = fakeResponseCode

    override fun getInputStream(): InputStream {
        if (fakeResponseCode !in 200..299) {
            throw IOException("No input stream for error response")
        }
        return ByteArrayInputStream(fakeResponseBody)
    }

    override fun getErrorStream(): InputStream? {
        return fakeErrorBody?.let { ByteArrayInputStream(it) }
    }
}
