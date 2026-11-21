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
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.strength.CalDay
import com.tikhomirov.zdorovyak.strength.DayStatus
import com.tikhomirov.zdorovyak.strength.Schedule
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
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

    val cal = rememberCalendar()
    LaunchedEffect(program, cal?.todayDay?.status) { session = app.training.nextSession(profile) }

    var sheetDay by remember { mutableStateOf<CalDay?>(null) }

    val greet = when (LocalTime.now().hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; else -> "Добрый вечер" }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(top = 4.dp)) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).uppercase(), color = Z.Lime, fontSize = 11.sp,
                fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Text("$greet, ${profile.name}", fontSize = 26.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                lineHeight = 30.sp)
        }

        // Питание: кольца КБЖУ
        ZCard(accent = Z.Lime, modifier = Modifier.clickable { onOpenTab(Tab.FOOD) }) {
            CardHeader("Питание", Icons.Filled.Restaurant, Z.Lime) {
                TextButton(onClick = { onOpenTab(Tab.FOOD) }) { Text("+ Еда") }
            }
            NutritionRings(
                Macros(food.sumOf { it.kcal }, food.sumOf { it.protein }, food.sumOf { it.fat }, food.sumOf { it.carbs }),
                Macros(profile.kcalTarget.toDouble(), profile.proteinTarget.toDouble(), profile.fatTarget.toDouble(), profile.carbsTarget.toDouble()),
                compact = true,
            )
        }

        // Тренировка: что по календарю сегодня
        ZCard(accent = Z.Orange) {
            CardHeader("Силовая", Icons.Filled.FitnessCenter, Z.Orange) {
                cal?.let { c ->
                    val total = Schedule.weekPlanTotal(c.days, Schedule.weekStart(c.today))
                    Pill("неделя ${c.discipline.thisWeek.done}/$total", Z.Orange)
                }
            }
            cal?.let { c -> WeekStrip(c, Schedule.weekStart(c.today), onWeek = null) { sheetDay = it } }
            val s = session
            val t = cal?.todayDay
            when {
                program == null -> {
                    Muted("Программы пока нет. Выберите дни и инвентарь — ИИ соберёт план, движок посчитает веса.")
                    OutlinedButton(onClick = { onOpenTab(Tab.TRAINING) }) { Text("Настроить") }
                }
                t?.status == DayStatus.DONE || t?.status == DayStatus.DONE_MANUAL || t?.status == DayStatus.REPLACED -> {
                    Text("Сегодня сделано. Так держать.", color = Z.Lime, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    cal.next?.let { Muted("Следующая — ${dayName(it.day)}${it.planDay?.let { p -> ", день $p" } ?: ""}.") }
                }
                t?.status == DayStatus.TODAY && s != null -> {
                    Text("Сегодня день ${s.dayName}${if (s.calibration) " · прикидка" else ""}${if (s.deload) " · разгрузка" else ""}",
                        color = Z.Orange, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    s.labourNote?.let { Muted(it) }
                    s.items.forEach { it2 ->
                        val w = if (it2.state.weightKg > 0) " · ${it2.state.weightKg.r1()} кг" else ""
                        Muted("${it2.exercise.nameRu}: ${it2.sets}×${it2.state.targetReps}$w${it2.skippedReason?.let { r -> " (пропуск: $r)" } ?: ""}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartWorkout, colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.Bg)) { Text("Начать") }
                        OutlinedButton(onClick = { sheetDay = t }) { Text("Перенести / пропуск") }
                    }
                }
                else -> {
                    val n = cal?.next
                    Text(when (t?.status) {
                        DayStatus.SKIPPED -> "Сегодня пропуск."
                        DayStatus.EXCUSED -> "Сегодня без тренировки — уважительная причина."
                        DayStatus.MOVED -> "Тренировка перенесена."
                        else -> "Сегодня день отдыха."
                    }, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    n?.let { Muted("Следующая — ${dayName(it.day)}${it.planDay?.let { p -> ", день $p" } ?: ""}. Восстановление — тоже часть тренировки.") }
                }
            }
        }

        // Восстановление (Garmin)
        ZCard(accent = Z.Blue) {
            CardHeader("Восстановление", Icons.Filled.Bedtime, Z.Blue) {
                if (app.garmin.isLoggedIn) TextButton(enabled = !syncing, onClick = {
                    syncing = true
                    scope.launch {
                        runCatching { app.garmin.sync(3) }.onSuccess(snack).onFailure { snack(it.message ?: "Ошибка Garmin") }
                        syncing = false
                    }
                }) { Text(if (syncing) "…" else "Обновить") }
            }
            val g = garmin
            if (g == null) Muted(if (app.garmin.isLoggedIn) "Нет данных за сегодня, нажмите «Обновить»." else "Подключите Garmin в настройках, чтобы видеть сон, Body Battery, HRV и стресс.")
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(g.sleepMinutes?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "–", "сон" + (g.sleepScore?.let { " · $it" } ?: ""), Modifier.weight(1f), Z.Blue)
                StatTile(g.bodyBatteryHigh?.toString() ?: "–", "Body Battery", Modifier.weight(1f), Z.Lime)
                StatTile(g.hrvAvg?.toString() ?: "–", "HRV" + (g.restingHr?.let { " · пульс $it" } ?: ""), Modifier.weight(1f))
                StatTile(g.stressAvg?.toString() ?: "–", "стресс", Modifier.weight(1f), Z.Fat)
            }
        }

        // Вес
        ZCard(modifier = Modifier.clickable { onOpenTab(Tab.BODY) }) {
            CardHeader("Вес", Icons.Filled.MonitorWeight, Z.Text) { weights.firstOrNull()?.let { Muted(dayLabel(it.day)) } }
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
        Spacer(Modifier.height(80.dp))
    }
    sheetDay?.let { d -> DaySheet(d, today(), onStartWorkout = onStartWorkout, onDismiss = { sheetDay = null }) }
}

private fun dayName(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return when (day - today()) {
        1L -> "завтра"
        2L -> "послезавтра"
        else -> d.format(DateTimeFormatter.ofPattern("EEEE", ru))
    }
}
