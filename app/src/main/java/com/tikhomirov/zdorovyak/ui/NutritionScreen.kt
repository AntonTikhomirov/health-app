package com.tikhomirov.zdorovyak.ui

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tikhomirov.zdorovyak.data.FoodEntry
import com.tikhomirov.zdorovyak.data.FoodItem
import com.tikhomirov.zdorovyak.nutrition.PhotoItem
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

val meals = listOf("Завтрак", "Обед", "Ужин", "Перекус")

fun defaultMeal(): String = when (LocalTime.now().hour) { in 4..10 -> "Завтрак"; in 11..15 -> "Обед"; in 16..21 -> "Ужин"; else -> "Перекус" }

/** Форма продукта: значения на 100 г + масса порции. */
data class FoodDraft(
    val name: String = "", val grams: String = "100",
    val kcal100: String = "", val p100: String = "", val f100: String = "", val c100: String = "",
    val barcode: String? = null, val source: String = "manual",
) {
    companion object {
        fun of(i: FoodItem, source: String) = FoodDraft(i.name, "100", i.kcal100.r1(), i.protein100.r1(), i.fat100.r1(), i.carbs100.r1(), i.barcode, source)
    }
}

@Composable
fun NutritionScreen(modifier: Modifier, quick: Quick?, consumeQuick: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    var day by remember { mutableStateOf(today()) }
    val entries by app.db.food().forDay(day).collectAsState(initial = emptyList())
    val recent by app.db.food().recentItems().collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf<FoodDraft?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var photoResult by remember { mutableStateOf<Pair<List<PhotoItem>, String>?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoHint by remember { mutableStateOf("") }
    var hintDialog by remember { mutableStateOf(false) }

    fun scanBarcode() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { bc ->
                val code = bc.rawValue ?: return@addOnSuccessListener
                busy = "Ищу продукт $code…"
                scope.launch {
                    val item = app.nutrition.lookupBarcode(code)
                    busy = null
                    draft = if (item != null) FoodDraft.of(item, "barcode")
                    else { snack("Продукт не найден в Open Food Facts — введите с упаковки"); FoodDraft(barcode = code, source = "barcode") }
                }
            }
            .addOnFailureListener { snack("Сканер недоступен: ${it.message}") }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && photoFile != null) hintDialog = true
    }
    fun startPhoto() {
        if (!app.claude.hasKey) { snack("Для фото нужен ключ Claude в настройках"); return }
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        photoFile = f
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
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
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { startPhoto() }, Modifier.weight(1f)) { Icon(Icons.Filled.CameraAlt, null); Text(" Фото") }
                OutlinedButton(onClick = { scanBarcode() }, Modifier.weight(1f)) { Icon(Icons.Filled.QrCodeScanner, null); Text(" Код") }
                OutlinedButton(onClick = { draft = FoodDraft() }, Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Text(" Ввод") }
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
                            Muted("${r.kcal100.r0()} ккал/100 г")
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
            scope.launch {
                app.nutrition.add(FoodEntry(day = day, time = System.currentTimeMillis(), meal = meal, name = d.name.ifBlank { "Продукт" }, grams = g,
                    kcal = (d.kcal100.toDoubleOrNull() ?: 0.0) * k, protein = (d.p100.toDoubleOrNull() ?: 0.0) * k,
                    fat = (d.f100.toDoubleOrNull() ?: 0.0) * k, carbs = (d.c100.toDoubleOrNull() ?: 0.0) * k,
                    source = d.source, barcode = d.barcode))
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
                            app.nutrition.add(FoodEntry(day = day, time = System.currentTimeMillis(), meal = meal, name = it0.name, grams = g,
                                kcal = it0.kcal * k, protein = it0.protein * k, fat = it0.fat * k, carbs = it0.carbs * k, source = "photo"))
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
    var d by remember { mutableStateOf(initial) }
    var meal by remember { mutableStateOf(defaultMeal()) }
    var suggestions by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    LaunchedEffect(d.name) {
        suggestions = if (d.name.length >= 2 && initial.name.isBlank()) app.db.food().search(d.name).filter { it.name != d.name }.take(5) else emptyList()
    }
    val g = d.grams.toDoubleOrNull() ?: 0.0
    val kcal = (d.kcal100.toDoubleOrNull() ?: 0.0) * g / 100
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.source == "barcode") "Продукт по штрихкоду" else "Добавить еду") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField1("Название", d.name, { d = d.copy(name = it) })
                suggestions.forEach { s ->
                    Text("↳ ${s.name} (${s.kcal100.r0()} ккал)", Modifier.clickable { d = FoodDraft.of(s, d.source) }.padding(4.dp), color = Z.Lime)
                }
                NumField("Масса порции, г", d.grams, { d = d.copy(grams = it) })
                Muted("На 100 г:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("ккал", d.kcal100, { d = d.copy(kcal100 = it) }, Modifier.weight(1f))
                    NumField("белки", d.p100, { d = d.copy(p100 = it) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("жиры", d.f100, { d = d.copy(f100 = it) }, Modifier.weight(1f))
                    NumField("углев.", d.c100, { d = d.copy(c100 = it) }, Modifier.weight(1f))
                }
                MealChips(meal) { meal = it }
                Text("Итого: ${kcal.r0()} ккал", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(d, meal) }, enabled = d.grams.toDoubleOrNull() != null) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
