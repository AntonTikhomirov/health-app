package com.tikhomirov.zdorovyak.labs

import com.tikhomirov.zdorovyak.data.LabResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Разбор ответа Claude по бланку анализов. Без Android — проверяется обычными юнит-тестами. */
object LabParser {

    data class Parsed(val day: LocalDate?, val lab: String, val title: String, val results: List<LabResult>, val note: String)

    /** Канонические показатели: код → (название, категория, единица по умолчанию). Нужны, чтобы сводить разные лаборатории. */
    val CANON: Map<String, Triple<String, String, String>> = linkedMapOf(
        // Общий анализ крови
        "hgb" to Triple("Гемоглобин", CBC, "г/л"),
        "rbc" to Triple("Эритроциты", CBC, "×10¹²/л"),
        "hct" to Triple("Гематокрит", CBC, "%"),
        "mcv" to Triple("Средний объём эритроцита (MCV)", CBC, "фл"),
        "mch" to Triple("Среднее содержание Hb в эритроците (MCH)", CBC, "пг"),
        "mchc" to Triple("Средняя концентрация Hb (MCHC)", CBC, "г/л"),
        "rdw" to Triple("Ширина распределения эритроцитов (RDW)", CBC, "%"),
        "plt" to Triple("Тромбоциты", CBC, "×10⁹/л"),
        "wbc" to Triple("Лейкоциты", CBC, "×10⁹/л"),
        "neut_pct" to Triple("Нейтрофилы, %", CBC, "%"),
        "lymph_pct" to Triple("Лимфоциты, %", CBC, "%"),
        "mono_pct" to Triple("Моноциты, %", CBC, "%"),
        "eos_pct" to Triple("Эозинофилы, %", CBC, "%"),
        "baso_pct" to Triple("Базофилы, %", CBC, "%"),
        "esr" to Triple("СОЭ", CBC, "мм/ч"),
        // Углеводный обмен
        "glucose" to Triple("Глюкоза", GLUC, "ммоль/л"),
        "hba1c" to Triple("Гликированный гемоглобин HbA1c", GLUC, "%"),
        "insulin" to Triple("Инсулин", GLUC, "мкЕд/мл"),
        "homa_ir" to Triple("Индекс HOMA-IR", GLUC, ""),
        // Липиды
        "chol_total" to Triple("Холестерин общий", LIPIDS, "ммоль/л"),
        "ldl" to Triple("ЛПНП", LIPIDS, "ммоль/л"),
        "hdl" to Triple("ЛПВП", LIPIDS, "ммоль/л"),
        "tg" to Triple("Триглицериды", LIPIDS, "ммоль/л"),
        "non_hdl" to Triple("Холестерин не-ЛПВП", LIPIDS, "ммоль/л"),
        "apob" to Triple("Аполипопротеин B", LIPIDS, "г/л"),
        "lpa" to Triple("Липопротеин(а)", LIPIDS, "нмоль/л"),
        // Печень, почки, белки
        "alt" to Triple("АЛТ", BIOCHEM, "Ед/л"),
        "ast" to Triple("АСТ", BIOCHEM, "Ед/л"),
        "ggt" to Triple("ГГТ", BIOCHEM, "Ед/л"),
        "alp" to Triple("Щелочная фосфатаза", BIOCHEM, "Ед/л"),
        "bili_total" to Triple("Билирубин общий", BIOCHEM, "мкмоль/л"),
        "bili_direct" to Triple("Билирубин прямой", BIOCHEM, "мкмоль/л"),
        "protein_total" to Triple("Общий белок", BIOCHEM, "г/л"),
        "albumin" to Triple("Альбумин", BIOCHEM, "г/л"),
        "creatinine" to Triple("Креатинин", BIOCHEM, "мкмоль/л"),
        "egfr" to Triple("СКФ (eGFR)", BIOCHEM, "мл/мин/1,73м²"),
        "urea" to Triple("Мочевина", BIOCHEM, "ммоль/л"),
        "uric_acid" to Triple("Мочевая кислота", BIOCHEM, "мкмоль/л"),
        "ck" to Triple("Креатинкиназа (КФК)", BIOCHEM, "Ед/л"),
        "ldh" to Triple("ЛДГ", BIOCHEM, "Ед/л"),
        "crp" to Triple("С-реактивный белок", BIOCHEM, "мг/л"),
        "hs_crp" to Triple("hs-СРБ (высокочувствительный)", BIOCHEM, "мг/л"),
        "homocysteine" to Triple("Гомоцистеин", BIOCHEM, "мкмоль/л"),
        // Электролиты и минералы
        "potassium" to Triple("Калий", MINERALS, "ммоль/л"),
        "sodium" to Triple("Натрий", MINERALS, "ммоль/л"),
        "chloride" to Triple("Хлор", MINERALS, "ммоль/л"),
        "calcium" to Triple("Кальций общий", MINERALS, "ммоль/л"),
        "calcium_ion" to Triple("Кальций ионизированный", MINERALS, "ммоль/л"),
        "magnesium" to Triple("Магний", MINERALS, "ммоль/л"),
        "phosphorus" to Triple("Фосфор", MINERALS, "ммоль/л"),
        "iron" to Triple("Железо", MINERALS, "мкмоль/л"),
        "ferritin" to Triple("Ферритин", MINERALS, "нг/мл"),
        "transferrin" to Triple("Трансферрин", MINERALS, "г/л"),
        "tibc" to Triple("ОЖСС", MINERALS, "мкмоль/л"),
        "tsat" to Triple("Насыщение трансферрина", MINERALS, "%"),
        "zinc" to Triple("Цинк", MINERALS, "мкмоль/л"),
        // Витамины
        "vit_d" to Triple("Витамин D (25-OH)", VITAMINS, "нг/мл"),
        "b12" to Triple("Витамин B12", VITAMINS, "пг/мл"),
        "folate" to Triple("Фолиевая кислота", VITAMINS, "нг/мл"),
        // Гормоны
        "tsh" to Triple("ТТГ", HORMONES, "мМЕ/л"),
        "ft4" to Triple("Т4 свободный", HORMONES, "пмоль/л"),
        "ft3" to Triple("Т3 свободный", HORMONES, "пмоль/л"),
        "anti_tpo" to Triple("Антитела к ТПО", HORMONES, "МЕ/мл"),
        "testosterone" to Triple("Тестостерон общий", HORMONES, "нмоль/л"),
        "testosterone_free" to Triple("Тестостерон свободный", HORMONES, "пг/мл"),
        "shbg" to Triple("ГСПГ (SHBG)", HORMONES, "нмоль/л"),
        "estradiol" to Triple("Эстрадиол", HORMONES, "пмоль/л"),
        "lh" to Triple("ЛГ", HORMONES, "мМЕ/мл"),
        "fsh" to Triple("ФСГ", HORMONES, "мМЕ/мл"),
        "prolactin" to Triple("Пролактин", HORMONES, "мМЕ/л"),
        "cortisol" to Triple("Кортизол", HORMONES, "нмоль/л"),
        "dhea_s" to Triple("ДГЭА-С", HORMONES, "мкмоль/л"),
        "igf1" to Triple("ИФР-1 (соматомедин С)", HORMONES, "нг/мл"),
        "psa_total" to Triple("ПСА общий", HORMONES, "нг/мл"),
        "psa_free" to Triple("ПСА свободный", HORMONES, "нг/мл"),
        // Моча
        "urine_protein" to Triple("Белок в моче", URINE, "г/л"),
        "urine_glucose" to Triple("Глюкоза в моче", URINE, "ммоль/л"),
        "urine_sg" to Triple("Удельный вес мочи", URINE, ""),
        "urine_ph" to Triple("pH мочи", URINE, ""),
        "urine_wbc" to Triple("Лейкоциты в моче", URINE, "в п/з"),
        "urine_rbc" to Triple("Эритроциты в моче", URINE, "в п/з"),
        "acr" to Triple("Альбумин/креатинин мочи", URINE, "мг/ммоль"),
    )

