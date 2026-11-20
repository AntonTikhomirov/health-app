package com.tikhomirov.zdorovyak.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tikhomirov.zdorovyak.data.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Озвучка ответов коуча. Два пути:
 * - device: системный TextToSpeech (предпочитаем движок Google), бесплатно, сетевые голоса Google звучат заметно живее офлайновых;
 * - yandex / google: облачный нейросинтез, текст режется на куски, следующий кусок качается, пока играет текущий.
 */
class Speaker(private val ctx: Context, private val settings: Settings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null

    /** id сообщения, которое сейчас звучит (null — тишина). */
    private val _speaking = MutableStateFlow<Long?>(null)
    val speaking: StateFlow<Long?> = _speaking

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun clearError() { _error.value = null }

    fun speak(text: String, id: Long = -1L) {
        stop()
        val clean = TtsText.clean(text)
        if (clean.isBlank() || settings.ttsEngine == "off") return
        _speaking.value = id
        job = scope.launch {
            runCatching {
                when (val engine = settings.ttsEngine) {
                    "device" -> speakDevice(clean)
                    else -> speakCloud(engine, clean)
                }
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) _error.value = it.message ?: "Ошибка озвучки" }
            if (_speaking.value == id) _speaking.value = null
        }
    }

    fun stop() {
        job?.cancel(); job = null
        player?.runCatching { stop(); release() }; player = null
        tts?.stop()
        _speaking.value = null
    }

    // ---------- Голос телефона ----------

    private suspend fun ensureTts(): TextToSpeech {
        tts?.let { if (ttsReady?.await() == true) return it }
        val ready = CompletableDeferred<Boolean>()
        ttsReady = ready
        val engine = GOOGLE_TTS.takeIf { runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess }
        val t = TextToSpeech(ctx, { status -> ready.complete(status == TextToSpeech.SUCCESS) }, engine)
        tts = t
        if (!ready.await()) { tts = null; throw IllegalStateException("Синтез речи на телефоне недоступен. Установите «Распознавание и синтез речи Google» из Play Маркета.") }
        return t
    }

    /** Русские голоса движка: имя → подпись для настроек. Сетевые (network) звучат лучше, но нужен интернет. */
    suspend fun deviceVoices(): List<Pair<String, String>> {
        val t = ensureTts()
        return t.voices.orEmpty().filter { it.locale.language == "ru" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
            .map { v -> v.name to "${v.name.removePrefix("ru-ru-x-").removePrefix("ru-RU-")} · ${if (v.isNetworkConnectionRequired) "сеть" else "офлайн"}" }
    }

    private suspend fun speakDevice(text: String) {
        val t = ensureTts()
        val res = t.setLanguage(Locale.forLanguageTag("ru-RU"))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw IllegalStateException("Нет русского голоса в синтезе речи телефона: Настройки Android → Синтез речи → Google → установить русский")
        }
        val wanted = settings.deviceVoice
        val voice = t.voices.orEmpty().firstOrNull { it.name == wanted }
            ?: t.voices.orEmpty().filter { it.locale.language == "ru" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .maxWithOrNull(compareBy<Voice> { it.quality }.thenBy { if (it.isNetworkConnectionRequired) 1 else 0 })
        voice?.let { t.voice = it }
        t.setSpeechRate(settings.ttsSpeed)
        val parts = TtsText.chunks(text, first = 3000, max = minOf(3000, TextToSpeech.getMaxSpeechInputLength() - 1))
        val done = CompletableDeferred<Unit>()
        val lastId = "z${parts.size - 1}"
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == lastId) done.complete(Unit) }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { done.complete(Unit) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { done.complete(Unit) }
        })
        parts.forEachIndexed { i, p -> t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "z$i") }
        try { done.await() } finally { if (!done.isCompleted) t.stop() }
    }

    // ---------- Облако ----------

    private fun cloud(engine: String): CloudTts = when (engine) {
        "yandex" -> YandexTts(settings.yandexKey, settings.yandexVoice)
        "google" -> GoogleCloudTts(settings.googleTtsKey, settings.googleVoice)
        else -> throw IllegalStateException("Неизвестный движок $engine")
    }

    private suspend fun speakCloud(engine: String, text: String) {
        val api = cloud(engine)
        val speed = settings.ttsSpeed
        val dir = File(ctx.cacheDir, "tts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val parts = TtsText.chunks(text)
        val loads = parts.mapIndexed { i, p ->
            scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val bytes = api.synthesize(p, speed)
                settings.addCloudChars(engine, p.length)
                File(dir, "p$i.ogg").apply { writeBytes(bytes) }
            }
        }
        try {
            for (i in loads.indices) {
                loads[i].start(); loads.getOrNull(i + 1)?.start()
                play(loads[i].await())
            }
        } finally { loads.forEach { it.cancel() } }
    }

    private suspend fun play(file: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { it.release(); if (player === it) player = null; if (cont.isActive) cont.resume(Unit) }
            mp.setOnErrorListener { p, _, _ -> p.release(); if (player === p) player = null; if (cont.isActive) cont.resume(Unit); true }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            cont.invokeOnCancellation { runCatching { mp.stop(); mp.release() } }
        }
    }

    companion object { const val GOOGLE_TTS = "com.google.android.tts" }
}
