package com.tikhomirov.zdorovyak.labs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Images
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.ai.Part
import com.tikhomirov.zdorovyak.coach.CoachRepo
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.LabReport
import com.tikhomirov.zdorovyak.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Анализы: PDF/фото бланка → Claude извлекает показатели → таблица с динамикой → ИИ-разбор.
 * Файл копируется во внутреннюю память приложения, чтобы его можно было открыть позже.
 */
class LabsRepo(
    private val ctx: Context,
    private val db: AppDatabase,
    private val claude: ClaudeClient,
    private val settings: Settings,
    private val coach: () -> CoachRepo,
) {
    private val dir get() = File(ctx.filesDir, "labs").apply { mkdirs() }

    data class ImportResult(val reportId: Long, val count: Int, val note: String)

    suspend fun import(uri: Uri): ImportResult {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: "application/pdf"
        val name = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "analiz"
        val isPdf = mime == "application/pdf" || name.endsWith(".pdf", true)
        if (!isPdf && !mime.startsWith("image/")) throw IllegalArgumentException("Нужен PDF или фото бланка")
        val file = File(dir, "${System.currentTimeMillis()}.${if (isPdf) "pdf" else "jpg"}")
        withContext(Dispatchers.IO) {
            cr.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("Не удалось прочитать файл")
        }
        if (file.length() > 24L * 1024 * 1024) { file.delete(); throw IllegalArgumentException("Файл больше 24 МБ — Claude его не примет") }
        val part = withContext(Dispatchers.Default) {
            if (isPdf) Part.Pdf(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)) else Part.Image(Images.jpegBase64(file, 2000))
        }
        val parsed = try { extract(part) } catch (e: Exception) { file.delete(); throw e }
        if (parsed.results.isEmpty()) {
            file.delete()
            throw IllegalStateException(parsed.note.ifBlank { "В документе не нашлось показателей анализов" })
        }
        val day = (parsed.day ?: LocalDate.now()).toEpochDay()
        val id = db.labs().insertReport(LabReport(
            day = day, lab = parsed.lab, title = parsed.title.ifBlank { name.substringBeforeLast('.') },
            filePath = file.absolutePath, mime = if (isPdf) "application/pdf" else "image/jpeg",
        ))
        db.labs().insertResults(parsed.results.map { it.copy(reportId = id, day = day) })
        val note = buildString {
            if (parsed.day == null) append("Дату в бланке не нашёл — поставил сегодняшнюю, её можно поменять. ")
            append(parsed.note)
        }.trim()
        return ImportResult(id, parsed.results.size, note)
    }

    private suspend fun extract(part: Part): LabParser.Parsed {
        val system = """
Ты извлекаешь результаты лабораторных анализов из бланка (лаборатории Казахстана и России: Инвитро, КДЛ Олимп, Гемотест, Хеликс, Sapa и др.).
Перенеси ВСЕ показатели с результатами, ничего не придумывай и не интерпретируй. Если значение в бланке нечитаемо — пропусти его.
Для каждого показателя:
- code: канонический код из списка ниже, если показатель совпадает по смыслу (например, «Холестерин ЛПНП» → ldl, «25-OH витамин D» → vit_d). Если в списке нет — свой короткий код латиницей snake_case (например ana_screen).
- value: число (точка как разделитель). Для «<0,5» или «>100» value = граница числом, а value_text = как в бланке. Для качественных результатов («отрицательно», «не обнаружено») value = null, value_text = текст.
- unit: единица как в бланке. Если единица отличается от указанной в списке и пересчёт однозначен (мг/дл↔ммоль/л для глюкозы и липидов, нг/дл↔нмоль/л для тестостерона, нмоль/л↔нг/мл для витамина D), пересчитай value и границы в единицу из списка и упомяни это в note.
- ref_low / ref_high: границы референса числами, если есть; ref_text: референс как в бланке, если он не сводится к двум числам (например «<5,2» или «отрицательно»).
- flag: H / L / N по пометкам бланка (стрелки, звёздочки, выделение) или сравнению с референсом; ? если неясно.
- category: одна из: Общий анализ крови, Углеводный обмен, Липиды, Биохимия, Минералы и железо, Витамины, Гормоны, Моча, Другое.
date — дата взятия материала (не печати бланка), формат YYYY-MM-DD; если нет — null.
Если документ не про анализы — results пустой и объясни в note.
Верни только JSON:
{"date":"2026-11-15","lab":"Инвитро","title":"Биохимия крови","note":"коротко по-русски, если что-то пересчитал или не смог прочитать",
 "results":[{"code":"glucose","name":"Глюкоза","category":"Углеводный обмен","value":5.4,"value_text":"","unit":"ммоль/л","ref_low":4.1,"ref_high":5.9,"ref_text":"","flag":"N"}]}

Канонические коды [единица]:
${LabParser.canonForPrompt()}
""".trimIndent()
        val o = claude.completeJson(system, listOf(Msg("user", listOf(part, Part.Text("Извлеки все показатели из этого бланка.")))), maxTokens = 8000)
        return LabParser.parse(o, LocalDate.now())
    }

    suspend fun setDate(reportId: Long, day: Long) {
        val r = db.labs().report(reportId) ?: return
        db.labs().updateReport(r.copy(day = day))
        db.labs().moveResults(reportId, day)
    }

    suspend fun delete(reportId: Long) {
        db.labs().report(reportId)?.filePath?.let { runCatching { File(it).delete() } }
        db.labs().deleteResultsFor(reportId)
        db.labs().deleteReport(reportId)
    }

    /** ИИ-разбор отчёта на фоне всей истории анализов и остальных данных приложения. */
    suspend fun interpret(reportId: Long): String {
        val report = db.labs().report(reportId) ?: throw IllegalStateException("Отчёт не найден")
        val current = db.labs().resultsFor(reportId)
        val all = db.labs().resultsNow()
        val p = settings.profile.value
        val currentText = current.joinToString("\n") { r ->
            val ref = LabParser.refDisplay(r).let { if (it.isBlank()) "" else " (норма $it)" }
            "${r.name}: ${LabParser.display(r)} ${r.unit}$ref [${r.flag}]"
        }
        val history = LabTable.summary(all.filter { it.reportId != reportId })
        val system = """
Ты опытный врач-терапевт с подготовкой в спортивной медицине. Объясняешь человеку его анализы простым языком: что в норме, что нет, что это может значить и что обсудить с врачом.
Человек: ${p.name}, ${p.age} лет, пол ${if (p.sex == "F") "женский" else "мужской"}, рост ${p.heightCm} см. Цель: ${p.goal}. Занимается силовыми, следит за питанием.${if (p.limitations.isNotBlank()) " Ограничения: ${p.limitations}." else ""}
Правила:
- Это не диагноз. Не ставь диагнозов и не назначай лекарства и дозировки. Говори «может указывать на», «стоит проверить».
- Отклонение в пределах нескольких процентов от границы и одиночное — часто без значения; так и скажи, не пугай.
- Связывай с данными из приложения (вес и его тренд, питание, тренировки, сон и пульс Garmin), если связь правдоподобна: например, СОЭ/КФК после тяжёлой тренировки, глюкоза и HbA1c на фоне дефицита калорий.
- Учитывай динамику с прошлыми анализами.
- Если есть значения, требующие срочного обращения (например калий > 6, глюкоза натощак > 11, гемоглобин < 90 г/л, тромбоциты < 50), начни с этого прямо и коротко.
Формат: обычный текст без markdown (без #, *, таблиц). Короткие абзацы. Разделы — строкой с двоеточием в конце:
Главное:
Что вне нормы и что это может значить:
Динамика:
Что обсудить с врачом:
Что можно сделать самому:
Объём — до 350 слов.
""".trimIndent()
        val user = """
Анализ от ${LocalDate.ofEpochDay(report.day)}${if (report.lab.isNotBlank()) ", ${report.lab}" else ""}${if (report.title.isNotBlank()) ", ${report.title}" else ""}:
$currentText

Прошлые анализы (последние значения):
${history.ifBlank { "(нет)" }}

Данные из приложения:
${coach().dataSnapshot(includeLabs = false)}
""".trimIndent()
        val text = claude.complete(system, listOf(Msg("user", user)), maxTokens = 2500).trim()
        db.labs().updateReport(report.copy(interpretation = text, interpretedAt = System.currentTimeMillis()))
        return text
    }
}
