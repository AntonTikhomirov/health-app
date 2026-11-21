package com.tikhomirov.zdorovyak.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tikhomirov.zdorovyak.data.FoodEntry
import com.tikhomirov.zdorovyak.data.FoodItem
import com.tikhomirov.zdorovyak.nutrition.FoodBase
import com.tikhomirov.zdorovyak.nutrition.FoodOption
import com.tikhomirov.zdorovyak.nutrition.FoodSource
import com.tikhomirov.zdorovyak.nutrition.MealItem
import com.tikhomirov.zdorovyak.nutrition.ParsedMeal
import com.tikhomirov.zdorovyak.nutrition.Portions
import com.tikhomirov.zdorovyak.voice.VoiceInput
import android.app.Activity
import android.speech.RecognizerIntent
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

val meals = listOf("Завтрак", "Обед", "Ужин", "Перекус")
val mealColors = listOf(Z.Fat, Z.Lime, Z.Carbs, Z.Protein)

fun defaultMeal(): String = when (LocalTime.now().hour) { in 4..10 -> "Завтрак"; in 11..15 -> "Обед"; in 16..21 -> "Ужин"; else -> "Перекус" }

/**
 * Форма продукта: значения на 100 г + масса порции.
 * [source] — как запись попала в дневник (manual / barcode), [itemSource] — откуда цифры на 100 г (FoodSource).
 */
data class FoodDraft(
    val name: String = "", val grams: String = "100",
    val kcal100: String = "", val p100: String = "", val f100: String = "", val c100: String = "",
    val barcode: String? = null, val source: String = "manual", val itemSource: String = FoodSource.USER,
) {
    fun withItem(i: FoodItem, keepGrams: Boolean = true) = copy(
        name = i.name, grams = if (keepGrams) grams else "100",
        kcal100 = i.kcal100.r1(), p100 = i.protein100.r1(), f100 = i.fat100.r1(), c100 = i.carbs100.r1(),
        barcode = i.barcode ?: barcode, itemSource = i.source,
    )

    fun isEmptyNutrition() = listOf(kcal100, p100, f100, c100).all { (it.toDoubleOrNull() ?: 0.0) == 0.0 }

    companion object {
        fun of(i: FoodItem, source: String) = FoodDraft(source = source).withItem(i, keepGrams = false)
    }
}

/** Снимок камерой во временный файл, который потом можно отдать Claude. */
fun newPhotoFile(ctx: Context, prefix: String): Pair<File, Uri> {
    val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
    val f = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
}

