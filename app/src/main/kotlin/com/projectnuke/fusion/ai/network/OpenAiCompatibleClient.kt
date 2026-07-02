package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiChatResponse
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.secure.SecretStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiCompatibleClient(
    private val secretStore: SecretStore
) {
    suspend fun chatCompletion(
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
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ConnectTimeoutMillis
                readTimeout = ReadTimeoutMillis
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val body = buildRequestJson(config, request).toString()
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val responseText = if (status in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }
                if (status !in 200..299) {
                    throw AiProviderClientException(buildHttpErrorMessage(status, responseText))
                }
                parseResponse(responseText)
            } catch (e: AiProviderClientException) {
                throw e
            } catch (_: SocketTimeoutException) {
                throw AiProviderClientException("외부 AI API 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.")
            } catch (_: IOException) {
                throw AiProviderClientException("네트워크 연결에 실패했습니다. 인터넷 연결과 Base URL을 확인해 주세요.")
            } catch (_: Exception) {
                throw AiProviderClientException("외부 AI API 응답을 처리할 수 없습니다.")
            } finally {
                connection.disconnect()
            }
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
            ?.replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [redacted]")
            ?.replace(Regex("(?i)authorization\\s*:\\s*[^\\s,]+"), "Authorization: [redacted]")
            ?.take(180)
    }

    private companion object {
        const val ConnectTimeoutMillis = 30_000
        const val ReadTimeoutMillis = 120_000
    }
}

class AiProviderClientException(message: String) : Exception(message)
