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
import com.tikhomirov.zdorovyak.nutrition.PhotoItem
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

val meals = listOf("Завтрак", "Обед", "Ужин", "Перекус")

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
    var photoResult by remember { mutableStateOf<Pair<List<PhotoItem>, String>?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoHint by remember { mutableStateOf("") }
    var hintDialog by remember { mutableStateOf(false) }
    var showEnergyNote by remember { mutableStateOf(false) }

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
                .onSuccess { photoResult = it.items to it.comment }
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
                val kcal = entries.sumOf { it.kcal }
                Row(verticalAlignment = Alignment.Bottom) {
                    Big(kcal.r0())
                    Muted("  из ${profile.kcalTarget} ккал", Modifier.padding(bottom = 6.dp))
                }
                MacroBar("Белок, г", entries.sumOf { it.protein }, profile.proteinTarget.toDouble(), Z.Lime)
                MacroBar("Жиры, г", entries.sumOf { it.fat }, profile.fatTarget.toDouble(), Z.Orange)
                MacroBar("Углеводы, г", entries.sumOf { it.carbs }, profile.carbsTarget.toDouble(), Z.Blue)
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { startPhoto() }, Modifier.weight(1f)) { Icon(Icons.Filled.CameraAlt, null); Text(" Фото") }
                OutlinedButton(onClick = { scanBarcode() }, Modifier.weight(1f)) { Icon(Icons.Filled.QrCodeScanner, null); Text(" Код") }
                OutlinedButton(onClick = { draft = FoodDraft() }, Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Text(" Ввод") }
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

    photoResult?.let { (items, comment) ->
        val edit = remember(items) { mutableStateListOf(*items.map { it to it.grams.r0() }.toTypedArray()) }
        var meal by remember { mutableStateOf(defaultMeal()) }
        AlertDialog(
            onDismissRequest = { photoResult = null },
            title = { Text("На фото") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comment.isNotBlank()) Muted(comment)
                    MealChips(meal) { meal = it }
                    edit.forEachIndexed { i, (it0, grams) ->
                        val g = grams.toDoubleOrNull() ?: 0.0
                        val k = if (it0.grams > 0) g / it0.grams else 0.0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it0.name)
                                Muted("${(it0.kcal * k).r0()} ккал · Б ${(it0.protein * k).r0()} Ж ${(it0.fat * k).r0()} У ${(it0.carbs * k).r0()}")
                            }
                            NumField("г", grams, { v -> edit[i] = it0 to v }, Modifier.padding(start = 8.dp).width(84.dp), decimal = false)
                        }
                    }
                    if (edit.isEmpty()) Text("Ничего не распознано.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        edit.forEach { (it0, grams) ->
                            val g = grams.toDoubleOrNull() ?: return@forEach
                            if (g <= 0) return@forEach
                            val k = if (it0.grams > 0) g / it0.grams else 1.0
                            // Прикидку по фото в справочник не пишем: она не должна затирать проверенные цифры
                            app.nutrition.add(FoodEntry(day = day, time = System.currentTimeMillis(), meal = meal, name = it0.name, grams = g,
                                kcal = it0.kcal * k, protein = it0.protein * k, fat = it0.fat * k, carbs = it0.carbs * k, source = "photo"),
                                remember = null)
                        }
                    }
                    photoResult = null
                }) { Text("Добавить всё") }
            },
            dismissButton = { TextButton(onClick = { photoResult = null }) { Text("Отмена") } },
        )
    }
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
                NumField("Масса порции, г", d.grams, { d = d.copy(grams = it) })
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
