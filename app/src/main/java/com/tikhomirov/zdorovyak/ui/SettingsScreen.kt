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
    var actToTarget by remember { mutableStateOf(profile.activityToTarget) }
    var photoEvery by remember { mutableStateOf(profile.photoEveryDays) }
    val energy by app.settings.energy.collectAsState()
    var gEmail by remember { mutableStateOf(app.settings.garminEmail) }
    var gPass by remember { mutableStateOf(app.settings.garminPassword) }
    var garminBusy by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(app.garmin.isLoggedIn) }
    var mfaWaiter by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf(app.settings.ttsEngine) }
    var ttsWhen by remember { mutableStateOf(app.settings.ttsWhen) }
    var ttsSpeed by remember { mutableStateOf(app.settings.ttsSpeed.toString()) }
    var deviceVoice by remember { mutableStateOf(app.settings.deviceVoice) }
    var deviceVoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var yKey by remember { mutableStateOf(app.settings.yandexKey) }
    var yVoice by remember { mutableStateOf(app.settings.yandexVoice) }
    var gKey by remember { mutableStateOf(app.settings.googleTtsKey) }
    var gVoice by remember { mutableStateOf(app.settings.googleVoice) }
    var autoSend by remember { mutableStateOf(app.settings.voiceAutoSend) }
    androidx.compose.runtime.LaunchedEffect(ttsEngine) {
        if (ttsEngine == "device" && deviceVoices.isEmpty()) deviceVoices = runCatching { app.speaker.deviceVoices() }.getOrDefault(emptyList())
    }

    fun saveVoice() {
        app.settings.ttsEngine = ttsEngine; app.settings.ttsWhen = ttsWhen
        ttsSpeed.toFloatOrNull()?.let { app.settings.ttsSpeed = it }
        app.settings.deviceVoice = deviceVoice
        app.settings.yandexKey = yKey; app.settings.yandexVoice = yVoice
        app.settings.googleTtsKey = gKey; app.settings.googleVoice = gVoice
        app.settings.voiceAutoSend = autoSend
    }

    fun save() {
        saveVoice()
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
            autoTargets = auto, activityToTarget = actToTarget, photoEveryDays = photoEvery,
        ))
        runCatching { com.tikhomirov.zdorovyak.body.PhotoReminder.schedule(app, photoEvery) }
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
            Muted(if (auto && actToTarget) "Образ жизни (без тренировок — они прибавляются к норме в свой день)" else "Активность")
            ChipRow(Activity.entries.map { it.name to it.title }, activity) { activity = it }
            Muted(runCatching { Activity.valueOf(activity).let { if (auto && actToTarget) it.baseHint else it.hint } }.getOrDefault(""))

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Прибавлять активность к норме")
                        Muted("Силовые, работы по дому, тренировки Garmin: +ккал в тот же день, в углеводы")
                    }
                    Switch(checked = actToTarget, onCheckedChange = { actToTarget = it })
                }
                Muted("Базовая норма: ${profile.kcalTarget} ккал · Б ${profile.proteinTarget} · Ж ${profile.fatTarget} · У ${profile.carbsTarget}")
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

            SectionTitle("Фото прогресса")
            Muted("Напоминать сфотографироваться (утром, натощак, при том же свете)")
            ChipRow(listOf("0" to "Не напоминать", "7" to "Раз в неделю", "14" to "Раз в 2 недели", "28" to "Раз в месяц"), photoEvery.toString()) {
                photoEvery = it.toInt()
            }

            SectionTitle("Голос коуча")
            Muted("Диктовка — бесплатное распознавание Google на телефоне (кнопка микрофона в чате). Озвучка ответов:")
            ChipRow(listOf("off" to "Выкл", "device" to "Google на телефоне", "yandex" to "Яндекс (облако)", "google" to "Google Cloud"), ttsEngine) { ttsEngine = it }
            when (ttsEngine) {
                "device" -> {
                    Muted("Бесплатно. Голоса с пометкой «сеть» звучат заметно живее офлайновых, но нужен интернет. Нет русского — Настройки Android → Синтез речи → Google → Установить голосовые данные.")
                    if (deviceVoices.isNotEmpty()) ChipRow(listOf("" to "Авто") + deviceVoices, deviceVoice) { deviceVoice = it }
                }
                "yandex" -> {
                    Muted("Yandex SpeechKit — самый естественный русский. 6 710 ₸ (1 342 ₽) за 1 млн символов: ответ коуча ≈ 800 символов ≈ 5 ₸. Ключ: Yandex Cloud → сервисный аккаунт с ролью ai.speechkit-tts.user → API-ключ.")
                    TextField1("API-ключ SpeechKit", yKey, { yKey = it }, password = true)
                    ChipRow(com.tikhomirov.zdorovyak.voice.YandexTts.VOICES, yVoice) { yVoice = it }
                    Muted("В этом месяце: ${app.settings.cloudCharsThisMonth("yandex")} символов ≈ ${com.tikhomirov.zdorovyak.voice.TtsText.yandexCostKzt(app.settings.cloudCharsThisMonth("yandex")).r0()} ₸")
                }
                "google" -> {
                    Muted("Google Cloud Text-to-Speech: 1 млн символов WaveNet или Chirp 3 HD в месяц бесплатно — для одного человека это обычно 0 ₸. Ключ: Google Cloud → включить Text-to-Speech API → Credentials → API key (нужен платёжный аккаунт).")
                    TextField1("API-ключ Google Cloud", gKey, { gKey = it }, password = true)
                    ChipRow(com.tikhomirov.zdorovyak.voice.GoogleCloudTts.VOICES, gVoice) { gVoice = it }
                    TextField1("Имя голоса", gVoice, { gVoice = it })
                    Muted("В этом месяце: ${app.settings.cloudCharsThisMonth("google")} символов из 1 000 000 бесплатных")
                }
            }
            if (ttsEngine != "off") {
                Muted("Когда озвучивать")
                ChipRow(listOf("voice" to "Если спросил голосом", "always" to "Всегда", "manual" to "Только по кнопке"), ttsWhen) { ttsWhen = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("Скорость", ttsSpeed, { ttsSpeed = it }, Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        saveVoice()
                        app.speaker.speak("Привет, ${name.ifBlank { "Антон" }}. Так звучит голос коуча. Если нравится, оставляем.")
                    }) { Text("Послушать") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отправлять сразу после диктовки", Modifier.weight(1f))
                Switch(checked = autoSend, onCheckedChange = { autoSend = it })
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
