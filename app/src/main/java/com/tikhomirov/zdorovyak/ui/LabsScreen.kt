package com.tikhomirov.zdorovyak.ui

import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tikhomirov.zdorovyak.data.LabReport
import com.tikhomirov.zdorovyak.data.LabResult
import com.tikhomirov.zdorovyak.labs.LabParser
import com.tikhomirov.zdorovyak.labs.LabRow
import com.tikhomirov.zdorovyak.labs.LabTable
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yy", ru)
private fun labDate(day: Long) = LocalDate.ofEpochDay(day).format(dateFmt)
private fun flagColor(f: String): Color = when (f) { "H", "L" -> Z.Orange; "N" -> Z.Text; else -> Z.Muted }
private fun flagMark(f: String) = when (f) { "H" -> " ↑"; "L" -> " ↓"; else -> "" }

const val LAB_DISCLAIMER = "Не диагноз. Разбор ИИ помогает подготовиться к разговору с врачом, но не заменяет его. Отклонения обсудите с врачом."

@Composable
fun LabsScreen(onClose: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reports by app.db.labs().reports().collectAsState(initial = emptyList())
    val results by app.db.labs().results().collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var onlyOut by remember { mutableStateOf(false) }
    var rowOpen by remember { mutableStateOf<LabRow?>(null) }
    var reportOpen by remember { mutableStateOf<LabReport?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    fun import(uri: android.net.Uri, cleanup: () -> Unit = {}) {
        if (!app.claude.hasKey) { snack("Нужен ключ Claude в настройках"); cleanup(); return }
        busy = "Claude читает бланк… обычно 20–60 секунд"
        scope.launch {
            runCatching { app.labs.import(uri) }
                .onSuccess { r ->
                    snack("Готово: ${r.count} показателей" + if (r.note.isNotBlank()) ". ${r.note}" else "")
                    reportOpen = app.db.labs().report(r.reportId)
                }
                .onFailure { snack(it.message ?: "Не удалось разобрать документ") }
            busy = null; cleanup()
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { import(it) } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = photoFile ?: return@rememberLauncherForActivityResult
        if (ok) import(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) { f.delete() } else f.delete()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Анализы", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = busy == null, onClick = { pick.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)) { Text("PDF или фото") }
                OutlinedButton(enabled = busy == null, onClick = {
                    val (f, uri) = newPhotoFile(ctx, "lab"); photoFile = f; takePhoto.launch(uri)
                }, modifier = Modifier.weight(1f)) { Text("Снять бланк") }
            }
            busy?.let { Muted(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Динамика") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Документы (${reports.size})") })
                if (tab == 0) FilterChip(selected = onlyOut, onClick = { onlyOut = !onlyOut }, label = { Text("Вне нормы") })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (reports.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                ZCard {
                    Text("Загрузите PDF из лаборатории (Инвитро, Олимп, Sapa и др.) или сфотографируйте бланк. Claude перенесёт показатели в таблицу, а при повторных анализах здесь появится динамика.")
                    Muted("Коуч тоже видит анализы и учитывает их в разговоре.")
                    Muted(LAB_DISCLAIMER)
                }
            }
        } else if (tab == 0) {
            DynamicsTable(results, onlyOut) { rowOpen = it }
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reports, key = { it.id }) { r ->
                    val rr = results.filter { it.reportId == r.id }
                    val out = rr.count { it.flag == "H" || it.flag == "L" }
                    ZCard(Modifier.clickable { reportOpen = r }, accent = if (out > 0) Z.Orange else null) {
                        Text(listOf(labDate(r.day), r.lab).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.SemiBold)
                        if (r.title.isNotBlank()) Muted(r.title)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${rr.size} показ.", Z.Lime)
                            if (out > 0) Pill("$out вне нормы", Z.Orange)
                            if (r.interpretation != null) Pill("есть разбор", Z.Blue)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    rowOpen?.let { row -> RowDialog(row) { rowOpen = null } }
    reportOpen?.let { rep ->
        // Свежая версия отчёта из базы (после разбора/смены даты)
        val live = reports.firstOrNull { it.id == rep.id } ?: rep
        ReportDialog(live, results.filter { it.reportId == live.id }, onClose = { reportOpen = null })
    }
}

@Composable
private fun DynamicsTable(results: List<LabResult>, onlyOut: Boolean, onRow: (LabRow) -> Unit) {
    val allRows = remember(results) { LabTable.rows(results) }
    val rows = if (onlyOut) allRows.filter { it.latest.flag == "H" || it.latest.flag == "L" } else allRows
    val days = remember(results) { LabTable.days(results) }
    val hScroll = rememberScrollState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Показатель", Modifier.width(150.dp), color = Z.Muted, fontSize = 12.sp)
                HeaderCells(days, hScroll)
            }
        }
        if (rows.isEmpty()) item { Muted("Всё в пределах референсов лаборатории.", Modifier.padding(8.dp)) }
        var lastCat = ""
        rows.forEach { row ->
            if (row.category != lastCat) {
                lastCat = row.category
                item(key = "cat_${row.category}") { SectionTitle(row.category) }
            }
            item(key = row.code) {
                Row(Modifier.fillMaxWidth().clickable { onRow(row) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(150.dp).padding(end = 6.dp)) {
                        Text(row.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (row.unit.isNotBlank()) Text(row.unit, fontSize = 11.sp, color = Z.Muted)
                    }
                    Row(Modifier.horizontalScroll(hScroll)) {
                        days.forEach { d ->
                            val r = row.byDay[d]
                            Text(r?.let { LabParser.display(it) + flagMark(it.flag) } ?: "·", Modifier.width(78.dp),
                                color = r?.let { flagColor(it.flag) } ?: Z.Line, fontSize = 14.sp,
                                fontWeight = if (r != null && r.flag != "N" && r.flag != "?") FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        item { Muted(LAB_DISCLAIMER, Modifier.padding(vertical = 12.dp)) }
    }
}

@Composable
private fun HeaderCells(days: List<Long>, hScroll: ScrollState) {
    Row(Modifier.horizontalScroll(hScroll)) {
        days.forEach { Text(labDate(it), Modifier.width(78.dp), color = Z.Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun RowDialog(row: LabRow, onClose: () -> Unit) {
    val points = row.byDay.entries.sortedBy { it.key }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(row.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val nums = points.mapNotNull { it.value.value }
                if (nums.size >= 2) Sparkline(nums, Z.Lime, Modifier.fillMaxWidth().height(70.dp))
                points.reversed().forEach { (d, r) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labDate(d))
                        Text("${LabParser.display(r)} ${r.unit}${flagMark(r.flag)}", color = flagColor(r.flag), fontWeight = FontWeight.SemiBold)
                    }
                    LabParser.refDisplay(r).takeIf { it.isNotBlank() }?.let { Muted("норма лаборатории: $it") }
                }
                Muted("Референсы у лабораторий разные: сравнивайте динамику, а не одно значение.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun ReportDialog(report: LabReport, results: List<LabResult>, onClose: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var interpreting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<LabResult?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(listOf(labDate(report.day), report.lab).filter { it.isNotBlank() }.joinToString(" · ")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.title.isNotBlank()) Muted(report.title)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val d = LocalDate.ofEpochDay(report.day)
                        DatePickerDialog(ctx, { _, y, m, dd -> scope.launch { app.labs.setDate(report.id, LocalDate.of(y, m + 1, dd).toEpochDay()) } },
                            d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }) { Text("Дата") }
                    if (report.filePath.isNotBlank() && File(report.filePath).exists()) TextButton(onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(report.filePath))
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, report.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { snack("Нет приложения, чтобы открыть файл") }
                    }) { Text("Файл") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = Z.Red) }
                }

                ZCard(accent = Z.Blue) {
                    Text("Разбор ИИ", fontWeight = FontWeight.SemiBold)
                    Pill("Не диагноз — обсудите с врачом", Z.Orange)
                    when {
                        interpreting -> Muted("Claude сопоставляет с прошлыми анализами, весом, питанием, тренировками и сном…")
                        report.interpretation != null -> Text(report.interpretation, fontSize = 14.sp)
                        else -> Muted("Разбор учтёт прошлые анализы и данные приложения.")
                    }
                    OutlinedButton(enabled = !interpreting, onClick = {
                        interpreting = true
                        scope.launch {
                            runCatching { app.labs.interpret(report.id) }.onFailure { snack(it.message ?: "Ошибка") }
                            interpreting = false
                        }
                    }) { Text(if (report.interpretation == null) "Разобрать" else "Разобрать заново") }
                }

                SectionTitle("Показатели · нажмите, чтобы поправить")
                results.sortedWith(compareBy({ LabParser.CATEGORY_ORDER.indexOf(it.category) }, { it.id })).forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { edit = r }.padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, Modifier.weight(1f), fontSize = 13.sp)
                        Text("${LabParser.display(r)} ${r.unit}${flagMark(r.flag)}", color = flagColor(r.flag), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить анализ?") },
        text = { Text("Удалятся документ и все его показатели.") },
        confirmButton = { TextButton(onClick = { scope.launch { app.labs.delete(report.id) }; confirmDelete = false; onClose() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
    edit?.let { r ->
        var value by remember(r.id) { mutableStateOf(r.value?.toString() ?: "") }
        var text by remember(r.id) { mutableStateOf(r.valueText) }
        var low by remember(r.id) { mutableStateOf(r.refLow?.toString() ?: "") }
        var high by remember(r.id) { mutableStateOf(r.refHigh?.toString() ?: "") }
        var unit by remember(r.id) { mutableStateOf(r.unit) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(r.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumField("Значение", value, { value = it }, Modifier.weight(1f))
                        TextField1("Ед.", unit, { unit = it }, Modifier.weight(1f))
                    }
                    TextField1("Текстом (если не число)", text, { text = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumField("Норма от", low, { low = it }, Modifier.weight(1f))
                        NumField("до", high, { high = it }, Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.toDoubleOrNull(); val lo = low.toDoubleOrNull(); val hi = high.toDoubleOrNull()
                    scope.launch {
                        app.db.labs().updateResult(r.copy(value = v, valueText = text, unit = unit, refLow = lo, refHigh = hi,
                            flag = LabParser.flag(v, lo, hi, r.flag)))
                    }
                    edit = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { app.db.labs().deleteResult(r) }; edit = null }) { Text("Удалить строку", color = Z.Red) }
            },
        )
    }
}
