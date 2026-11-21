package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.data.Profile
import com.tikhomirov.zdorovyak.strength.Catalog
import com.tikhomirov.zdorovyak.strength.Feel
import com.tikhomirov.zdorovyak.strength.Inventory
import com.tikhomirov.zdorovyak.strength.Load
import androidx.compose.foundation.layout.width
import com.tikhomirov.zdorovyak.strength.SessionItem
import com.tikhomirov.zdorovyak.strength.SessionPlan
import com.tikhomirov.zdorovyak.strength.TrainingRepo
import com.tikhomirov.zdorovyak.ui.theme.Z
import com.tikhomirov.zdorovyak.strength.CalDay
import com.tikhomirov.zdorovyak.strength.DayStatus
import com.tikhomirov.zdorovyak.strength.Schedule
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import kotlinx.coroutines.launch

@Composable
fun TrainingScreen(modifier: Modifier, quick: Quick?, onStartWorkout: () -> Unit, consumeQuick: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    val programEntity by app.db.training().activeProgram().collectAsState(initial = null)
    val workouts by app.db.training().finishedWorkouts().collectAsState(initial = emptyList())
    val others by app.db.training().others().collectAsState(initial = emptyList())
    val states by app.db.training().states().collectAsState(initial = emptyList())
    val cardio by app.db.garmin().lastActivities(10).collectAsState(initial = emptyList())
    var setupOpen by remember { mutableStateOf(false) }
    var otherDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var sheetDay by remember { mutableStateOf<CalDay?>(null) }
    var weekStart by remember { mutableStateOf(Schedule.weekStart(today())) }

    LaunchedEffect(quick) { if (quick == Quick.OTHER_ACTIVITY) { otherDialog = true; consumeQuick() } }
    LaunchedEffect(programEntity) { loaded = true }

    // Первый запуск: сразу показываем настройку. Фиксируем это в setupOpen, иначе после нажатия «Составить»
    // (screeningPassed = true, профиль сохранён) экран переключится и отменит составление программы на полпути.
    val autoSetup = loaded && programEntity == null && !app.settings.screeningPassed
    LaunchedEffect(autoSetup) { if (autoSetup) setupOpen = true }
    if (setupOpen || autoSetup) {
        ProgramSetup(profile, onDone = { setupOpen = false }, modifier = modifier)
    } else {
        val plan = app.training.parsePlan(programEntity)
        val cal = rememberCalendar()
        val stateMap = states.associateBy { it.exerciseId }
        LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cal?.let { c ->
                item {
                    ZCard(accent = Z.Orange) {
                        CardHeader("Дни: ${Schedule.describe(profile.weekdays)}", Icons.Filled.CalendarMonth, Z.Orange) {
                            TextButton(onClick = { scheduleOpen = true }) { Text("Изменить") }
                        }
                        WeekStrip(c, weekStart, onWeek = { weekStart = it }) { sheetDay = it }
                        DisciplineRow(c)
                        val t = c.todayDay
                        when (t?.status) {
                            DayStatus.TODAY -> Button(onClick = onStartWorkout, Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.Bg)) {
                                Text("Сегодня тренировка${t.planDay?.let { " · день $it" } ?: ""} — начать")
                            }
                            else -> c.next?.let { n ->
                                Muted("Следующая: ${n.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", ru))}${n.planDay?.let { " · день $it" } ?: ""}. Нажмите на день, чтобы отметить или перенести.")
                            }
                        }
                    }
                }
                item {
                    ZCard {
                        CardHeader("Последние 5 недель", null, Z.Orange)
                        WeeksGrid(c) { sheetDay = it }
                    }
                }
            }
            item {
                ZCard(accent = Z.Orange) {
                    if (plan == null) {
                        Text("Программа не составлена", fontWeight = FontWeight.SemiBold)
                        Button(onClick = { setupOpen = true }) { Text("Составить программу") }
                    } else {
                        CardHeader("Программа", Icons.Filled.FitnessCenter, Z.Orange) {
                            Pill(if (plan.source == "ai") "составил ИИ" else "базовая", Z.Orange)
                        }
                        if (plan.rationale.isNotBlank()) Muted(plan.rationale)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onStartWorkout) { Text("Тренировка вне плана") }
                            OutlinedButton(onClick = { setupOpen = true }) { Text("Пересобрать") }
                        }
                    }
                }
            }
            plan?.days?.forEach { d ->
                item { SectionTitle("День ${d.name}${cal?.days?.firstOrNull { it.day >= today() && it.planDay == d.name && (it.status == DayStatus.TODAY || it.status == DayStatus.PLANNED) }?.let { " · ближайший: " + dayLabel(it.day) } ?: ""}") }
                item {
                    ZCard {
                        d.exercises.forEach { pe ->
                            val st = stateMap[pe.exerciseId]
                            val ex = st?.variantId?.let { Catalog[it] } ?: Catalog[pe.exerciseId]
                            Row(Modifier.fillMaxWidth()) {
                                Text(ex?.nameRu ?: pe.exerciseId, Modifier.weight(1f))
                                Muted(buildString {
                                    append("${st?.sets ?: pe.sets}×${st?.targetReps ?: pe.repMin}")
                                    if ((st?.weightKg ?: 0.0) > 0) append(" · ${st!!.weightKg.r1()} кг")
                                    if (st?.calibrated != true) append(" · прикидка")
                                })
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Прочая активность")
                    TextButton(onClick = { otherDialog = true }) { Text("+ Добавить") }
                }
            }
            if (others.isEmpty()) item { Muted("Работа по дому, на участке, переноска тяжестей. Нагрузка = RPE × активные минуты; тяжёлый день для ног/спины разгружает следующую силовую.") }
            items(others.take(10), key = { "o${it.id}" }) { o ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.title)
                        Muted("${dayLabel(o.day)} · ${o.activeMinutes} мин · RPE ${o.rpe} · ${o.load} ед.${if (o.replacesWorkout) " · вместо тренировки" else ""}")
                    }
                    IconButton(onClick = { scope.launch { app.db.training().deleteOther(o) } }) { Icon(Icons.Filled.Delete, "Удалить", tint = Z.Muted) }
                }
            }
            if (cardio.isNotEmpty()) {
                item { SectionTitle("Кардио из Garmin") }
                items(cardio, key = { "g${it.activityId}" }) { a ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name.ifBlank { a.type })
                            Muted(buildString {
                                append("${dayLabel(a.day)} · ${a.durationMin.r0()} мин")
                                a.distanceKm?.takeIf { it > 0.05 }?.let { append(" · ${it.r1()} км") }
                                a.avgHr?.let { append(" · пульс $it") }
                            })
                        }
                        a.trainingLoad?.let { Pill("нагрузка ${it.r0()}", Z.Blue) }
                    }
                }
            }
            item { SectionTitle("История тренировок") }
            if (workouts.isEmpty()) item { Muted("Пока пусто") }
            items(workouts.take(20), key = { "w${it.id}" }) { w ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${dayLabel(w.day)} · день ${w.dayName}", Modifier.weight(1f))
                    if (w.calibration) Pill("прикидка", Z.Orange)
                    w.finishedAt?.let { Muted("  ${(it - w.startedAt) / 60000} мин") }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (otherDialog) OtherActivityDialog(onDismiss = { otherDialog = false })
    if (scheduleOpen) ScheduleDialog(
        planDays = app.training.parsePlan(programEntity)?.days?.size,
        onDismiss = { scheduleOpen = false },
        onRebuild = { setupOpen = true },
    )
    sheetDay?.let { d -> DaySheet(d, today(), onStartWorkout = onStartWorkout, onDismiss = { sheetDay = null }) }
}

