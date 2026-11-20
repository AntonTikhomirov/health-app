package com.tikhomirov.zdorovyak.nutrition

import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.ai.Part
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.EnergyState
import com.tikhomirov.zdorovyak.data.FoodEntry
import com.tikhomirov.zdorovyak.data.FoodItem
import com.tikhomirov.zdorovyak.data.Profile
import com.tikhomirov.zdorovyak.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class PhotoItem(val name: String, val grams: Double, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)
data class PhotoResult(val items: List<PhotoItem>, val comment: String)

/** Источники данных о продукте, от самого надёжного к наименее. */
object FoodSource {
    const val USER = "user"     // введено руками (с упаковки)
    const val LABEL = "label"   // Claude прочитал этикетку
    const val OFF = "off"       // Open Food Facts
    const val BASE = "base"     // встроенная база
    const val AI = "ai"         // оценка Claude по названию

    fun trust(s: String): Int = when (s) { USER, LABEL -> 4; OFF -> 3; BASE -> 2; AI -> 1; else -> 2 }
    fun label(s: String): String = when (s) {
        USER -> "мой"; LABEL -> "этикетка"; OFF -> "штрихкод"; BASE -> "база"; AI -> "оценка ИИ"; else -> s
    }
}

/** Вариант в поиске: из справочника или из встроенной базы. */
data class FoodOption(val item: FoodItem, val fromHistory: Boolean)

