package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.strength.Feel
import com.tikhomirov.zdorovyak.strength.LoggedExercise
import com.tikhomirov.zdorovyak.strength.PlannedExercise
import com.tikhomirov.zdorovyak.strength.WorkoutLog
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun feelColor(f: Feel?) = when (f) { Feel.EASY -> Z.Lime; Feel.NORMAL -> Z.Orange; Feel.HARD -> Z.Red; null -> Z.Muted }

private fun setsText(sets: List<Pair<Double, Int>>): String {
    if (sets.isEmpty()) return "—"
    val sameW = sets.all { it.first == sets.first().first }
    return if (sameW) {
        val w = sets.first().first
        (if (w > 0) "${w.r1()} кг × " else "") + sets.joinToString(", ") { it.second.toString() }
    } else sets.joinToString(", ") { (w, r) -> if (w > 0) "${w.r1()}×$r" else "$r" }
}

/** Строка дневника: дата, день программы, сводка. Нажатие — подробности. */
@Composable
fun WorkoutLogRow(log: WorkoutLog, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Z.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${dayLabel(log.workout.day)} · день ${log.workout.dayName}", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Muted(log.summary())
        }
        if (log.workout.calibration) Pill("прикидка", Z.Orange)
        val up = log.exercises.count { it.trend > 0 }
        if (up > 0) { Spacer(Modifier.width(6.dp)); Pill("↑ $up", Z.Lime) }
    }
}

@Composable
fun LoggedExerciseRow(e: LoggedExercise) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            e.feel?.let { Text(it.ru.lowercase(), color = feelColor(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Text(setsText(e.sets), color = Z.Text, fontSize = 14.sp)
        val extra = buildList {
            if (e.previous.isNotEmpty()) add("в прошлый раз: ${setsText(e.previous)}" + when (e.trend) { 1 -> " ↑"; -1 -> " ↓"; else -> "" })
            if (e.pain > 0) add("боль ${e.pain}/10")
        }
        if (extra.isNotEmpty()) Muted(extra.joinToString(" · "))
    }
}

/** Полная запись: упражнения, подходы, выводы движка на следующий раз. */
@Composable
fun WorkoutLogContent(log: WorkoutLog) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Muted(log.summary() + if (log.workout.calibration) " · прикидка" else "")
        log.exercises.forEach { LoggedExerciseRow(it) }
        if (log.exercises.isEmpty()) Muted("Подходы не записаны.")
        if (log.notes.isNotEmpty()) {
            SectionTitle("Что изменилось на следующий раз")
            log.notes.forEach { Muted("• $it") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogSheet(workoutId: Long, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var log by remember { mutableStateOf<WorkoutLog?>(null) }
    LaunchedEffect(workoutId) { log = app.training.workoutLog(workoutId) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Z.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val l = log
            if (l == null) Muted("Загрузка…")
            else {
                Text(LocalDate.ofEpochDay(l.workout.day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).replaceFirstChar { it.uppercase() } +
                    " · день ${l.workout.dayName}", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                WorkoutLogContent(l)
            }
        }
    }
}

/** План будущей тренировки: упражнения и текущие цели. */
@Composable
fun PlannedPreview(items: List<PlannedExercise>, isNext: Boolean) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Что будет")
        items.forEach { p ->
            Row(Modifier.fillMaxWidth()) {
                Text(p.name, Modifier.weight(1f))
                Muted(buildString {
                    append("${p.sets}×${p.reps}")
                    if (p.weightKg > 0) append(" · ${p.weightKg.r1()} кг")
                    if (!p.calibrated) append(" · прикидка")
                })
            }
        }
        Muted(if (isNext) "Веса — текущие цели движка." else "Веса — по текущим целям; уточнятся после предыдущих тренировок.")
    }
}
