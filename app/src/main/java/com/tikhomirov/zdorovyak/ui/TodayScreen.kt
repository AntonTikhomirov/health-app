package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.strength.SessionPlan
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(modifier: Modifier, onOpenTab: (Tab) -> Unit, onStartWorkout: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    val food by app.db.food().forDay(today()).collectAsState(initial = emptyList())
    val weights by app.db.body().weights().collectAsState(initial = emptyList())
    val garmin by app.db.garmin().day(today()).collectAsState(initial = null)
    val program by app.db.training().activeProgram().collectAsState(initial = null)
    var session by remember { mutableStateOf<SessionPlan?>(null) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(program) { session = app.training.nextSession(profile) }

    val greet = when (LocalTime.now().hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; else -> "Добрый вечер" }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("$greet, ${profile.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Muted(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)))
        }

        // Восстановление (Garmin)
        ZCard(accent = Z.Blue) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Восстановление", fontWeight = FontWeight.SemiBold)
                if (app.garmin.isLoggedIn) OutlinedButton(enabled = !syncing, onClick = {
                    syncing = true
                    scope.launch {
                        runCatching { app.garmin.sync(3) }.onSuccess(snack).onFailure { snack(it.message ?: "Ошибка Garmin") }
                        syncing = false
                    }
                }) { Text(if (syncing) "…" else "Обновить") }
            }
            val g = garmin
            if (g == null) Muted(if (app.garmin.isLoggedIn) "Нет данных за сегодня, нажмите «Обновить»." else "Подключите Garmin в настройках, чтобы видеть сон, Body Battery, HRV и стресс.")
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Сон", g.sleepMinutes?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "–", g.sleepScore?.let { "оценка $it" })
                Stat("Body Battery", g.bodyBatteryHigh?.toString() ?: "–", g.bodyBatteryLow?.let { "мин $it" })
                Stat("HRV", g.hrvAvg?.toString() ?: "–", g.restingHr?.let { "пульс $it" })
                Stat("Стресс", g.stressAvg?.toString() ?: "–", g.steps?.let { "$it шагов" })
            }
        }

        // Питание
        val kcal = food.sumOf { it.kcal }
        ZCard(accent = Z.Lime) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Питание", fontWeight = FontWeight.SemiBold)
                Muted("цель ${profile.kcalTarget} ккал")
            }
            Row {
                Big(kcal.r0())
                Spacer(Modifier.width(8.dp))
                Muted("ккал · осталось ${(profile.kcalTarget - kcal).r0()}", Modifier.padding(top = 12.dp))
            }
            MacroBar("Белок", food.sumOf { it.protein }, profile.proteinTarget.toDouble(), Z.Lime)
            MacroBar("Жиры", food.sumOf { it.fat }, profile.fatTarget.toDouble(), Z.Orange)
            MacroBar("Углеводы", food.sumOf { it.carbs }, profile.carbsTarget.toDouble(), Z.Blue)
            OutlinedButton(onClick = { onOpenTab(Tab.FOOD) }) { Text("Добавить еду") }
        }

        // Тренировка
        ZCard(accent = Z.Orange) {
            Text("Силовая", fontWeight = FontWeight.SemiBold)
            val s = session
            when {
                program == null -> {
                    Muted("Программы пока нет. Заполните инвентарь — ИИ соберёт план, движок посчитает веса.")
                    OutlinedButton(onClick = { onOpenTab(Tab.TRAINING) }) { Text("Настроить") }
                }
                s != null -> {
                    Text("Следующая: день ${s.dayName}${if (s.calibration) " · прикидка" else ""}${if (s.deload) " · разгрузка" else ""}", color = Z.Orange)
                    s.labourNote?.let { Muted(it) }
                    s.items.forEach { it2 ->
                        val w = if (it2.state.weightKg > 0) " · ${it2.state.weightKg.r1()} кг" else ""
                        Muted("${it2.exercise.nameRu}: ${it2.sets}×${it2.state.targetReps}$w${it2.skippedReason?.let { r -> " (пропуск: $r)" } ?: ""}")
                    }
                    Button(onClick = onStartWorkout, colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.Bg)) { Text("Начать тренировку") }
                }
            }
        }

        // Вес
        ZCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Вес", fontWeight = FontWeight.SemiBold)
                weights.firstOrNull()?.let { Muted(dayLabel(it.day)) }
            }
            if (weights.isEmpty()) Muted("Записей нет")
            else {
                Row {
                    Big("${weights.first().weightKg.r1()} кг")
                    val week = weights.firstOrNull { it.day <= weights.first().day - 7 }
                    week?.let {
                        val d = weights.first().weightKg - it.weightKg
                        Spacer(Modifier.width(8.dp))
                        Muted("${if (d >= 0) "+" else ""}${d.r1()} за неделю", Modifier.padding(top = 12.dp))
                    }
                }
                Sparkline(weights.take(30).reversed().map { it.weightKg }, Z.Lime, Modifier.fillMaxWidth().height(48.dp))
            }
            profile.targetWeightKg?.let { Muted("Цель: ${it.r1()} кг") }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?) {
    Column {
        Muted(label)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        sub?.let { Text(it, color = Z.Muted, fontSize = 11.sp) }
    }
}