    const val CBC = "Общий анализ крови"
    const val GLUC = "Углеводный обмен"
    const val LIPIDS = "Липиды"
    const val BIOCHEM = "Биохимия"
    const val MINERALS = "Минералы и железо"
    const val VITAMINS = "Витамины"
    const val HORMONES = "Гормоны"
    const val URINE = "Моча"
    const val OTHER = "Другое"
    val CATEGORY_ORDER = listOf(CBC, GLUC, LIPIDS, BIOCHEM, MINERALS, VITAMINS, HORMONES, URINE, OTHER)

    /** Строка для промпта: какие коды и единицы ждём. */
    fun canonForPrompt(): String = CANON.entries.joinToString("\n") { (k, v) -> "$k — ${v.first} [${v.third}]" }

    fun num(e: JsonElement?): Double? {
        if (e == null || e is JsonNull) return null
        val p = (e as? JsonPrimitive) ?: return null
        p.content.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()?.let { return it }
        return null
    }

    private fun str(o: JsonObject, k: String): String =
        (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim().orEmpty()

    /** Флаг считаем сами, если есть число и границы; иначе берём оценку модели. */
    fun flag(value: Double?, low: Double?, high: Double?, modelFlag: String): String {
        if (value != null && (low != null || high != null)) {
            if (low != null && value < low) return "L"
            if (high != null && value > high) return "H"
            return "N"
        }
        return when (modelFlag.uppercase()) { "L", "LOW" -> "L"; "H", "HIGH" -> "H"; "N", "NORMAL" -> "N"; else -> "?" }
    }

    fun normalizeCode(raw: String): String =
        raw.lowercase().trim().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "unknown" }

