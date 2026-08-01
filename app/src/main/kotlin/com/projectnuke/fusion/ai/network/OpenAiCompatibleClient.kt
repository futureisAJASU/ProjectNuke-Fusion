package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiChatResponse
import com.projectnuke.fusion.ai.model.AiProviderAuthMode
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

internal interface ChatClient { suspend fun chatCompletion(config: AiProviderConfig, request: AiChatRequest): AiChatResponse }

class OpenAiCompatibleClient(
    private val secretStore: SecretStore,
    private val connectionFactory: ((URL) -> HttpURLConnection)? = null,
) : ChatClient {
    @Volatile internal var onBeforeCompletion: () -> Unit = {}

    override suspend fun chatCompletion(config: AiProviderConfig, request: AiChatRequest): AiChatResponse {
        val base = normalizeBaseUrl(config.baseUrl)
        if (base.isBlank()) throw AiProviderClientException("Base URL을 입력해 주세요.")
        if (config.modelId.isBlank()) throw AiProviderClientException("모델 ID를 입력해 주세요.")
        val secret = when (config.authMode) {
            AiProviderAuthMode.NONE -> null
            else -> config.apiKeySecretId?.takeIf { it.isNotBlank() }?.let { secretStore.getSecret(it)?.takeIf(String::isNotBlank) }
                ?: throw AiProviderClientException("API 키를 입력해 주세요.")
        }
        val endpoint = if (base.endsWith("chat/completions/")) base.trimEnd('/') else "${base}chat/completions"
        return withContext(Dispatchers.IO) {
            val connection = createConnection(endpoint, config, secret)
            val ctx = coroutineContext
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { connection.disconnect() }
                try {
                    ctx.ensureActive()
                    val body = buildRequestJson(config, request).toString().toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { output ->
                        var offset = 0
                        while (offset < body.size) { ctx.ensureActive(); val size = minOf(8192, body.size - offset); output.write(body, offset, size); offset += size }
                    }
                    val status = connection.responseCode
                    val text = if (status in 200..299) readBoundedBody(connection.inputStream, MaxSuccessBodyBytes, BodyKind.SUCCESS, ctx)
                    else connection.errorStream?.let { readBoundedBody(it, MaxErrorBodyBytes, BodyKind.ERROR, ctx) }.orEmpty()
                    ctx.ensureActive()
                    if (status !in 200..299) throw AiProviderClientException(buildHttpErrorMessage(status, text))
                    val response = parseResponse(text, config.reasoningContentEnabled)
                    onBeforeCompletion()
                    continuation.tryResumeSuccess(response)
                } catch (e: AiProviderClientException) { continuation.tryResumeFailure(e)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: SocketTimeoutException) { ctx.ensureActive(); continuation.tryResumeFailure(AiProviderClientException("외부 AI API 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.", e))
                } catch (e: IOException) { ctx.ensureActive(); continuation.tryResumeFailure(AiProviderClientException("네트워크 연결에 실패했습니다. 인터넷 연결과 Base URL을 확인해 주세요.", e))
                } catch (e: Exception) { ctx.ensureActive(); continuation.tryResumeFailure(AiProviderClientException("외부 AI API 응답을 처리할 수 없습니다.", e))
                } finally { connection.disconnect() }
            }
        }
    }

    internal fun createConnection(endpoint: String, apiKey: String): HttpURLConnection =
        createConnection(endpoint, AiProviderConfig("test", com.projectnuke.fusion.ai.model.AiProviderType.CUSTOM_OPENAI_COMPATIBLE, "test", endpoint, "test", "test"), apiKey)

    internal fun createConnection(endpoint: String, config: AiProviderConfig, secret: String?): HttpURLConnection {
        val conn = connectionFactory?.invoke(URL(endpoint)) ?: (URL(endpoint).openConnection() as HttpURLConnection)
        return conn.apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 120_000; doOutput = true; instanceFollowRedirects = false
            when (config.authMode) {
                AiProviderAuthMode.BEARER_API_KEY -> secret?.let { setRequestProperty("Authorization", "Bearer $it") }
                AiProviderAuthMode.CUSTOM_HEADER -> config.authHeaderName?.takeIf(String::isNotBlank)?.let { setRequestProperty(it, secret.orEmpty()) }
                AiProviderAuthMode.NONE -> Unit
            }
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json")
        }
    }

    private fun buildRequestJson(config: AiProviderConfig, request: AiChatRequest) = JSONObject()
        .put("model", config.modelId)
        .put("messages", JSONArray().also { array -> request.messages.forEach { array.put(JSONObject().put("role", it.role.toOpenAiRole()).put("content", it.content)) } })
        .put("temperature", request.temperature ?: config.temperature).put("stream", false)
        .also { (request.maxTokens ?: config.maxTokens)?.let { value -> it.put("max_tokens", value) } }

    private fun parseResponse(raw: String, reasoningEnabled: Boolean): AiChatResponse {
        val json = JSONObject(raw); val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw AiProviderClientException("Provider response has no message")
        val content = when (val value = message.opt("content")) {
            is String -> value
            is JSONArray -> buildString { for (i in 0 until minOf(value.length(), 128)) { val part = value.optJSONObject(i) ?: continue; if (part.optString("type") == "text" || part.has("text")) append(part.optString("text")) } }
            else -> ""
        }.ifBlank { if (reasoningEnabled) message.optString("reasoning_content").ifBlank { message.optString("reasoningContent") } else "" }
        if (content.isBlank()) throw AiProviderClientException("외부 AI API 응답을 처리할 수 없습니다.")
        return AiChatResponse(json.optString("id").takeIf(String::isNotBlank), json.optString("model").takeIf(String::isNotBlank), content)
    }

    private fun normalizeBaseUrl(value: String) = value.trim().trimEnd('/').takeIf(String::isNotBlank)?.plus("/").orEmpty()
    private fun AiRole.toOpenAiRole() = when (this) { AiRole.SYSTEM -> "system"; AiRole.USER -> "user"; AiRole.ASSISTANT -> "assistant" }
    private fun buildHttpErrorMessage(status: Int, raw: String): String {
        val providerMessage = extractProviderErrorMessage(raw)
        return when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> "인증에 실패했습니다. API 키와 권한 설정을 확인해 주세요."
            429 -> "요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
            in 500..599 -> "외부 AI API 서버에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요."
            else -> providerMessage?.let { "외부 AI API 요청에 실패했습니다. $it" } ?: "외부 AI API 요청에 실패했습니다. 상태 코드: $status"
        }
    }
    private fun extractProviderErrorMessage(raw: String) = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") ?: JSONObject(raw).optString("message") }.getOrNull()?.trim()?.replace(Regex("[\\r\\n\\t]+"), " ")?.replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [redacted]")?.replace(Regex("(?i)authorization\\s*:\\s*[^\\s,]+"), "Authorization: [redacted]")?.take(180)

    internal enum class BodyKind(val oversizedMessage: String) { SUCCESS("외부 AI API 응답이 너무 큽니다."), ERROR("외부 AI API 오류 응답이 너무 큽니다.") }
    internal companion object {
        const val MaxSuccessBodyBytes = 2 * 1024 * 1024; const val MaxErrorBodyBytes = 64 * 1024
        internal fun readBoundedBody(stream: InputStream, maxBytes: Int, bodyKind: BodyKind, context: CoroutineContext): String {
            val buffer = ByteArray(8192); val output = ByteArrayOutputStream(maxBytes.coerceAtMost(64 * 1024)); var total = 0
            stream.use { input -> while (true) { context.ensureActive(); val remaining = maxBytes - total; val read = input.read(buffer, 0, if (remaining >= buffer.size) buffer.size else remaining + 1); if (read < 0) break; if (read > remaining) throw AiProviderClientException(bodyKind.oversizedMessage); output.write(buffer, 0, read); total += read } }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

@OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
private fun <T> kotlinx.coroutines.CancellableContinuation<T>.tryResumeSuccess(value: T) { tryResume(value)?.also(::completeResume) }
@OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
private fun kotlinx.coroutines.CancellableContinuation<*>.tryResumeFailure(error: Throwable) { tryResumeWithException(error)?.also(::completeResume) }
class AiProviderClientException(message: String, cause: Throwable? = null) : Exception(message, cause)