class NutritionRepo(
    private val db: AppDatabase,
    private val claude: ClaudeClient,
    private val settings: Settings,
    baseLoader: () -> FoodBase,
) {
    private val dao = db.food()
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    val base: FoodBase by lazy(baseLoader)
    private val targetsLock = Mutex()

    // ---------- Дневник и справочник ----------

    /**
     * Записать еду. [remember] — продукт на 100 г для справочника (null — не запоминать: так для фото тарелки,
     * где цифры — прикидка по целому блюду). Более надёжный источник не затирается менее надёжным.
     */
    suspend fun add(e: FoodEntry, remember: FoodItem?) {
        dao.insert(e)
        remember?.let { rememberItem(it) }
    }

    suspend fun rememberItem(new: FoodItem) {
        if (new.name.isBlank()) return
        val existing = new.barcode?.let { dao.byBarcode(it) } ?: dao.byName(new.name)
        val now = System.currentTimeMillis()
        if (existing != null && FoodSource.trust(existing.source) > FoodSource.trust(new.source)) {
            dao.touchItem(existing.id, now)
            return
        }
        dao.upsertItem(new.copy(id = existing?.id ?: 0, barcode = new.barcode ?: existing?.barcode, lastUsed = now))
    }

    /** Поиск по названию: сначала свой справочник (кроме оценок ИИ), потом встроенная база, потом оценки ИИ. */
    suspend fun search(query: String, limit: Int = 8): List<FoodOption> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val mine = dao.search(q)
        val trusted = mine.filter { it.source != FoodSource.AI }.map { FoodOption(it, true) }
        val ai = mine.filter { it.source == FoodSource.AI }.map { FoodOption(it, true) }
        val taken = mine.map { FoodBase.norm(it.name) }.toMutableSet()
        val fromBase = base.search(q, limit).filter { taken.add(FoodBase.norm(it.name)) }.map {
            FoodOption(FoodItem(name = it.name, kcal100 = it.kcal100, protein100 = it.protein100, fat100 = it.fat100,
                carbs100 = it.carbs100, source = FoodSource.BASE), false)
        }
        return (trusted.take(4) + fromBase + trusted.drop(4) + ai).take(limit)
    }

    /** Повторить приём пищи с другого дня. */
    suspend fun repeatMeal(fromDay: Long, toDay: Long, meal: String): Int {
        val src = dao.forDayNow(fromDay).filter { it.meal == meal }
        val now = System.currentTimeMillis()
        src.forEachIndexed { i, e -> dao.insert(e.copy(id = 0, day = toDay, time = now + i)) }
        return src.size
    }

    // ---------- Штрихкод ----------

    /** Результат поиска по штрихкоду: продукт или хотя бы название без КБЖУ (тогда предлагаем этикетку). */
    data class BarcodeResult(val item: FoodItem?, val nameOnly: String? = null)

    /** Штрихкод: сначала локальный справочник, затем Open Food Facts. */
    suspend fun lookupBarcode(code: String): BarcodeResult {
        dao.byBarcode(code)?.let { return BarcodeResult(it) }
        val found = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_ru,brands,nutriments")
                .header("User-Agent", "Zdorovyak/0.2 (personal Android app)")
                .build()
            runCatching {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val o = json.parseToJsonElement(r.body!!.string()).jsonObject
                    if (o["status"]?.jsonPrimitive?.content != "1") return@use null
                    val p = o["product"]!!.jsonObject
                    val n = p["nutriments"]?.jsonObject ?: JsonObject(emptyMap())
                    fun num(k: String) = n[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val name = listOfNotNull(
                        p["product_name_ru"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: p["product_name"]?.jsonPrimitive?.content,
                        p["brands"]?.jsonPrimitive?.content?.substringBefore(',')?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { "Продукт $code" }
                    val prot = num("proteins_100g"); val fat = num("fat_100g"); val carbs = num("carbohydrates_100g")
                    var kcal = num("energy-kcal_100g")
                    if (kcal == 0.0) kcal = num("energy_100g") / 4.184
                    if (kcal == 0.0) kcal = prot * 4 + fat * 9 + carbs * 4
                    FoodItem(name = name, kcal100 = kcal, protein100 = prot, fat100 = fat, carbs100 = carbs, barcode = code, source = FoodSource.OFF)
                }
            }.getOrNull()
        } ?: return BarcodeResult(null)
        // Пустые нутриенты в OFF — частая история: не пишем нули в справочник, просим этикетку
        if (found.kcal100 <= 0.0) return BarcodeResult(null, nameOnly = found.name)
        rememberItem(found)
        return BarcodeResult(found)
    }

    // ---------- Claude: этикетка, оценка по названию, фото тарелки ----------

    /** Фото этикетки → пищевая ценность на 100 г. */
    suspend fun readLabel(file: File): FoodItem {
        val b64 = withContext(Dispatchers.Default) { compress(file) }
        val system = """
Ты читаешь упаковку продукта. Найди таблицу «Пищевая ценность» и верни значения НА 100 г (или 100 мл).
Если на упаковке только «на порцию» и указана масса порции — пересчитай на 100 г. Если на 100 г указано явно — бери как есть.
Энергию бери в ккал (не кДж). Название — коротко, как на упаковке, по-русски, с брендом если виден.
Если таблицу прочитать нельзя — верни "ok": false и объясни в note.
Верни только JSON:
{"ok":true,"name":"Творог 5% Простоквашино","kcal":121,"protein":17.2,"fat":5.0,"carbs":1.8,"note":"коротко, если что-то пересчитывал"}
""".trimIndent()
        val o = claude.completeJson(system, listOf(Msg("user", listOf(Part.Image(b64), Part.Text("Прочитай пищевую ценность на 100 г.")))), maxTokens = 600)
        fun d(k: String) = o[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
        if (o["ok"]?.jsonPrimitive?.content == "false" || d("kcal") <= 0.0) {
            throw IllegalStateException(o["note"]?.jsonPrimitive?.content?.ifBlank { null } ?: "Не удалось прочитать этикетку")
        }
        return FoodItem(name = o["name"]?.jsonPrimitive?.content?.trim().orEmpty(), kcal100 = d("kcal"), protein100 = d("protein"),
            fat100 = d("fat"), carbs100 = d("carbs"), source = FoodSource.LABEL)
    }

    /** Оценка КБЖУ на 100 г по названию, когда в базе ничего нет. Помечается как «оценка ИИ». */
    suspend fun estimateByName(name: String): FoodItem {
        val system = """
Ты нутрициолог. Для продукта или блюда дай типичную пищевую ценность на 100 г готового продукта,
ориентируясь на российские таблицы химсостава (Скурихин) и USDA. Кухня — русская, казахская, среднеазиатская.
Верни только JSON: {"name":"уточнённое название","kcal":0,"protein":0,"fat":0,"carbs":0}
""".trimIndent()
        val o = claude.completeJson(system, listOf(Msg("user", listOf(Part.Text(name)))), maxTokens = 300)
        fun d(k: String) = o[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return FoodItem(name = name.trim(), kcal100 = d("kcal"), protein100 = d("protein"), fat100 = d("fat"), carbs100 = d("carbs"),
            source = FoodSource.AI)
    }

    /** Фото тарелки → список блюд с оценкой массы и КБЖУ. */
    suspend fun analyzePhoto(file: File, hint: String): PhotoResult {
        val b64 = withContext(Dispatchers.Default) { compress(file) }
        val system = """
Ты нутрициолог. По фото еды оцени, что на тарелке, массу каждого компонента в граммах и КБЖУ.
Учитывай типичные порции, видимый размер посуды, масло при жарке. Если не уверен — дай наиболее вероятную оценку и скажи об этом в comment.
Верни только JSON:
{"items":[{"name":"гречка отварная","grams":180,"kcal":198,"protein":7.6,"fat":2.0,"carbs":38.0}],"comment":"коротко по-русски: что учтено, где неопределённость"}
Значения kcal/protein/fat/carbs — на указанную массу, не на 100 г.
""".trimIndent()
        val obj = claude.completeJson(system, listOf(Msg("user", listOf(
            Part.Image(b64),
            Part.Text(if (hint.isBlank()) "Что здесь и сколько?" else "Подсказка: $hint"),
        ))), maxTokens = 1500)
        val items = obj["items"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            fun d(k: String) = o[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
            PhotoItem(o["name"]?.jsonPrimitive?.content ?: "Блюдо", d("grams"), d("kcal"), d("protein"), d("fat"), d("carbs"))
        } ?: emptyList()
        return PhotoResult(items, obj["comment"]?.jsonPrimitive?.content ?: "")
    }

    private fun compress(file: File): String = com.tikhomirov.zdorovyak.ai.Images.jpegBase64(file)

    // ---------- Нормы ----------

    fun input(p: Profile, weightKg: Double) = TargetInput(
        sex = runCatching { Sex.valueOf(p.sex) }.getOrDefault(Sex.M),
        ageYears = p.age, heightCm = p.heightCm, weightKg = weightKg, targetWeightKg = p.targetWeightKg,
        activity = runCatching { Activity.valueOf(p.activity) }.getOrDefault(Activity.LIGHT),
        goal = runCatching { GoalMode.valueOf(p.goalMode) }.getOrDefault(GoalMode.LOSE),
        rateKgPerWeek = p.rateKgPerWeek, proteinPerKg = p.proteinPerKg,
    )

    /**
     * Пересчитать нормы: тренд веса → формула; раз в неделю — адаптивный расход по факту.
     * Если в профиле включены авто-нормы, результат записывается в профиль (его читают все экраны и коуч).
     * Безопасно вызывать часто: без изменений ничего не сохраняет.
     */
    suspend fun refreshTargets(today: Long = LocalDate.now().toEpochDay()): Targets? = targetsLock.withLock {
        val p = settings.profile.value
        val weighIns = db.body().weightsSince(today - 180).map { it.day to it.weightKg }
        if (weighIns.isEmpty()) {
            val e = settings.energy.value
            val note = "Добавьте взвешивание на вкладке «Тело» — нормы посчитаются от вашего веса."
            if (e.note != note) settings.saveEnergy(e.copy(note = note))
            return@withLock null
        }
        val weight = NutritionMath.weightTrend(weighIns).last().second
        val inp = input(p, weight)
        val formula = NutritionMath.formulaTdee(inp)
        var e = settings.energy.value
        if (today - e.lastCheckDay >= 7) {
            val intake = dao.kcalByDay(today - NutritionMath.WINDOW_DAYS, today - 1).associate { it.day to it.kcal }
            val prev = if (e.adaptive && e.tdee != null) e.tdee!! else formula
            val a = NutritionMath.adaptiveTdee(intake, weighIns, today, prev, 0.6 * p.kcalTarget, firstTime = !e.adaptive)
            e = if (a.tdee != null) EnergyState(tdee = a.tdee, adaptive = true, lastCheckDay = today, note = a.explanation, formulaTdee = formula)
            else e.copy(note = if (e.adaptive) "Оценка расхода не меняется: ${a.explanation.substringAfter("не хватает: ")}" else a.explanation)
        }
        val tdee = if (e.adaptive && e.tdee != null) e.tdee!! else formula
        val t = NutritionMath.targets(inp, tdee)
        e = e.copy(formulaTdee = formula)
        if (e != settings.energy.value) settings.saveEnergy(e)
        if (p.autoTargets) {
            val np = p.copy(kcalTarget = t.kcal, proteinTarget = t.protein, fatTarget = t.fat, carbsTarget = t.carbs)
            if (np != p) settings.saveProfile(np)
        }
        t
    }
}
