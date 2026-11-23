package com.tikhomirov.zdorovyak.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.East
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.strength.CalDay
import com.tikhomirov.zdorovyak.strength.DayStatus
import com.tikhomirov.zdorovyak.strength.Discipline
import com.tikhomirov.zdorovyak.strength.Mark
import com.tikhomirov.zdorovyak.strength.Schedule
import com.tikhomirov.zdorovyak.strength.TrainingReminder
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Календарь, общий для «Сегодня» и «Тренировок»: 5 недель назад и 2 вперёд, пересчитывается от Flow. */
data class CalendarState(val days: List<CalDay>, val discipline: Discipline, val today: Long) {
    fun day(d: Long) = days.firstOrNull { it.day == d }
    val todayDay: CalDay? get() = day(today)
    /** Ближайшая плановая тренировка после сегодняшнего дня. */
    val next: CalDay? get() = days.firstOrNull { it.day > today && it.status == DayStatus.PLANNED }
}

@Composable
fun rememberCalendar(): CalendarState? {
    val app = LocalApp.current
    val profile by app.settings.profile.collectAsState()
    val prog by app.db.training().activeProgram().collectAsState(initial = null)
    val workouts by app.db.training().finishedWorkouts().collectAsState(initial = null)
    val others by app.db.training().others().collectAsState(initial = emptyList())
    val marks by app.db.training().marks().collectAsState(initial = emptyList())
    val w = workouts ?: return null
    val today = today()
    return remember(profile, prog, w, others, marks, today) {
        val from = Schedule.weekStart(today) - 7 * 5
        val days = app.training.calendar(profile, prog, w, others, marks, from, Schedule.weekStart(today) + 7 * 3 - 1, today)
        CalendarState(days, Schedule.discipline(days, today), today)
    }
}

private fun statusColor(s: DayStatus): Color = when (s) {
    DayStatus.DONE, DayStatus.DONE_MANUAL -> Z.Orange
    DayStatus.REPLACED -> Z.Sand
    DayStatus.MISSED, DayStatus.SKIPPED -> Z.Red
    DayStatus.EXCUSED, DayStatus.MOVED -> Z.Faint
    DayStatus.TODAY, DayStatus.PLANNED -> Z.Orange
    DayStatus.REST -> Z.Faint
}