    fun parse(root: JsonObject, fallbackDay: LocalDate): Parsed {
        val day = runCatching { LocalDate.parse(str(root, "date")) }.getOrNull()
        val d = (day ?: fallbackDay).toEpochDay()
        val items = runCatching { root["results"]!!.jsonArray }.getOrNull().orEmpty()
        val results = items.mapNotNull { el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val code = normalizeCode(str(o, "code"))
            val canon = CANON[code]
            val name = str(o, "name").ifBlank { canon?.first ?: code }
            val value = num(o["value"])
            val valueText = str(o, "value_text").ifBlank { (o["value"] as? JsonPrimitive)?.content?.takeIf { value == null && it != "null" }.orEmpty() }
            if (value == null && valueText.isBlank()) return@mapNotNull null
            val low = num(o["ref_low"]); val high = num(o["ref_high"])
            val category = str(o, "category").takeIf { it in CATEGORY_ORDER } ?: canon?.second ?: OTHER
            LabResult(
                reportId = 0, day = d, code = code, name = name, category = category,
                value = value, valueText = valueText, unit = str(o, "unit").ifBlank { canon?.third.orEmpty() },
                refLow = low, refHigh = high, refText = str(o, "ref_text"),
                flag = flag(value, low, high, str(o, "flag")),
            )
        }
        return Parsed(day, str(root, "lab"), str(root, "title"), results, str(root, "note"))
    }

    /** Значение для таблицы: число без лишних нулей или текст из бланка. */
    fun display(r: LabResult): String = r.value?.let {
        if (it == Math.floor(it) && kotlin.math.abs(it) < 1e7) it.toLong().toString()
        else "%.3f".format(java.util.Locale.ROOT, it).trimEnd('0').trimEnd('.').replace('.', ',')
    } ?: r.valueText

    fun refDisplay(r: LabResult): String = when {
        r.refText.isNotBlank() -> r.refText
        r.refLow != null && r.refHigh != null -> "${fmtN(r.refLow)}–${fmtN(r.refHigh)}"
        r.refLow != null -> "> ${fmtN(r.refLow)}"
        r.refHigh != null -> "< ${fmtN(r.refHigh)}"
        else -> ""
    }

    private fun fmtN(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
}

/** Строка таблицы динамики: один показатель, значения по датам (последнее значение за день). */
data class LabRow(val code: String, val name: String, val category: String, val unit: String, val byDay: Map<Long, LabResult>) {
    val latest: LabResult get() = byDay.maxBy { it.key }.value
    val previous: LabResult? get() = byDay.entries.sortedByDescending { it.key }.getOrNull(1)?.value
}

object LabTable {
    fun rows(results: List<LabResult>): List<LabRow> =
        results.groupBy { it.code }.map { (code, list) ->
            val last = list.maxWith(compareBy({ it.day }, { it.id }))
            LabRow(
                code = code,
                name = LabParser.CANON[code]?.first ?: last.name,
                category = last.category,
                unit = last.unit,
                byDay = list.sortedWith(compareBy({ it.day }, { it.id })).associateBy { it.day },
            )
        }.sortedWith(compareBy({ LabParser.CATEGORY_ORDER.indexOf(it.category).let { i -> if (i < 0) 99 else i } },
            { LabParser.CANON.keys.indexOf(it.code).let { i -> if (i < 0) 999 else i } }, { it.name }))

    fun days(results: List<LabResult>): List<Long> = results.map { it.day }.distinct().sortedDescending()

    /** Компактная сводка для коуча и для интерпретации: последнее значение, референс, флаг, прошлое значение. */
    fun summary(results: List<LabResult>, maxLines: Int = 80): String {
        val rows = rows(results)
        if (rows.isEmpty()) return ""
        return rows.take(maxLines).joinToString("\n") { r ->
            val l = r.latest
            val prev = r.previous?.let { p -> "; ранее ${LabParser.display(p)} (${java.time.LocalDate.ofEpochDay(p.day)})" }.orEmpty()
            val ref = LabParser.refDisplay(l).let { if (it.isBlank()) "" else ", норма $it" }
            val fl = when (l.flag) { "H" -> " ↑ВЫШЕ"; "L" -> " ↓НИЖЕ"; else -> "" }
            "${r.name}: ${LabParser.display(l)} ${r.unit}$ref$fl (${java.time.LocalDate.ofEpochDay(l.day)})$prev"
        }
    }
}