@Composable
fun NutritionScreen(modifier: Modifier, quick: Quick?, consumeQuick: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    val energy by app.settings.energy.collectAsState()
    var day by remember { mutableStateOf(today()) }
    val entries by app.db.food().forDay(day).collectAsState(initial = emptyList())
    val prevEntries by app.db.food().forDay(day - 1).collectAsState(initial = emptyList())
    val recent by app.db.food().recentItems().collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf<FoodDraft?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<Pair<ParsedMeal, String>?>(null) }   // разбор + источник записи (photo / phrase)
    var phraseOpen by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoHint by remember { mutableStateOf("") }
    var hintDialog by remember { mutableStateOf(false) }
    var showEnergyNote by remember { mutableStateOf(false) }
    // Калории за 7 дней, заканчивая выбранным днём (для столбиков «Неделя»)
    var week by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(day, entries) {
        val byDay = app.db.food().kcalByDay(day - 6, day).associate { it.day to it.kcal }
        week = (day - 6..day).map { d ->
            java.time.LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, ru).removeSuffix(".").replaceFirstChar { it.uppercase() } to (byDay[d] ?: 0.0)
        }
    }

    fun scanBarcode() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { bc ->
                val code = bc.rawValue ?: return@addOnSuccessListener
                busy = "Ищу продукт $code…"
                scope.launch {
                    val r = app.nutrition.lookupBarcode(code)
                    busy = null
                    draft = when {
                        r.item != null -> FoodDraft.of(r.item, "barcode")
                        r.nameOnly != null -> {
                            snack("В Open Food Facts нет КБЖУ — сфотографируйте этикетку")
                            FoodDraft(name = r.nameOnly, barcode = code, source = "barcode")
                        }
                        else -> {
                            snack("Продукта нет в Open Food Facts — сфотографируйте этикетку или введите с упаковки")
                            FoodDraft(barcode = code, source = "barcode")
                        }
                    }
                }
            }
            .addOnFailureListener { snack("Сканер недоступен: ${it.message}") }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && photoFile != null) hintDialog = true
    }
    fun startPhoto() {
        if (!app.claude.hasKey) { snack("Для фото нужен ключ Claude в настройках"); return }
        val (f, uri) = newPhotoFile(ctx, "meal")
        photoFile = f
        takePhoto.launch(uri)
    }
    fun analyze() {
        val f = photoFile ?: return
        busy = "Claude смотрит на тарелку…"
        scope.launch {
            runCatching { app.nutrition.analyzePhoto(f, photoHint) }
                .onSuccess { parsed = it to "photo" }
                .onFailure { snack(it.message ?: "Ошибка анализа") }
            busy = null; photoHint = ""
            f.delete()
        }
    }

    LaunchedEffect(quick) {
        when (quick) {
            Quick.FOOD_MANUAL -> draft = FoodDraft()
            Quick.FOOD_BARCODE -> scanBarcode()
            Quick.FOOD_PHOTO -> startPhoto()
            Quick.FOOD_PHRASE -> phraseOpen = true
            else -> {}
        }
        if (quick != null) consumeQuick()
    }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { day-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") }
                Text(dayLabel(day), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { if (day < today()) day++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") }
            }
        }
        item {
            ZCard(accent = Z.Lime) {
                NutritionRings(
                    Macros(entries.sumOf { it.kcal }, entries.sumOf { it.protein }, entries.sumOf { it.fat }, entries.sumOf { it.carbs }),
                    Macros(profile.kcalTarget.toDouble(), profile.proteinTarget.toDouble(), profile.fatTarget.toDouble(), profile.carbsTarget.toDouble()),
                )
                MealSplitBar(meals.mapIndexed { i, m -> Triple(m, entries.filter { it.meal == m }.sumOf { it.kcal }, mealColors[i]) },
                    Modifier.padding(top = 4.dp))
                val status = when {
                    !profile.autoTargets -> "Нормы заданы вручную"
                    energy.adaptive && energy.tdee != null -> "Нормы авто · расход по факту ≈ ${energy.tdee!!.r0()} ккал"
                    energy.formulaTdee != null -> "Нормы авто · расход по формуле ≈ ${energy.formulaTdee!!.r0()} ккал"
                    else -> "Нормы авто · нужен вес"
                }
                Row(Modifier.fillMaxWidth().clickable { showEnergyNote = !showEnergyNote }.padding(top = 6.dp)) {
                    Muted(status + if (energy.note.isNotBlank() && profile.autoTargets) (if (showEnergyNote) "  ▴" else "  ▾") else "")
                }
                if (showEnergyNote && profile.autoTargets && energy.note.isNotBlank()) Muted(energy.note)
            }
        }
        if (week.any { it.second > 0 }) item {
            ZCard {
                val logged = week.filter { it.second > 0 }
                CardHeader("Неделя", null, Z.Lime) {
                    Muted("в среднем ${(logged.sumOf { it.second } / logged.size).r0()} ккал")
                }
                WeekKcalBars(week, profile.kcalTarget.toDouble())
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputTile("Сказать", Icons.Filled.Mic, Z.Lime, Modifier.weight(1f)) { phraseOpen = true }
                InputTile("Фото", Icons.Filled.CameraAlt, Z.Carbs, Modifier.weight(1f)) { startPhoto() }
                InputTile("Штрихкод", Icons.Filled.QrCodeScanner, Z.Fat, Modifier.weight(1f)) { scanBarcode() }
                InputTile("Вручную", Icons.Filled.Edit, Z.Protein, Modifier.weight(1f)) { draft = FoodDraft() }
            }
        }
        // «Как вчера»: приёмы пищи, которые были накануне и ещё не записаны сегодня
        val repeatable = meals.filter { m -> prevEntries.any { it.meal == m } && entries.none { it.meal == m } }
        if (repeatable.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeatable.forEach { m ->
                        val k = prevEntries.filter { it.meal == m }.sumOf { it.kcal }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val n = app.nutrition.repeatMeal(day - 1, day, m)
                                snack("$m как вчера: добавлено $n")
                            }
                        }) { Icon(Icons.Filled.Replay, null); Text(" $m как вчера · ${k.r0()}", fontSize = 13.sp) }
                    }
                }
            }
        }
        busy?.let { msg ->
            item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text(msg) } }
        }
        meals.forEach { meal ->
            val list = entries.filter { it.meal == meal }
            if (list.isNotEmpty()) {
                item { SectionTitle("$meal · ${list.sumOf { it.kcal }.r0()} ккал") }
                list.forEach { e ->
                    item(key = e.id) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name)
                                Muted("${e.grams.r0()} г · Б ${e.protein.r0()} Ж ${e.fat.r0()} У ${e.carbs.r0()}")
                            }
                            Text("${e.kcal.r0()}", fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { scope.launch { app.db.food().delete(e) } }) { Icon(Icons.Filled.Delete, "Удалить", tint = Z.Muted) }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Недавние продукты — нажмите, чтобы добавить") }
            item {
                Column {
                    recent.take(12).forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { draft = FoodDraft.of(r, "manual") }.padding(vertical = 8.dp)) {
                            Text(r.name, Modifier.weight(1f))
                            Muted("${r.kcal100.r0()} ккал/100 г · ${FoodSource.label(r.source)}")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (hintDialog) {
        AlertDialog(
            onDismissRequest = { hintDialog = false; analyze() },
            title = { Text("Подсказка для ИИ") },
            text = { TextField1("Например: «гречка с курицей, ложка масла»", photoHint, { photoHint = it }, single = false) },
            confirmButton = { TextButton(onClick = { hintDialog = false; analyze() }) { Text("Анализировать") } },
        )
    }

    draft?.let { d0 ->
        FoodDialog(d0, onDismiss = { draft = null }) { d, meal ->
            val g = d.grams.toDoubleOrNull() ?: 0.0
            val k = g / 100.0
            val per = FoodItem(name = d.name.ifBlank { "Продукт" }, kcal100 = d.kcal100.toDoubleOrNull() ?: 0.0,
                protein100 = d.p100.toDoubleOrNull() ?: 0.0, fat100 = d.f100.toDoubleOrNull() ?: 0.0,
                carbs100 = d.c100.toDoubleOrNull() ?: 0.0, barcode = d.barcode, source = d.itemSource)
            scope.launch {
                app.nutrition.add(
                    FoodEntry(day = day, time = System.currentTimeMillis(), meal = meal, name = per.name, grams = g,
                        kcal = per.kcal100 * k, protein = per.protein100 * k, fat = per.fat100 * k, carbs = per.carbs100 * k,
                        source = d.source, barcode = d.barcode),
                    remember = per.takeIf { !d.isEmptyNutrition() },
                )
            }
            draft = null
        }
    }

    parsed?.let { (meal0, source) ->
        MealReviewDialog(meal0, onDismiss = { parsed = null }) { picked, meal ->
            scope.launch {
                val now = System.currentTimeMillis()
                picked.forEachIndexed { i, (it0, g) ->
                    // В справочник пишем только то, что взято из базы/справочника; оценки ИИ — нет
                    app.nutrition.add(it0.entryFor(day, meal, g, source, now + i), remember = it0.per100.takeIf { !it0.estimated })
                }
                snack("Добавлено: ${picked.size}")
            }
            parsed = null
        }
    }

    if (phraseOpen) PhraseDialog(onDismiss = { phraseOpen = false }) { text ->
        phraseOpen = false
        busy = "Разбираю: «${text.take(40)}${if (text.length > 40) "…" else ""}»"
        scope.launch {
            runCatching { app.nutrition.parsePhrase(text) }
                .onSuccess { parsed = it to "phrase" }
                .onFailure { snack(it.message ?: "Не удалось разобрать") }
            busy = null
        }
    }
}

@Composable
private fun InputTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Z.Surface).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold)
    }
}

