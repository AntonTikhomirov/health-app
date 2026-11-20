package com.tikhomirov.zdorovyak.ai

import com.tikhomirov.zdorovyak.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeException(msg: String) : Exception(msg)

/** Один блок содержимого сообщения. */
sealed interface Part {
    data class Text(val text: String) : Part
    data class Image(val base64Jpeg: String) : Part
}

data class Msg(val role: String, val parts: List<Part>) {
    constructor(role: String, text: String) : this(role, listOf(Part.Text(text)))
}

/** Минимальный клиент Messages API (без SDK, только OkHttp). */
class ClaudeClient(private val settings: Settings) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    val hasKey: Boolean get() = settings.claudeKey.isNotBlank()

    suspend fun complete(
        system: String,
        messages: List<Msg>,
        maxTokens: Int = 2048,
        temperature: Double? = null,
    ): String = withContext(Dispatchers.IO) {
        val key = settings.claudeKey
        if (key.isBlank()) throw ClaudeException("Не задан ключ Claude API. Откройте Настройки.")
        val body = buildJsonObject {
            put("model", settings.claudeModel)
            put("max_tokens", maxTokens)
            put("system", system)
            temperature?.let { put("temperature", it) }
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", buildJsonArray {
                            m.parts.forEach { p ->
                                when (p) {
                                    is Part.Text -> add(buildJsonObject {
                                        put("type", "text"); put("text", p.text)
                                    })
                                    is Part.Image -> add(buildJsonObject {
                                        put("type", "image")
                                        put("source", buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", "image/jpeg")
                                            put("data", p.base64Jpeg)
                                        })
                                    })
                                }
                            }
                        })
                    })
                }
            })
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw ClaudeException("Claude API ${resp.code}: ${err ?: text.take(300)}")
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val content = obj["content"]?.jsonArray ?: JsonArray(emptyList())
            content.mapNotNull { el ->
                val o = el.jsonObject
                if (o["type"]?.jsonPrimitive?.content == "text") o["text"]?.jsonPrimitive?.content else null
            }.joinToString("")
        }
    }

    /** Достаёт первый JSON-объект из ответа (модель иногда оборачивает его в ```json). */
    fun extractJson(text: String): JsonElement {
        val cleaned = text.replace("```json", "```")
        val fenced = Regex("```\\s*([\\s\\S]*?)```").find(cleaned)?.groupValues?.get(1)
        val candidate = fenced ?: run {
            val s = text.indexOfFirst { it == '{' || it == '[' }
            val e = text.indexOfLast { it == '}' || it == ']' }
            if (s >= 0 && e > s) text.substring(s, e + 1) else text
        }
        return json.parseToJsonElement(candidate.trim())
    }

    suspend fun completeJson(system: String, messages: List<Msg>, maxTokens: Int = 2048): JsonObject {
        val out = complete(system, messages, maxTokens)
        return runCatching { extractJson(out).jsonObject }
            .getOrElse { throw ClaudeException("Не удалось разобрать ответ ИИ как JSON: ${out.take(200)}") }
    }
}
