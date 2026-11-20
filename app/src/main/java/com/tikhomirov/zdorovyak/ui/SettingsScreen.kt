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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.ai.Msg
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
        ))
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

            SectionTitle("Нормы на день")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("ккал", kcal, { kcal = it }, Modifier.weight(1f), decimal = false)
                NumField("белок", prot, { prot = it }, Modifier.weight(1f), decimal = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("жиры", fat, { fat = it }, Modifier.weight(1f), decimal = false)
                NumField("углеводы", carbs, { carbs = it }, Modifier.weight(1f), decimal = false)
            }
            Muted("Ориентир для снижения веса: дефицит 300–500 ккал от поддержки, белок 1,6–2 г на кг целевого веса.")

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
            Muted("Версия 0.1.0 · данные хранятся только на телефоне")
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