@Composable
private fun ProgramSetup(profile: Profile, onDone: () -> Unit, modifier: Modifier) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    var inv by remember { mutableStateOf(profile.inventory) }
    var weekdays by remember { mutableStateOf(profile.weekdays) }
    val days = weekdays.size
    var minutes by remember { mutableStateOf(profile.sessionMinutes.toString()) }
    var limitations by remember { mutableStateOf(profile.limitations) }
    var experience by remember { mutableStateOf(profile.experience) }
    var kbText by remember { mutableStateOf(inv.kettlebells.joinToString(", ") { it.r1() }) }
    var dbMin by remember { mutableStateOf(inv.dbMin.r1()) }
    var dbMax by remember { mutableStateOf(inv.dbMax.r1()) }
    var dbStep by remember { mutableStateOf(inv.dbStep.r1()) }
    var bbMin by remember { mutableStateOf(inv.barbellMin.r1()) }
    var bbMax by remember { mutableStateOf(inv.barbellMax.r1()) }
    var bbStep by remember { mutableStateOf(inv.barbellStep.r1()) }
    val redFlags = listOf(
        "Боль или давление в груди при нагрузке",
        "Известные болезни сердца, аритмия",
        "Неконтролируемое давление",
        "Операция за последние 3 месяца",
        "Острая боль в спине или суставах сейчас",
    )
    val flags = remember { mutableStateListOf(*Array(redFlags.size) { false }) }
    var building by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Настройка силовой программы", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Muted("ИИ выбирает упражнения из каталога, движок проверяет план и ведёт прогрессию. Первые 2 тренировки — прикидка весов без тестов на максимум.")

        SectionTitle("Инвентарь")
        CheckRow("Гантели разборные", inv.dumbbells) { inv = inv.copy(dumbbells = it) }
        if (inv.dumbbells) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("от, кг", dbMin, { dbMin = it }, Modifier.weight(1f))
            NumField("до, кг", dbMax, { dbMax = it }, Modifier.weight(1f))
            NumField("шаг, кг", dbStep, { dbStep = it }, Modifier.weight(1f))
        }
        TextField1("Гири, кг через запятую (если есть)", kbText, { kbText = it })
        CheckRow("Штанга", inv.barbell) { inv = inv.copy(barbell = it) }
        if (inv.barbell) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("гриф, кг", bbMin, { bbMin = it }, Modifier.weight(1f))
            NumField("макс, кг", bbMax, { bbMax = it }, Modifier.weight(1f))
            NumField("шаг, кг", bbStep, { bbStep = it }, Modifier.weight(1f))
        }
        CheckRow("Скамья", inv.bench) { inv = inv.copy(bench = it) }
        CheckRow("Турник", inv.pullupBar) { inv = inv.copy(pullupBar = it) }
        CheckRow("Резинки", inv.bands) { inv = inv.copy(bands = it) }
        CheckRow("Тренажёры / блоки (зал)", inv.machines) { inv = inv.copy(machines = it) }

        SectionTitle("Режим")
        Text("Дни тренировок")
        WeekdayPicker(weekdays) { weekdays = it }
        ScheduleHints(weekdays)
        NumField("Минут на тренировку", minutes, { minutes = it }, decimal = false)
        TextField1("Опыт (например: «год назад ходил в зал»)", experience, { experience = it })
        TextField1("Что беречь (колено, поясница, плечо…)", limitations, { limitations = it }, single = false)

        SectionTitle("Безопасность")
        Muted("Отметьте, если что-то из этого есть:")
        redFlags.forEachIndexed { i, f -> CheckRow(f, flags[i]) { flags[i] = it } }
        if (flags.any { it }) Text("Сначала обсудите нагрузки с врачом. Программу можно собрать, но начинайте только после его согласия.", color = Z.Red)

        building?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text(it) } }
        log.forEach { Muted(it) }
        Button(enabled = building == null && days in 2..4, onClick = {
            val kbs = kbText.split(',', ';', ' ').mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            val inv2 = inv.copy(kettlebells = kbs,
                dbMin = dbMin.toDoubleOrNull() ?: inv.dbMin, dbMax = dbMax.toDoubleOrNull() ?: inv.dbMax,
                dbStep = dbStep.toDoubleOrNull()?.takeIf { it > 0 } ?: inv.dbStep,
                barbellMin = bbMin.toDoubleOrNull() ?: inv.barbellMin, barbellMax = bbMax.toDoubleOrNull() ?: inv.barbellMax,
                barbellStep = bbStep.toDoubleOrNull()?.takeIf { it > 0 } ?: inv.barbellStep)
            val p = profile.copy(inventory = inv2, trainingDays = days, trainingWeekdays = weekdays.sorted(),
                sessionMinutes = minutes.toIntOrNull() ?: 50, limitations = limitations, experience = experience)
            app.settings.saveProfile(p)
            app.settings.screeningPassed = true
            building = "Составляю…"
            scope.launch {
                runCatching { app.training.buildProgram(p) { building = it } }
                    .onSuccess { (plan, l) ->
                        log = l
                        snack(if (plan.source == "ai") "Программа готова" else "Собран базовый план (без ИИ)")
                        building = null
                        if (l.isEmpty() || plan.source == "ai") onDone()
                    }
                    .onFailure { building = null; snack(it.message ?: "Ошибка") }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Составить программу") }
        if (app.training.parsePlan(null) == null) TextButton(onClick = onDone) { Text("Закрыть") }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text)
    }
}

