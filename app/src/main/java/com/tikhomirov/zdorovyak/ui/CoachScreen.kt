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

    LaunchedEffect(messages.size, thinking) { val n = list.layoutInfo.totalItemsCount; if (n > 0) list.animateScrollToItem(n - 1) }

    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { memoryOpen = true }) { Text("Что коуч помнит") }
            TextButton(onClick = { confirmClear = true }) { Text("Очистить чат") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item {
                ZCard {
                    Text("Здесь можно разобрать что угодно: срыв в питании, нет сил на тренировку, тревога, плохой сон. Коуч видит ваши данные из приложения и помнит прошлые разговоры.")
                    Muted("Это не замена психотерапевту и врачу. В кризисе: телефон доверия 150, экстренно 112.")
                }
            }
            items(messages, key = { it.id }) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.content,
                        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Z.Lime.copy(alpha = 0.18f) else Z.Surface).padding(12.dp),
                        fontSize = 15.sp,
                    )
                }
            }
            if (thinking) item { Muted("Коуч думает…") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Напишите, что на душе…") }, maxLines = 5)
            IconButton(enabled = input.isNotBlank() && !thinking, onClick = {
                val text = input.trim(); input = ""; thinking = true
                scope.launch {
                    runCatching { app.coach.send(text) }.onFailure { snack(it.message ?: "Ошибка") }
                    thinking = false
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = Z.Lime) }
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