/** Ввод фразой: текст или диктовка. «Гречка 200 г, две котлеты, чай с сахаром». */
@Composable
private fun PhraseDialog(onDismiss: () -> Unit, onParse: (String) -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    var text by remember { mutableStateOf("") }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { t ->
                text = (text + " " + t).trim()
            }
        }
    }
    fun listen() = runCatching { dictation.launch(VoiceInput.intent()) }
        .onFailure { snack("На телефоне нет распознавания речи. Установите приложение Google.") }
    LaunchedEffect(Unit) { if (app.claude.hasKey) listen() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что съели?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField1("Например: гречка 200 г, две котлеты, чай с ложкой сахара", text, { text = it }, single = false)
                OutlinedButton(onClick = { listen() }) { Icon(Icons.Filled.Mic, null); Text(" Сказать ещё") }
                Muted("ИИ разделит на продукты и граммы, а калории и БЖУ возьмёт из базы. Чего нет в базе — оценит и пометит.")
                if (!app.claude.hasKey) Text("Нужен ключ Claude в настройках.", color = Z.Red, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onParse(text.trim()) }, enabled = text.isNotBlank() && app.claude.hasKey) { Text("Разобрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Проверка разбора: граммы можно поправить, лишнее — снять галочку. */
@Composable
private fun MealReviewDialog(m: ParsedMeal, onDismiss: () -> Unit, onSave: (List<Pair<MealItem, Double>>, String) -> Unit) {
    val rows = remember(m) { mutableStateListOf(*m.items.map { Triple(it, it.grams.r0(), true) }.toTypedArray()) }
    var meal by remember { mutableStateOf(m.meal ?: defaultMeal()) }
    fun g(i: Int) = rows[i].second.toDoubleOrNull() ?: 0.0
    val total = rows.indices.filter { rows[it].third }.sumOf { rows[it].first.per100.kcal100 * g(it) / 100 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проверьте") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.comment.isNotBlank()) Muted(m.comment)
                MealChips(meal) { meal = it }
                rows.forEachIndexed { i, (it0, grams, on) ->
                    val k = g(i) / 100
                    val p = it0.per100
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { c -> rows[i] = Triple(it0, grams, c) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = if (on) Z.Text else Z.Muted)
                            Muted("${it0.amount} · ${(p.kcal100 * k).r0()} ккал · Б ${(p.protein100 * k).r0()} Ж ${(p.fat100 * k).r0()} У ${(p.carbs100 * k).r0()}")
                            Text(FoodSource.label(p.source) + if (it0.estimated) " — цифры примерные" else "",
                                color = if (it0.estimated) Z.Fat else Z.Faint, fontSize = 11.sp)
                        }
                        NumField("г", grams, { v -> rows[i] = Triple(it0, v, on) }, Modifier.padding(start = 6.dp).width(80.dp), decimal = false)
                    }
                }
                if (rows.isEmpty()) Text("Ничего не распознано.")
                else Text("Итого ${total.r0()} ккал", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(rows.indices.filter { rows[it].third && g(it) > 0 }.map { rows[it].first to g(it) }, meal)
            }, enabled = rows.any { it.third }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun MealChips(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        meals.forEach { m -> FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(m) }) }
    }
}

