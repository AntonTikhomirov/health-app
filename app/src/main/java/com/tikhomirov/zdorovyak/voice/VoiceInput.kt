package com.tikhomirov.zdorovyak.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Диктовка по-русски через системный SpeechRecognizer. На Xiaomi/POCO служба по умолчанию бывает своя и нерабочая,
 * поэтому явно берём распознавание Google, если оно установлено.
 */
class VoiceInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null

    /** Есть ли хоть какая-то служба распознавания (иначе — системное окно Google через Intent). */
    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    private fun googleComponent(): ComponentName? {
        val services = ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val preferred = listOf("com.google.android.googlequicksearchbox", "com.google.android.tts", "com.google.android.as")
        return preferred.firstNotNullOfOrNull { pkg -> services.firstOrNull { it.serviceInfo.packageName == pkg } }
            ?.let { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    }

    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit) {
        stop()
        val r = googleComponent()?.let { SpeechRecognizer.createSpeechRecognizer(ctx, it) } ?: SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onFinal(text); onEnd()
            }
            override fun onError(error: Int) {
                onError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышал, попробуйте ещё раз"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет сети для распознавания речи"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание занято, попробуйте через секунду"
                    else -> "Ошибка распознавания ($error)"
                })
                onEnd()
            }
        })
        r.startListening(intent())
    }

    fun stop() { rec?.runCatching { stopListening(); destroy() }; rec = null }

    /** Мягкая остановка: дослушать и выдать результат. */
    fun finish() { rec?.stopListening() }

    companion object {
        fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
        }
    }
}