/** Кружок дня: заливка — сделано, обводка — по плану, красный крестик — пропуск, точка — отдых. */
@Composable
fun DayDot(d: CalDay, size: Dp, showLetter: Boolean = true) {
    val c = statusColor(d.status)
    val mod = Modifier.size(size).clip(CircleShape)
    Box(
        when (d.status) {
            DayStatus.DONE, DayStatus.DONE_MANUAL, DayStatus.REPLACED -> mod.background(c)
            DayStatus.TODAY -> mod.background(c.copy(alpha = 0.18f)).border(2.dp, c, CircleShape)
            DayStatus.PLANNED -> mod.border(1.5.dp, c.copy(alpha = 0.6f), CircleShape)
            DayStatus.MISSED, DayStatus.SKIPPED -> mod.background(c.copy(alpha = 0.14f)).border(1.5.dp, c.copy(alpha = 0.7f), CircleShape)
            DayStatus.EXCUSED, DayStatus.MOVED -> mod.background(Z.Surface3)
            DayStatus.REST -> mod
        },
        contentAlignment = Alignment.Center,
    ) {
        val small = size < 28.dp
        when (d.status) {
            DayStatus.DONE, DayStatus.DONE_MANUAL -> Icon(Icons.Filled.Check, null, tint = Z.Bg, modifier = Modifier.size(size * 0.55f))
            DayStatus.REPLACED -> Icon(Icons.Filled.Construction, null, tint = Z.Bg, modifier = Modifier.size(size * 0.5f))
            DayStatus.MISSED, DayStatus.SKIPPED -> Icon(Icons.Filled.Close, null, tint = c, modifier = Modifier.size(size * 0.5f))
            DayStatus.MOVED -> Icon(Icons.Filled.East, null, tint = Z.Muted, modifier = Modifier.size(size * 0.5f))
            DayStatus.EXCUSED -> if (!small) Text("ув", color = Z.Muted, fontSize = 10.sp)
            DayStatus.TODAY, DayStatus.PLANNED -> if (showLetter && !small) d.planDay?.let {
                Text(it, color = c, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            DayStatus.REST -> Box(Modifier.size(5.dp).clip(CircleShape).background(Z.Line))
        }
    }
}

/** Неделя Пн–Вс с переключением недель. Нажатие на день — действия с ним. */
@Composable
fun WeekStrip(cal: CalendarState, weekStart: Long, onWeek: ((Long) -> Unit)?, onDay: (CalDay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (onWeek != null) Row(verticalAlignment = Alignment.CenterVertically) {
            val ws = LocalDate.ofEpochDay(weekStart)
            val label = when (weekStart) {
                Schedule.weekStart(cal.today) -> "Эта неделя"
                Schedule.weekStart(cal.today) - 7 -> "Прошлая неделя"
                Schedule.weekStart(cal.today) + 7 -> "Следующая неделя"
                else -> ws.format(DateTimeFormatter.ofPattern("d MMM", ru)) + " – " + ws.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", ru))
            }
            Text(label, Modifier.weight(1f), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val first = cal.days.first().day; val last = cal.days.last().day
            IconButton(onClick = { onWeek(weekStart - 7) }, enabled = weekStart - 7 >= first) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Раньше") }
            IconButton(onClick = { onWeek(weekStart + 7) }, enabled = weekStart + 7 <= last) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Позже") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { i ->
                val d = cal.day(weekStart + i) ?: return@forEach
                val isToday = d.day == cal.today
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) Z.Surface3 else Color.Transparent)
                        .clickable { onDay(d) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(Schedule.short(DayOfWeek.of(i + 1)), color = if (d.planned) Z.Text else Z.Muted, fontSize = 11.sp,
                        fontWeight = if (d.planned) FontWeight.SemiBold else FontWeight.Normal)
                    DayDot(d, 34.dp)
                    Text(d.date.dayOfMonth.toString(), color = if (isToday) Z.Lime else Z.Faint, fontSize = 11.sp,
                        fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Три цифры дисциплины: неделя, серия, процент за 4 недели. */
@Composable
fun DisciplineRow(cal: CalendarState) {
    val dsc = cal.discipline
    val total = Schedule.weekPlanTotal(cal.days, Schedule.weekStart(cal.today))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${dsc.thisWeek.done} из $total", "на этой неделе", Modifier.weight(1f), if (total > 0 && dsc.thisWeek.done >= total) Z.Lime else Z.Text)
        StatTile(if (dsc.streakWeeks > 0) "${dsc.streakWeeks} нед." else "—", "серия без пропусков", Modifier.weight(1f), Z.Orange)
        StatTile(dsc.adherence?.let { "${(it * 100).toInt()}%" } ?: "—", "выполнено за 4 нед.", Modifier.weight(1f),
            when { dsc.adherence == null -> Z.Text; dsc.adherence >= 0.85 -> Z.Lime; dsc.adherence >= 0.6 -> Z.Fat; else -> Z.Red })
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = Z.Text) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Z.Bg.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(value, color = color, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = Z.Muted, fontSize = 11.sp, lineHeight = 13.sp)
    }
}

/** Сетка последних недель: каждая строка — неделя, каждая клетка — день. Видно, где были провалы. */
@Composable
fun WeeksGrid(cal: CalendarState, weeks: Int = 5, onDay: (CalDay) -> Unit) {
    val ws = Schedule.weekStart(cal.today)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            (1..7).forEach { Text(Schedule.short(DayOfWeek.of(it)), Modifier.weight(1f), color = Z.Faint, fontSize = 10.sp, textAlign = TextAlign.Center) }
        }
        (weeks - 1 downTo 0).forEach { back ->
            val start = ws - 7L * back
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(LocalDate.ofEpochDay(start).format(DateTimeFormatter.ofPattern("d.MM", ru)), Modifier.width(44.dp), color = Z.Faint, fontSize = 10.sp)
                (0 until 7).forEach { i ->
                    val d = cal.day(start + i)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (d != null) Box(Modifier.clickable { onDay(d) }) { DayDot(d, 22.dp, showLetter = false) }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(Z.Orange, "сделано"); Legend(Z.Sand, "физработа"); Legend(Z.Red, "пропуск"); Legend(Z.Faint, "уважительно / перенос")
        }
    }
}

@Composable
private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text(" $t", color = Z.Muted, fontSize = 11.sp)
}

/** Действия с днём календаря. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(d: CalDay, today: Long, onStartWorkout: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    fun act(msg: String, block: suspend () -> Unit) { scope.launch { block(); snack(msg) }; onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Z.Surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val title = d.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).replaceFirstChar { it.uppercase() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayDot(d, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Muted(buildString {
                        append(d.status.ru.replaceFirstChar { it.uppercase() })
                        d.planDay?.let { append(" · день $it") }
                    })
                }
            }
            when (d.status) {
                DayStatus.TODAY -> {
                    Button(onClick = { onDismiss(); onStartWorkout() }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.OnAccent)) { Text("Начать тренировку") }
                    OutlinedButton(onClick = { act("Перенесено на завтра") { app.training.moveToNextDay(d.day, profile.weekdays) } }, Modifier.fillMaxWidth()) {
                        Text("Перенести на завтра")
                    }
                    SkipButtons(d) { status, note, msg -> act(msg) { app.training.mark(d.day, status, note) } }
                    TextButton(onClick = { act("Отмечено: сделано") { app.training.mark(d.day, Mark.DONE) } }) { Text("Уже сделал, без записи") }
                }
                DayStatus.PLANNED -> {
                    OutlinedButton(onClick = { act("Перенесено на следующий день") { app.training.moveToNextDay(d.day, profile.weekdays) } }, Modifier.fillMaxWidth()) {
                        Text("Перенести на следующий день")
                    }
                    Muted("Знаете заранее, что не получится?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { act("Отмечено: поездка") { app.training.mark(d.day, Mark.EXCUSED, "поездка") } }) { Text("Поездка") }
                        OutlinedButton(onClick = { act("Отмечено: болезнь") { app.training.mark(d.day, Mark.EXCUSED, "болезнь") } }) { Text("Болею") }
                    }
                }
                DayStatus.MISSED -> {
                    Muted("Тренировки в этот день нет в записях. Честно: что было?")
                    Button(onClick = { act("Отмечено: сделано") { app.training.mark(d.day, Mark.DONE) } }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.OnAccent)) { Text("Сделал, но не записал") }
                    SkipButtons(d) { status, note, msg -> act(msg) { app.training.mark(d.day, status, note) } }
                }
                DayStatus.SKIPPED, DayStatus.EXCUSED, DayStatus.DONE_MANUAL, DayStatus.MOVED -> {
                    d.note.takeIf { it.isNotBlank() }?.let { Muted("Причина: $it") }
                    OutlinedButton(onClick = { act("Отметка снята") { app.training.clearMark(d.day) } }, Modifier.fillMaxWidth()) { Text("Снять отметку") }
                }
                DayStatus.REST -> if (d.day >= today) {
                    if (d.day == today) Button(onClick = { onDismiss(); onStartWorkout() }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.OnAccent)) { Text("Внеплановая тренировка") }
                    OutlinedButton(onClick = { act("Тренировка добавлена в календарь") { app.training.mark(d.day, Mark.EXTRA) } }, Modifier.fillMaxWidth()) {
                        Text("Запланировать тренировку на этот день")
                    }
                    if (d.status == DayStatus.REST && d.day > today) Muted("Плановый день по расписанию: ${Schedule.describe(profile.weekdays)}.")
                } else Muted("День отдыха.")
                DayStatus.DONE -> Muted("Тренировка записана. Подробности — в истории ниже.")
                DayStatus.REPLACED -> Muted("Вместо тренировки была физработа — день засчитан.")
            }
        }
    }
}

@Composable
private fun SkipButtons(d: CalDay, onMark: (String, String, String) -> Unit) {
    Muted(if (d.status == DayStatus.TODAY) "Не получится сегодня?" else "Если пропустил:")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        OutlinedButton(onClick = { onMark(Mark.SKIPPED, "нет времени / сил", "Пропуск отмечен") }) { Text("Пропускаю") }
        OutlinedButton(onClick = { onMark(Mark.EXCUSED, "болезнь", "Уважительная причина: болезнь") }) { Text("Болею") }
        OutlinedButton(onClick = { onMark(Mark.EXCUSED, "поездка", "Уважительная причина: поездка") }) { Text("Поездка") }
    }
    Muted("Болезнь и поездки не портят серию. Обычный пропуск — портит, так честнее.")
}

/**
 * Выбор дней недели и напоминания. Если число дней меняется — программу надо пересобрать
 * (у неё ровно столько дней A/B/C), об этом говорим прямо.
 */
@Composable
fun ScheduleDialog(planDays: Int?, onDismiss: () -> Unit, onRebuild: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val profile by app.settings.profile.collectAsState()
    var days by remember { mutableStateOf(profile.weekdays) }
    var hour by remember { mutableStateOf(profile.remindHour) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) snack("Без разрешения на уведомления напоминаний не будет")
    }
    fun save(): Boolean {
        if (days.size !in 2..4) { snack("Выберите от 2 до 4 дней"); return false }
        app.settings.saveProfile(profile.copy(trainingWeekdays = days.sorted(), trainingDays = days.size, remindHour = hour))
        TrainingReminder.schedule(ctx, hour)
        if (hour >= 0 && Build.VERSION.SDK_INT >= 33 && !TrainingReminder.canNotify(ctx)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }
    val countChanged = planDays != null && planDays != days.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Дни тренировок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekdayPicker(days) { days = it }
                ScheduleHints(days)
                SectionTitle("Напоминание в день тренировки")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1, 7, 9, 12, 17, 18, 19, 20).forEach { h ->
                        FilterChip(selected = hour == h, onClick = { hour = h }, label = { Text(if (h < 0) "Выкл" else "$h:00") })
                    }
                }
                if (countChanged) Text("Программа рассчитана на $planDays тренировки в неделю, а дней выбрано ${days.size}. " +
                    "Сохраните и пересоберите программу — прогресс по упражнениям сохранится.", color = Z.Fat, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (save()) { onDismiss(); if (countChanged) onRebuild() } }) {
                Text(if (countChanged) "Сохранить и пересобрать" else "Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun WeekdayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { d ->
            val on = d in days
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                    .background(if (on) Z.Orange else Z.Surface3)
                    .clickable { onChange(if (on) days - d else days + d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(Schedule.short(DayOfWeek.of(d)), color = if (on) Z.Bg else Z.Muted, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ScheduleHints(days: Set<Int>) {
    val b2b = Schedule.backToBack(days)
    when {
        days.size < 2 -> Muted("Минимум 2 дня: реже — прогресса почти не будет.")
        days.size > 4 -> Text("Больше 4 силовых в неделю в 50 лет — лишнее: не успеете восстанавливаться.", color = Z.Fat, fontSize = 13.sp)
        b2b.isNotEmpty() -> Text("Подряд: ${b2b.joinToString { "${Schedule.short(DayOfWeek.of(it.first))}–${Schedule.short(DayOfWeek.of(it.second))}" }}. " +
            "Программа фулбоди — между тренировками нужно 48 ч. Лучше через день.", color = Z.Fat, fontSize = 13.sp)
        else -> Muted("${days.size} тренировки в неделю: ${Schedule.describe(days)}. Между ними есть день отдыха — хорошо.")
    }
}
