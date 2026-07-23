package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiChatResponse
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.secure.SecretStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal interface ChatClient {
    suspend fun chatCompletion(config: AiProviderConfig, request: AiChatRequest): AiChatResponse
}

class OpenAiCompatibleClient(
    private val secretStore: SecretStore,
    private val connectionFactory: ((URL) -> HttpURLConnection)? = null
) : ChatClient {

    override suspend fun chatCompletion(
        config: AiProviderConfig,
        request: AiChatRequest
    ): AiChatResponse {
        val normalizedBaseUrl = normalizeBaseUrl(config.baseUrl)
        if (normalizedBaseUrl.isBlank()) {
            throw AiProviderClientException("Base URL을 입력해 주세요.")
        }
        if (config.modelId.isBlank()) {
            throw AiProviderClientException("모델 ID를 입력해 주세요.")
        }

        val secretId = config.apiKeySecretId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderClientException("API 키를 입력해 주세요.")
        val apiKey = secretStore.getSecret(secretId)?.takeIf { it.isNotBlank() }
            ?: throw AiProviderClientException("API 키를 입력해 주세요.")
        val endpoint = "${normalizedBaseUrl}chat/completions"

        return withContext(Dispatchers.IO) {
            val connection = createConnection(endpoint, apiKey)
            val ctx = coroutineContext
            suspendCancellableCoroutine { continuation ->
                val cancellationHandler = Runnable { connection.disconnect() }
                continuation.invokeOnCancellation { cancellationHandler.run() }

                try {
                    ctx.ensureActive()

                    val body = buildRequestJson(config, request).toString()
                    ctx.ensureActive()

                    connection.outputStream.use { output ->
                        val bodyBytes = body.toByteArray(Charsets.UTF_8)
                        var offset = 0
                        while (offset < bodyBytes.size) {
                            ctx.ensureActive()
                            val chunkSize = minOf(WriteChunkSize, bodyBytes.size - offset)
                            output.write(bodyBytes, offset, chunkSize)
                            offset += chunkSize
                        }
                    }

                    ctx.ensureActive()
                    val status = connection.responseCode

                    val responseText = if (status in 200..299) {
                        ctx.ensureActive()
                        readBoundedBody(connection.inputStream, MaxSuccessBodyBytes, BodyKind.SUCCESS, ctx)
                    } else {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            ctx.ensureActive()
                            readBoundedBody(errorStream, MaxErrorBodyBytes, BodyKind.ERROR, ctx)
                        } else {
                            ""
                        }
                    }

                    ctx.ensureActive()
                    if (status !in 200..299) {
                        throw AiProviderClientException(buildHttpErrorMessage(status, responseText))
                    }
                    val response = parseResponse(responseText)

                    ctx.ensureActive()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    }
                } catch (e: AiProviderClientException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancellationHandler.run()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                } catch (e: SocketTimeoutException) {
                    ctx.ensureActive()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(
                            AiProviderClientException("외부 AI API 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.", e)
                        ))
                    }
                } catch (e: IOException) {
                    ctx.ensureActive()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(
                            AiProviderClientException("네트워크 연결에 실패했습니다. 인터넷 연결과 Base URL을 확인해 주세요.", e)
                        ))
                    }
                } catch (e: Exception) {
                    ctx.ensureActive()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(
                            AiProviderClientException("외부 AI API 응답을 처리할 수 없습니다.", e)
                        ))
                    }
                } finally {
                    cancellationHandler.run()
                }
            }
        }
    }

    internal fun createConnection(endpoint: String, apiKey: String): HttpURLConnection {
        val url = URL(endpoint)
        val conn = connectionFactory?.invoke(url) ?: (url.openConnection() as HttpURLConnection)
        return conn.apply {
            requestMethod = "POST"
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun buildRequestJson(config: AiProviderConfig, request: AiChatRequest): JSONObject {
        return JSONObject()
            .put("model", config.modelId)
            .put("messages", JSONArray().also { array ->
                request.messages.forEach { message ->
                    array.put(
                        JSONObject()
                            .put("role", message.role.toOpenAiRole())
                            .put("content", message.content)
                    )
                }
            })
            .put("temperature", request.temperature ?: config.temperature)
            .put("stream", false)
            .also { json ->
                (request.maxTokens ?: config.maxTokens)?.let { json.put("max_tokens", it) }
            }
    }

    private fun parseResponse(raw: String): AiChatResponse {
        val json = JSONObject(raw)
        val choices = json.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        return AiChatResponse(
            id = json.optString("id").takeIf { it.isNotBlank() },
            model = json.optString("model").takeIf { it.isNotBlank() },
            content = message?.optString("content").orEmpty()
        )
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }?.plus("/") ?: ""
    }

    private fun AiRole.toOpenAiRole(): String {
        return when (this) {
            AiRole.SYSTEM -> "system"
            AiRole.USER -> "user"
            AiRole.ASSISTANT -> "assistant"
        }
    }

    private fun buildHttpErrorMessage(status: Int, raw: String): String {
        val providerMessage = extractProviderErrorMessage(raw)
        return when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN -> "인증에 실패했습니다. API 키와 권한 설정을 확인해 주세요."
            429 -> "요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
            in 500..599 -> "외부 AI API 서버에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요."
            else -> providerMessage?.let {
                "외부 AI API 요청에 실패했습니다. $it"
            } ?: "외부 AI API 요청에 실패했습니다. 상태 코드: $status"
        }
    }

    private fun extractProviderErrorMessage(raw: String): String? {
        return runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[\\r\\n\\t]+"), " ")
            ?.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            ?.replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [redacted]")
            ?.replace(Regex("(?i)authorization\\s*:\\s*[^\\s,]+"), "Authorization: [redacted]")
            ?.take(180)
    }

    internal enum class BodyKind(val oversizedMessage: String) {
        SUCCESS("외부 AI API 응답이 너무 큽니다."),
        ERROR("외부 AI API 오류 응답이 너무 큽니다.")
    }

    internal companion object {
        const val ConnectTimeoutMillis = 30_000
        const val ReadTimeoutMillis = 120_000
        const val MaxSuccessBodyBytes = 2 * 1024 * 1024
        const val MaxErrorBodyBytes = 64 * 1024
        private const val ReadBufferSize = 8192
        private const val WriteChunkSize = 8192

        internal fun readBoundedBody(
            stream: InputStream,
            maxBytes: Int,
            bodyKind: BodyKind,
            context: CoroutineContext
        ): String {
            val buffer = ByteArray(ReadBufferSize)
            val baos = ByteArrayOutputStream(maxBytes.coerceAtMost(64 * 1024))
            var totalBytesRead = 0
            stream.use { input ->
                while (true) {
                    context.ensureActive()
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    baos.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalBytesRead > maxBytes) {
                        throw AiProviderClientException(bodyKind.oversizedMessage)
                    }
                }
            }
            return baos.toString(Charsets.UTF_8.name())
        }
    }
}

class AiProviderClientException(message: String, cause: Throwable? = null) : Exception(message, cause)
