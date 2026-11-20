package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.nutrition.Activity
import com.tikhomirov.zdorovyak.nutrition.GoalMode
import com.tikhomirov.zdorovyak.nutrition.Sex
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val app = LocalApp.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()

    var key by remember { mutableStateOf(app.settings.claudeKey) }
    var model by remember { mutableStateOf(app.settings.claudeModel) }
    var name by remember { mutableStateOf(profile.name) }
    var age by remember { mutableStateOf(profile.age.toString()) }
    var height by remember { mutableStateOf(profile.heightCm.toString()) }
    var target by remember { mutableStateOf(profile.targetWeightKg?.r1() ?: "") }
    var goal by remember { mutableStateOf(profile.goal) }
    var kcal by remember { mutableStateOf(profile.kcalTarget.toString()) }
    var prot by remember { mutableStateOf(profile.proteinTarget.toString()) }
    var fat by remember { mutableStateOf(profile.fatTarget.toString()) }
    var carbs by remember { mutableStateOf(profile.carbsTarget.toString()) }
    var sex by remember { mutableStateOf(profile.sex) }
    var activity by remember { mutableStateOf(profile.activity) }
    var goalMode by remember { mutableStateOf(profile.goalMode) }
    var rate by remember { mutableStateOf(profile.rateKgPerWeek.r1()) }
    var protPerKg by remember { mutableStateOf(profile.proteinPerKg.r1()) }
    var auto by remember { mutableStateOf(profile.autoTargets) }
    val energy by app.settings.energy.collectAsState()
    var gEmail by remember { mutableStateOf(app.settings.garminEmail) }
    var gPass by remember { mutableStateOf(app.settings.garminPassword) }
    var garminBusy by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(app.garmin.isLoggedIn) }
    var mfaWaiter by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun save() {
        app.settings.claudeKey = key
        app.settings.claudeModel = model
        app.settings.saveProfile(profile.copy(
            name = name.ifBlank { "Антон" }, age = age.toIntOrNull() ?: profile.age, heightCm = height.toIntOrNull() ?: profile.heightCm,
            targetWeightKg = target.toDoubleOrNull(), goal = goal,
            kcalTarget = kcal.toIntOrNull() ?: profile.kcalTarget, proteinTarget = prot.toIntOrNull() ?: profile.proteinTarget,
            fatTarget = fat.toIntOrNull() ?: profile.fatTarget, carbsTarget = carbs.toIntOrNull() ?: profile.carbsTarget,
            sex = sex, activity = activity, goalMode = goalMode,
            rateKgPerWeek = rate.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: profile.rateKgPerWeek,
            proteinPerKg = protPerKg.toDoubleOrNull()?.coerceIn(0.8, 2.5) ?: profile.proteinPerKg,
            autoTargets = auto,
        ))
        // Пересчёт норм подхватит AppRoot (следит за профилем и взвешиваниями)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Claude API")
            TextField1("Ключ API (sk-ant-…)", key, { key = it }, password = true)
            TextField1("Модель", model, { model = it })
            Muted("Ключ хранится зашифрованно (Android Keystore) и уходит только на api.anthropic.com.")
            OutlinedButton(enabled = !testing, onClick = {
                save(); testing = true
                scope.launch {
                    runCatching { app.claude.complete("Ответь одним словом.", listOf(Msg("user", "Скажи «работает»")), maxTokens = 20) }
                        .onSuccess { snack("Claude: $it") }.onFailure { snack(it.message ?: "Ошибка") }
                    testing = false
                }
            }) { Text(if (testing) "Проверяю…" else "Проверить ключ") }

            SectionTitle("Профиль")
            TextField1("Имя", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("Возраст", age, { age = it }, Modifier.weight(1f), decimal = false)
                NumField("Рост, см", height, { height = it }, Modifier.weight(1f), decimal = false)
                NumField("Цель, кг", target, { target = it }, Modifier.weight(1f))
            }
            TextField1("Цель словами", goal, { goal = it })

            Muted("Пол")
            ChipRow(Sex.entries.map { it.name to it.title }, sex) { sex = it }
            Muted("Активность")
            ChipRow(Activity.entries.map { it.name to it.title }, activity) { activity = it }
            Muted(runCatching { Activity.valueOf(activity).hint }.getOrDefault(""))

            SectionTitle("Нормы КБЖУ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Считать автоматически", Modifier.weight(1f))
                Switch(checked = auto, onCheckedChange = { auto = it })
            }
            if (auto) {
                ChipRow(GoalMode.entries.map { it.name to it.title }, goalMode) { goalMode = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (goalMode != GoalMode.MAINTAIN.name) NumField("Темп, кг/нед", rate, { rate = it }, Modifier.weight(1f))
                    NumField("Белок, г/кг", protPerKg, { protPerKg = it }, Modifier.weight(1f))
                }
                Muted("Сейчас: ${profile.kcalTarget} ккал · Б ${profile.proteinTarget} · Ж ${profile.fatTarget} · У ${profile.carbsTarget}")
                Muted("Старт — по формуле Миффлина–Сан Жеора от тренда веса. Когда наберётся 10 полных дней питания и 4 взвешивания за 3 недели, расход раз в неделю уточняется по факту: сколько съели и как менялся вес. Безопасный темп похудения 0,5–0,7 % веса в неделю; белок 1,6–2 г/кг для силовых после 40.")
                if (energy.note.isNotBlank()) Muted(energy.note)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("ккал", kcal, { kcal = it }, Modifier.weight(1f), decimal = false)
                    NumField("белок", prot, { prot = it }, Modifier.weight(1f), decimal = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("жиры", fat, { fat = it }, Modifier.weight(1f), decimal = false)
                    NumField("углеводы", carbs, { carbs = it }, Modifier.weight(1f), decimal = false)
                }
            }

            SectionTitle("Garmin Connect")
            Muted("Вход через неофициальный протокол мобильного приложения Garmin (как в python-библиотеке garminconnect). Пароль хранится зашифрованно и нужен только для входа; дальше работают токены. Garmin может сломать этот способ в любой момент.")
            if (loggedIn) {
                Text("Подключено. Последняя синхронизация: ${app.settings.garminLastSync.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "не было"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = garminBusy == null, onClick = {
                        garminBusy = "Синхронизация…"
                        scope.launch {
                            runCatching { app.garmin.sync(14) }.onSuccess(snack).onFailure { snack(it.message ?: "Ошибка") }
                            garminBusy = null
                        }
                    }) { Text("Синхронизировать 14 дней") }
                    OutlinedButton(onClick = { app.garmin.logout(); loggedIn = false }) { Text("Выйти") }
                }
            } else {
                TextField1("E-mail Garmin", gEmail, { gEmail = it })
                TextField1("Пароль", gPass, { gPass = it }, password = true)
                Button(enabled = garminBusy == null && gEmail.isNotBlank() && gPass.isNotBlank(), onClick = {
                    app.settings.garminEmail = gEmail; app.settings.garminPassword = gPass
                    garminBusy = "Вход…"
                    scope.launch {
                        runCatching {
                            app.garmin.login(gEmail, gPass) {
                                val d = CompletableDeferred<String?>()
                                mfaWaiter = d
                                d.await()
                            }
                            garminBusy = "Синхронизация…"
                            app.garmin.sync(14)
                        }.onSuccess { snack(it); loggedIn = true }.onFailure { snack(it.message ?: "Ошибка входа") }
                        garminBusy = null
                    }
                }) { Text("Войти в Garmin") }
            }
            garminBusy?.let { Muted(it) }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { save(); snack("Сохранено"); onClose() }, Modifier.fillMaxWidth()) { Text("Сохранить") }
            Muted("Версия ${com.tikhomirov.zdorovyak.BuildConfig.VERSION_NAME} · данные хранятся только на телефоне")
            Spacer(Modifier.height(40.dp))
        }
    }

    mfaWaiter?.let { d ->
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { d.complete(null); mfaWaiter = null },
            title = { Text("Код Garmin") },
            text = { NumField("Код из письма / SMS", code, { code = it }, decimal = false) },
            confirmButton = { TextButton(onClick = { d.complete(code); mfaWaiter = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { d.complete(null); mfaWaiter = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, t) -> FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(t) }) }
    }
}
