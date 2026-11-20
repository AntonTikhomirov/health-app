package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tikhomirov.zdorovyak.voice.VoiceInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch

@Composable
fun CoachScreen(modifier: Modifier) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val messages by app.db.chat().all().collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var memoryOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val ctx = LocalContext.current
    val speaking by app.speaker.speaking.collectAsState()
    val ttsError by app.speaker.error.collectAsState()
    var listening by remember { mutableStateOf(false) }
    var voicePrefix by remember { mutableStateOf("") }
    val voice = remember { VoiceInput(ctx) }
    DisposableEffect(Unit) { onDispose { voice.stop(); app.speaker.stop() } }
    LaunchedEffect(ttsError) { ttsError?.let { snack(it); app.speaker.clearError() } }

    fun send(text: String, byVoice: Boolean) {
        if (text.isBlank() || thinking) return
        input = ""; thinking = true
        app.speaker.stop()
        scope.launch {
            runCatching { app.coach.send(text) }
                .onSuccess { reply ->
                    val mode = app.settings.ttsWhen
                    if (app.settings.ttsEngine != "off" && (mode == "always" || (mode == "voice" && byVoice))) {
                        val id = app.db.chat().last(1).firstOrNull()?.id ?: -1L
                        app.speaker.speak(reply, id)
                    }
                }
                .onFailure { snack(it.message ?: "Ошибка") }
            thinking = false
        }
    }

    fun onVoiceText(text: String) {
        val full = (voicePrefix + " " + text).trim()
        if (text.isNotBlank() && app.settings.voiceAutoSend) send(full, byVoice = true) else input = full
    }

    // Запасной путь: системное окно диктовки Google, если служба распознавания недоступна напрямую
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onVoiceText(it) }
        }
    }

    fun startListening() {
        app.speaker.stop()
        voicePrefix = input.trim()
        if (!voice.available) {
            runCatching { dictation.launch(VoiceInput.intent()) }.onFailure { snack("На телефоне нет распознавания речи. Установите приложение Google.") }
            return
        }
        listening = true
        voice.start(
            onPartial = { input = (voicePrefix + " " + it).trim() },
            onFinal = { onVoiceText(it) },
            onError = { snack(it); input = voicePrefix },
            onEnd = { listening = false },
        )
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening() else snack("Без доступа к микрофону диктовка не работает")
    }

    LaunchedEffect(messages.size, thinking) { val n = list.layoutInfo.totalItemsCount; if (n > 0) list.animateScrollToItem(n - 1) }

    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { memoryOpen = true }) { Text("Что коуч помнит") }
            if (speaking != null) TextButton(onClick = { app.speaker.stop() }) { Text("Остановить голос") }
            TextButton(onClick = { confirmClear = true }) { Text("Очистить чат") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item {
                ZCard {
                    Text("Здесь можно разобрать что угодно: срыв в питании, нет сил на тренировку, тревога, плохой сон. Коуч видит ваши данные из приложения, анализы и помнит прошлые разговоры.")
                    Muted("Можно говорить голосом: кнопка микрофона справа внизу. Ответ на голосовой вопрос коуч зачитает вслух (настраивается в Настройках → Голос коуча).")
                    Muted("Это не замена психотерапевту и врачу. В кризисе: телефон доверия 150, экстренно 112.")
                }
            }
            items(messages, key = { it.id }) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Z.Lime.copy(alpha = 0.18f) else Z.Surface).padding(12.dp),
                    ) {
                        Text(m.content, fontSize = 15.sp)
                        if (!mine && app.settings.ttsEngine != "off") {
                            val playing = speaking == m.id
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { if (playing) app.speaker.stop() else app.speaker.speak(m.content, m.id) }) {
                                    Icon(if (playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        if (playing) "Остановить" else "Озвучить", tint = if (playing) Z.Lime else Z.Muted)
                                }
                            }
                        }
                    }
                }
            }
            if (thinking) item { Muted("Коуч думает…") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f),
                placeholder = { Text(if (listening) "Говорите…" else "Напишите или надиктуйте…") }, maxLines = 5)
            IconButton(enabled = !thinking, onClick = {
                if (listening) { voice.finish() }
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, if (listening) "Закончить диктовку" else "Диктовать", tint = if (listening) Z.Red else Z.Lime) }
            IconButton(enabled = input.isNotBlank() && !thinking && !listening, onClick = { send(input.trim(), byVoice = false) }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = Z.Lime)
            }
        }
    }

    if (memoryOpen) {
        var mem by remember { mutableStateOf(app.settings.coachMemory) }
        AlertDialog(
            onDismissRequest = { memoryOpen = false },
            title = { Text("Память коуча") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Muted("Обновляется автоматически каждые 10 сообщений. Можно поправить вручную.")
                    OutlinedTextField(mem, { mem = it }, Modifier.fillMaxWidth(), minLines = 6)
                }
            },
            confirmButton = { TextButton(onClick = { app.settings.coachMemory = mem; memoryOpen = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { memoryOpen = false }) { Text("Закрыть") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить переписку?") },
            text = { Text("Сообщения удалятся. Долговременная память останется (её можно поправить отдельно).") },
            confirmButton = { TextButton(onClick = { scope.launch { app.coach.clearChat() }; confirmClear = false }) { Text("Очистить") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}
