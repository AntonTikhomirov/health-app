package com.tikhomirov.zdorovyak.voice

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Облачный синтез: текст → аудио OGG/Opus (MediaPlayer играет его с Android 10). */
interface CloudTts {
    fun synthesize(text: String, speed: Float): ByteArray
}

class TtsException(msg: String) : Exception(msg)

private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
private val json = Json { ignoreUnknownKeys = true }

/**
 * Yandex SpeechKit, REST API v1. Ключ — API-ключ сервисного аккаунта с ролью ai.speechkit-tts.user
 * (заголовок «Authorization: Api-Key …», folderId тогда не нужен). До 5000 символов за запрос.
 */
class YandexTts(private val key: String, private val voice: String) : CloudTts {
    override fun synthesize(text: String, speed: Float): ByteArray {
        if (key.isBlank()) throw TtsException("Не задан API-ключ Yandex SpeechKit (Настройки → Голос коуча)")
        val body = FormBody.Builder()
            .add("text", text).add("lang", "ru-RU").add("voice", voice)
            .add("speed", "%.2f".format(java.util.Locale.ROOT, speed)).add("format", "oggopus")
            .build()
        val req = Request.Builder().url("https://tts.api.cloud.yandex.net/speech/v1/tts:synthesize")
            .header("Authorization", "Api-Key $key").post(body).build()
        http.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            if (!r.isSuccessful) throw TtsException("SpeechKit ${r.code}: ${String(bytes).take(200)}")
            return bytes
        }
    }

    companion object {
        /** Голоса SpeechKit с русским (код → описание). */
        val VOICES = listOf(
            "filipp" to "Филипп, муж.", "ermil" to "Ермил, муж.", "zahar" to "Захар, муж.", "alexander" to "Александр, муж.",
            "kirill" to "Кирилл, муж.", "anton" to "Антон, муж.", "madirus" to "Мадирус, муж.",
            "alena" to "Алёна, жен.", "jane" to "Джейн, жен.", "omazh" to "Омаж, жен.", "dasha" to "Даша, жен.",
            "julia" to "Юлия, жен.", "lera" to "Лера, жен.", "marina" to "Марина, жен.", "masha" to "Маша, жен.",
        )
    }
}

/**
 * Google Cloud Text-to-Speech, REST v1 с API-ключом. Лимит 5000 байт на запрос (≈2400 символов кириллицы).
 * Бесплатно в месяц: 1 млн символов WaveNet / Chirp 3 HD, 4 млн Standard.
 */
class GoogleCloudTts(private val key: String, private val voice: String) : CloudTts {
    override fun synthesize(text: String, speed: Float): ByteArray {
        if (key.isBlank()) throw TtsException("Не задан API-ключ Google Cloud TTS (Настройки → Голос коуча)")
        val body = buildJsonObject {
            put("input", buildJsonObject { put("text", text) })
            put("voice", buildJsonObject { put("languageCode", "ru-RU"); put("name", voice) })
            put("audioConfig", buildJsonObject {
                put("audioEncoding", "OGG_OPUS")
                if (kotlin.math.abs(speed - 1f) > 0.01f) put("speakingRate", speed.toDouble())
            })
        }
        val req = Request.Builder().url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { json.parseToJsonElement(s).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }.getOrNull()
                throw TtsException("Google TTS ${r.code}: ${msg ?: s.take(200)}")
            }
            val b64 = json.parseToJsonElement(s).jsonObject["audioContent"]?.jsonPrimitive?.content
                ?: throw TtsException("Google TTS: пустой ответ")
            return Base64.decode(b64, Base64.DEFAULT)
        }
    }

    companion object {
        val VOICES = listOf(
            "ru-RU-Wavenet-D" to "WaveNet D, муж.", "ru-RU-Wavenet-B" to "WaveNet B, муж.",
            "ru-RU-Wavenet-A" to "WaveNet A, жен.", "ru-RU-Wavenet-C" to "WaveNet C, жен.",
            "ru-RU-Chirp3-HD-Charon" to "Chirp 3 HD Charon, муж.", "ru-RU-Chirp3-HD-Puck" to "Chirp 3 HD Puck, муж.",
            "ru-RU-Chirp3-HD-Aoede" to "Chirp 3 HD Aoede, жен.", "ru-RU-Chirp3-HD-Kore" to "Chirp 3 HD Kore, жен.",
        )
    }
}