@Composable
fun OtherActivityDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    var rpe by remember { mutableStateOf(5f) }
    val zones = listOf("LEGS" to "ноги", "BACK" to "спина", "ARMS" to "руки", "SHOULDERS" to "плечи", "CORE" to "кор")
    val sel = remember { mutableStateListOf<String>() }
    var replaces by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Прочая активность") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField1("Что делали (копал, таскал брус…)", title, { title = it })
                NumField("Активных минут (без перекуров)", minutes, { minutes = it }, decimal = false)
                Text("Тяжесть (RPE): ${rpe.toInt()} из 10")
                Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
                Muted("3 — легко, 5 — заметно, 7 — тяжело, 9 — на пределе")
                Text("Что нагружали:")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    zones.forEach { (k, l) ->
                        FilterChip(selected = k in sel, onClick = { if (k in sel) sel.remove(k) else sel.add(k) }, label = { Text(l) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Z.Sand, selectedLabelColor = Z.Bg))
                    }
                }
                CheckRow("Вместо силовой тренировки", replaces) { replaces = it }
                val load = (minutes.toIntOrNull() ?: 0) * rpe.toInt()
                Text("Нагрузка: $load ед.${if (load >= 400) " — тяжёлый день" else ""}", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { app.training.addOther(today(), title.ifBlank { "Физработа" }, minutes.toIntOrNull() ?: 0, rpe.toInt(), sel.toSet(), replaces) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------- Тренировка -------------------------------

private class ItemInput(val item: SessionItem) {
    var weight by mutableStateOf(if (item.state.weightKg > 0) item.state.weightKg.r1() else "")
    val reps = mutableStateListOf(*Array(item.sets) { item.state.targetReps.toString() })
    var feel by mutableStateOf<Feel?>(null)
    var pain by mutableStateOf(0f)
}

@Composable
fun WorkoutScreen(onClose: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    var session by remember { mutableStateOf<SessionPlan?>(null) }
    var inputs by remember { mutableStateOf<List<ItemInput>>(emptyList()) }
    val startedAt = remember { System.currentTimeMillis() }
    var summary by remember { mutableStateOf<List<String>?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = app.training.nextSession(profile)
        session = s
        inputs = s?.items?.filter { it.skippedReason == null }?.map { ItemInput(it) } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(session?.let { "День ${it.dayName}${if (it.calibration) " · прикидка" else ""}${if (it.deload) " · разгрузка" else ""}" } ?: "Тренировка",
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        val s = session
        if (s == null) { Muted("Нет активной программы.", Modifier.padding(16.dp)); return@Column }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ZCard(accent = Z.Orange) {
                    if (s.calibration) Muted("Прикидка: 2 разминочных подхода с лёгким весом, затем рабочий вес «на 10 раз с запасом». Делайте 8–12 повторов, останавливайтесь, когда в запасе ещё ~2. Если вышло 15+, следующий подход тяжелее на 15–20%. Впишите вес и лучший подход.")
                    else Muted("Работаем без отказа: в последнем подходе должно оставаться 1–3 повтора в запасе. После упражнения нажмите, как оно далось.")
                    if (s.deload) Text("Разгрузочная неделя: подходов меньше, вес тот же.", color = Z.Orange)
                    s.labourNote?.let { Text(it, color = Z.Sand) }
                    s.breakNote?.let { Text(it, color = Z.Orange) }
                }
            }
            items(inputs) { inp -> ExerciseCard(inp) }
            s.items.filter { it.skippedReason != null }.forEach { sk ->
                item { Muted("Пропуск: ${sk.exercise.nameRu} (${sk.skippedReason})") }
            }
            item {
                Button(
                    enabled = !saving && inputs.all { it.feel != null },
                    onClick = {
                        saving = true
                        scope.launch {
                            val results = inputs.map { i ->
                                TrainingRepo.ItemResult(i.item, i.weight.toDoubleOrNull() ?: 0.0,
                                    i.reps.mapNotNull { it.toIntOrNull() }.filter { it > 0 }, i.feel!!, i.pain.toInt())
                            }
                            runCatching { app.training.finishSession(profile, s, results, startedAt) }
                                .onSuccess { summary = it }
                                .onFailure { snack(it.message ?: "Ошибка") }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Z.Orange, contentColor = Z.Bg),
                ) { Text(if (inputs.all { it.feel != null }) "Завершить тренировку" else "Оцените каждое упражнение") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    summary?.let { lines ->
        AlertDialog(
            onDismissRequest = { summary = null; onClose() },
            title = { Text("Готово. Что дальше") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { lines.forEach { Text(it, fontSize = 14.sp) } } },
            confirmButton = { TextButton(onClick = { summary = null; onClose() }) { Text("OK") } },
        )
    }
}

@Composable
private fun ExerciseCard(inp: ItemInput) {
    val it0 = inp.item
    var painOpen by remember { mutableStateOf(false) }
    ZCard(accent = if (inp.feel != null) Z.Lime else null) {
        Text(it0.exercise.nameRu, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Muted("Цель: ${it0.sets}×${it0.state.targetReps} (диапазон ${it0.state.repMin}–${it0.state.repMax}), отдых ${it0.plan.restSec} с")
        if (it0.exercise.cue.isNotBlank()) Muted(it0.exercise.cue)
        if (it0.exercise.load in setOf(Load.DUMBBELL, Load.KETTLEBELL, Load.BARBELL, Load.MACHINE))
            NumField(if (it0.exercise.load.name == "DUMBBELL") "Вес одной гантели, кг" else "Вес, кг", inp.weight, { inp.weight = it }, Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            inp.reps.forEachIndexed { i, r ->
                NumField("Подход ${i + 1}", r, { inp.reps[i] = it }, Modifier.width(96.dp), decimal = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Feel.entries.forEach { f ->
                val c = when (f) { Feel.HARD -> Z.Red; Feel.NORMAL -> Z.Lime; Feel.EASY -> Z.Blue }
                FilterChip(selected = inp.feel == f, onClick = { inp.feel = f }, label = { Text(f.ru) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c, selectedLabelColor = Z.Bg))
            }
        }
        if (!painOpen && inp.pain == 0f) TextButton(onClick = { painOpen = true }) { Text("Был дискомфорт или боль?") }
        else {
            Text("Боль: ${inp.pain.toInt()} из 10", color = if (inp.pain >= 5) Z.Red else Z.Text)
            Slider(value = inp.pain, onValueChange = { inp.pain = it }, valueRange = 0f..10f, steps = 9)
        }
    }
}

@Suppress("unused")
private fun Inventory.short() = describe()
