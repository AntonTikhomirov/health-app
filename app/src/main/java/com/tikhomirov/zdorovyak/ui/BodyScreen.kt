package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikhomirov.zdorovyak.data.Measurement
import com.tikhomirov.zdorovyak.data.WeightEntry
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch

@Composable
fun BodyScreen(modifier: Modifier, quick: Quick?, consumeQuick: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    val weights by app.db.body().weights().collectAsState(initial = emptyList())
    val measurements by app.db.body().measurements().collectAsState(initial = emptyList())
    var weightDialog by remember { mutableStateOf(false) }
    var measureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(quick) { if (quick == Quick.WEIGHT) { weightDialog = true; consumeQuick() } }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ZCard(accent = Z.Lime) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Вес", fontWeight = FontWeight.SemiBold)
                    Button(onClick = { weightDialog = true }) { Text("Записать") }
                }
                weights.firstOrNull()?.let { w ->
                    Big("${w.weightKg.r1()} кг")
                    val bmi = w.weightKg / Math.pow(profile.heightCm / 100.0, 2.0)
                    Muted("ИМТ ${bmi.r1()}${profile.targetWeightKg?.let { t -> " · до цели ${(w.weightKg - t).r1()} кг" } ?: ""}")
                    // Среднее за 7 записей сглаживает колебания воды
                    if (weights.size >= 3) {
                        val avg7 = weights.take(7).map { it.weightKg }.average()
                        Muted("Среднее последних ${minOf(7, weights.size)} записей: ${avg7.r1()} кг")
                    }
                    Sparkline(weights.take(60).reversed().map { it.weightKg }, Z.Lime, Modifier.fillMaxWidth().height(80.dp))
                } ?: Muted("Пока нет записей. Взвешивайтесь утром натощак — так тренд честнее.")
            }
        }
        item {
            ZCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Замеры, см", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { measureDialog = true }) { Text("Добавить") }
                }
                if (measurements.isEmpty()) Muted("Талия — главный показатель, раз в 1–2 недели.")
                measurements.take(6).forEach { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(m.day))
                        Muted(listOfNotNull(m.waist?.let { "талия ${it.r1()}" }, m.chest?.let { "грудь ${it.r1()}" },
                            m.hips?.let { "бёдра ${it.r1()}" }, m.arm?.let { "рука ${it.r1()}" }, m.thigh?.let { "бедро ${it.r1()}" },
                            m.neck?.let { "шея ${it.r1()}" }).joinToString(" · "), Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(onClick = { scope.launch { app.db.body().deleteMeasurement(m) } }) { Icon(Icons.Filled.Delete, "Удалить", tint = Z.Muted) }
                    }
                }
            }
        }
        item { SectionTitle("История веса") }
        items(weights, key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(w.day), Modifier.weight(1f))
                Text("${w.weightKg.r1()} кг", fontWeight = FontWeight.SemiBold)
                w.bodyFatPct?.let { Muted("  ${it.r1()}% жира") }
                IconButton(onClick = { scope.launch { app.db.body().deleteWeight(w) } }) { Icon(Icons.Filled.Delete, "Удалить", tint = Z.Muted) }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (weightDialog) {
        var w by remember { mutableStateOf(weights.firstOrNull()?.weightKg?.r1() ?: "") }
        var fat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text("Вес сегодня") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Вес, кг", w, { w = it })
                    NumField("% жира (если есть)", fat, { fat = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    w.toDoubleOrNull()?.let { v ->
                        scope.launch { app.db.body().insertWeight(WeightEntry(day = today(), weightKg = v, bodyFatPct = fat.toDoubleOrNull())) }
                    }
                    weightDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Отмена") } },
        )
    }
    if (measureDialog) {
        val f = remember { List(6) { mutableStateOf("") } }
        val labels = listOf("Талия", "Грудь", "Бёдра", "Рука", "Бедро", "Шея")
        AlertDialog(
            onDismissRequest = { measureDialog = false },
            title = { Text("Замеры, см") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { i, l -> NumField(l, f[i].value, { f[i].value = it }) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = f.map { it.value.toDoubleOrNull() }
                    if (v.any { it != null }) scope.launch {
                        app.db.body().insertMeasurement(Measurement(day = today(), waist = v[0], chest = v[1], hips = v[2], arm = v[3], thigh = v[4], neck = v[5]))
                    }
                    measureDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { measureDialog = false }) { Text("Отмена") } },
        )
    }
}