@Composable
private fun FoodDialog(initial: FoodDraft, onDismiss: () -> Unit, onSave: (FoodDraft, String) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf(initial) }
    var meal by remember { mutableStateOf(defaultMeal()) }
    var text by remember { mutableStateOf(initial.name) }
    var searchOn by remember { mutableStateOf(initial.kcal100.isBlank()) }
    var options by remember { mutableStateOf<List<FoodOption>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var labelFile by remember { mutableStateOf<File?>(null) }
    var portionLabel by remember { mutableStateOf("") }
    var portionCount by remember { mutableStateOf(0) }

    // «гречка 180» — название и граммы одной строкой; поиск со встроенной базой
    LaunchedEffect(text, searchOn) {
        if (!searchOn || text.isBlank()) { options = emptyList(); return@LaunchedEffect }
        delay(150)
        val (name, grams) = FoodBase.splitGrams(text)
        if (grams != null) d = d.copy(grams = grams.r0())
        options = app.nutrition.search(name)
    }
    fun picked(item: FoodItem) { d = d.withItem(item); text = d.name; searchOn = false; options = emptyList() }

    val takeLabel = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = labelFile ?: return@rememberLauncherForActivityResult
        if (!ok) { f.delete(); return@rememberLauncherForActivityResult }
        busy = "Claude читает этикетку…"; error = null
        scope.launch {
            runCatching { app.nutrition.readLabel(f) }
                .onSuccess { item ->
                    val name = when {
                        d.name.isNotBlank() && d.barcode != null -> d.name   // название из OFF точнее
                        item.name.isNotBlank() -> item.name
                        else -> d.name
                    }
                    picked(item.copy(name = name, barcode = d.barcode))
                }
                .onFailure { error = it.message ?: "Не удалось прочитать этикетку" }
            busy = null
            f.delete()
        }
    }
    fun startLabel() {
        if (!app.claude.hasKey) { error = "Для чтения этикетки нужен ключ Claude в настройках"; return }
        val (f, uri) = newPhotoFile(ctx, "label")
        labelFile = f
        takeLabel.launch(uri)
    }
    fun estimate() {
        val name = FoodBase.splitGrams(text).first
        if (name.isBlank()) return
        if (!app.claude.hasKey) { error = "Для оценки нужен ключ Claude в настройках"; return }
        busy = "Claude оценивает «$name»…"; error = null
        scope.launch {
            runCatching { app.nutrition.estimateByName(name) }
                .onSuccess { picked(it) }
                .onFailure { error = it.message ?: "Ошибка" }
            busy = null
        }
    }

    val g = d.grams.toDoubleOrNull() ?: 0.0
    fun per(v: String) = (v.toDoubleOrNull() ?: 0.0) * g / 100
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.source == "barcode") "Продукт по штрихкоду" else "Добавить еду") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField1("Что съели (можно «гречка 180»)", text, {
                    text = it; searchOn = true; d = d.copy(name = FoodBase.splitGrams(it).first)
                })
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { picked(o.item) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(o.item.name, Modifier.weight(1f), color = Z.Lime)
                        Muted("${o.item.kcal100.r0()} ккал · ${FoodSource.label(o.item.source)}")
                    }
                }
                if (searchOn && text.length >= 2 && options.isEmpty() && busy == null) {
                    Muted("В базе не нашлось. Можно сфотографировать этикетку, ввести цифры с упаковки или попросить оценку ИИ.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { startLabel() }, Modifier.weight(1f)) { Icon(Icons.Filled.DocumentScanner, null); Text(" Этикетка", fontSize = 13.sp) }
                    OutlinedButton(onClick = { estimate() }, Modifier.weight(1f), enabled = text.length >= 2) {
                        Icon(Icons.Filled.AutoAwesome, null); Text(" Оценить ИИ", fontSize = 13.sp)
                    }
                }
                busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Muted(it) } }
                error?.let { Text(it, color = Z.Red, fontSize = 13.sp) }
                NumField("Масса порции, г", d.grams, { d = d.copy(grams = it); portionCount = 0 })
                val portions = remember(d.name) { Portions.forName(d.name) }
                if (portions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    portions.forEach { pt ->
                        val sel = portionLabel == pt.label && portionCount > 0
                        FilterChip(selected = sel, onClick = {
                            portionCount = if (portionLabel == pt.label) portionCount + 1 else 1
                            portionLabel = pt.label
                            d = d.copy(grams = (pt.grams * portionCount).r0())
                        }, label = { Text(if (sel) "${pt.label} ×$portionCount" else "${pt.label} · ${pt.grams.r0()} г") })
                    }
                }
                if (portionCount > 0) Muted("Нажмите ещё раз, чтобы добавить порцию. Можно поправить граммы вручную.")
                Muted("На 100 г" + if (d.kcal100.isNotBlank()) " · источник: ${FoodSource.label(d.itemSource)}" else ":")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("ккал", d.kcal100, { d = d.copy(kcal100 = it, itemSource = FoodSource.USER) }, Modifier.weight(1f))
                    NumField("белки", d.p100, { d = d.copy(p100 = it, itemSource = FoodSource.USER) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("жиры", d.f100, { d = d.copy(f100 = it, itemSource = FoodSource.USER) }, Modifier.weight(1f))
                    NumField("углев.", d.c100, { d = d.copy(c100 = it, itemSource = FoodSource.USER) }, Modifier.weight(1f))
                }
                if (d.itemSource == FoodSource.AI) Muted("Это оценка ИИ по названию: цифры примерные, лучше проверить по упаковке.")
                MealChips(meal) { meal = it }
                Text("Итого: ${per(d.kcal100).r0()} ккал · Б ${per(d.p100).r0()} Ж ${per(d.f100).r0()} У ${per(d.c100).r0()}", fontWeight = FontWeight.SemiBold)
                if (d.isEmptyNutrition() && d.name.isNotBlank()) Text("КБЖУ не заполнены — запишется 0 ккал", color = Z.Orange, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(d, meal) }, enabled = (d.grams.toDoubleOrNull() ?: 0.0) > 0 && d.name.isNotBlank() && busy == null) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
