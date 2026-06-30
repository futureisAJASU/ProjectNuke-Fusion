package com.projectnuke.fusion.ai.network

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiChatResponse
import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.secure.SecretStore
import java.io.IOException
import java.net.HttpURLConnection
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
        val secretId = config.apiKeySecretId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderClientException("API 키가 저장되어 있지 않습니다.")
        val apiKey = secretStore.getSecret(secretId)?.takeIf { it.isNotBlank() }
            ?: throw AiProviderClientException("API 키가 저장되어 있지 않습니다.")
        val endpoint = chatCompletionsUrl(config.baseUrl)

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
                    throw AiProviderClientException(safeHttpError(status, responseText))
                }
                parseResponse(responseText)
            } catch (e: AiProviderClientException) {
                throw e
            } catch (_: IOException) {
                throw AiProviderClientException("API 제공자에 연결할 수 없습니다.")
            } catch (_: Exception) {
                throw AiProviderClientException("응답을 처리할 수 없습니다.")
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

    private fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        if (normalized.isBlank()) throw AiProviderClientException("Base URL을 입력해 주세요.")
        return "${normalized}chat/completions"
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/') + "/"
    }

    private fun AiRole.toOpenAiRole(): String {
        return when (this) {
            AiRole.SYSTEM -> "system"
            AiRole.USER -> "user"
            AiRole.ASSISTANT -> "assistant"
        }
    }

    private fun safeHttpError(status: Int, raw: String): String {
        val message = runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (message.isNullOrBlank()) {
            "API 요청이 실패했습니다. 상태 코드: $status"
        } else {
            "API 요청이 실패했습니다. 상태 코드: $status, 메시지: ${message.take(180)}"
        }
    }

    private companion object {
        const val ConnectTimeoutMillis = 30_000
        const val ReadTimeoutMillis = 120_000
    }
}

class AiProviderClientException(message: String) : Exception(message)
