package com.tikhomirov.zdorovyak.coach

import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.ChatMessage
import com.tikhomirov.zdorovyak.data.Settings
import com.tikhomirov.zdorovyak.strength.Catalog
import com.tikhomirov.zdorovyak.strength.fmt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ИИ-коуч/психолог. Память двух уровней:
 * 1) последние 30 сообщений чата идут в запрос как есть;
 * 2) долговременная выжимка (факты, цели, триггеры, договорённости) хранится зашифрованно
 *    и переписывается ИИ каждые 10 новых сообщений.
 * Плюс свежая сводка данных из приложения (питание, вес, тренировки, Garmin).
 */
class CoachRepo(private val db: AppDatabase, private val claude: ClaudeClient, private val settings: Settings) {

    suspend fun send(text: String): String {
        db.chat().insert(ChatMessage(time = System.currentTimeMillis(), role = "user", content = text))
        val history = db.chat().last(30)
        val msgs = mergeRoles(history.map { Msg(it.role, it.content) })
        val reply = claude.complete(systemPrompt(dataSnapshot()), msgs, maxTokens = 1500)
        db.chat().insert(ChatMessage(time = System.currentTimeMillis(), role = "assistant", content = reply))
        maybeUpdateMemory()
        return reply
    }

    /** API требует чередования ролей и начала с user. */
    private fun mergeRoles(list: List<Msg>): List<Msg> {
        val out = mutableListOf<Msg>()
        list.dropWhile { it.role != "user" }.forEach { m ->
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) {
                out[out.size - 1] = Msg(m.role, (last.parts + m.parts))
            } else out += m
        }
        return out
    }

    private fun systemPrompt(snapshot: String): String {
        val p = settings.profile.value
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE"))
        return """
Ты личный коуч и психолог-консультант для ${p.name} (${p.age} лет). Сегодня $today.
Подход: когнитивно-поведенческая терапия, мотивационное интервьюирование, коучинг привычек. Тёплый, прямой, без сюсюканья и без шаблонных фраз.
Что ты делаешь:
- помогаешь разбираться с мыслями, эмоциями, стрессом, срывами в питании, мотивацией к тренировкам;
- опираешься на реальные данные ниже (сон, Body Battery, стресс, питание, вес, тренировки) и замечаешь связи, но не превращаешь каждый ответ в отчёт;
- задаёшь один точный вопрос, а не пять; предлагаешь маленькие конкретные шаги;
- помнишь прошлые разговоры по памяти ниже и возвращаешься к договорённостям.
Границы: ты не врач и не заменяешь психотерапевта. Не ставишь диагнозов. Если звучат мысли о самоповреждении, суициде, признаки тяжёлой депрессии, панических атак, зависимости или медицинские симптомы — мягко и прямо рекомендуй обратиться к специалисту (в Казахстане: телефон доверия 150, экстренно 112) и не продолжай как обычно.
Отвечай по-русски, коротко: 2–6 абзацев максимум, без заголовков, без эмодзи.

Долговременная память о человеке:
${settings.coachMemory.ifBlank { "(пока пусто)" }}

Данные из приложения:
$snapshot
""".trimIndent()
    }

    suspend fun dataSnapshot(): String {
        val today = LocalDate.now().toEpochDay()
        val p = settings.profile.value
        val sb = StringBuilder()
        val weights = db.body().lastWeights(10)
        if (weights.isNotEmpty()) sb.appendLine("Вес: " + weights.joinToString { "${LocalDate.ofEpochDay(it.day)} ${fmt(it.weightKg)}" })
        db.body().lastMeasurements(2).firstOrNull()?.let { m ->
            sb.appendLine("Замеры ${LocalDate.ofEpochDay(m.day)}: талия ${m.waist ?: "-"}, грудь ${m.chest ?: "-"}, бёдра ${m.hips ?: "-"}")
        }
        val food = db.food().range(today - 6, today).groupBy { it.day }
        if (food.isNotEmpty()) {
            sb.appendLine("Питание (цель ${p.kcalTarget} ккал, белок ${p.proteinTarget} г):")
            food.toSortedMap().forEach { (d, list) ->
                sb.appendLine("  ${LocalDate.ofEpochDay(d)}: ${list.sumOf { it.kcal }.roundToInt()} ккал, Б ${list.sumOf { it.protein }.roundToInt()} г, записей ${list.size}")
            }
        }
        val workouts = db.training().lastWorkouts(6)
        if (workouts.isNotEmpty()) {
            sb.appendLine("Силовые: " + workouts.joinToString { "${LocalDate.ofEpochDay(it.day)} день ${it.dayName}${if (it.calibration) " (прикидка)" else ""}" })
            workouts.firstOrNull()?.let { w ->
                val fb = db.training().feedbackFor(w.id)
                if (fb.isNotEmpty()) sb.appendLine("  Последняя: " + fb.joinToString { "${Catalog[it.exerciseId]?.nameRu ?: it.exerciseId} — ${it.feel}${if (it.pain > 0) ", боль ${it.pain}" else ""}" })
            }
        }
        val others = db.training().othersRange(today - 6, today)
        if (others.isNotEmpty()) sb.appendLine("Прочая активность: " + others.joinToString { "${LocalDate.ofEpochDay(it.day)} ${it.title} ${it.activeMinutes} мин RPE ${it.rpe}" })
        val g = db.garmin().lastDaysNow(7)
        if (g.isNotEmpty()) {
            sb.appendLine("Garmin:")
            g.forEach {
                sb.appendLine("  ${LocalDate.ofEpochDay(it.day)}: сон ${it.sleepMinutes?.let { m -> "${m / 60}ч${m % 60}м" } ?: "-"} (оценка ${it.sleepScore ?: "-"}), пульс покоя ${it.restingHr ?: "-"}, HRV ${it.hrvAvg ?: "-"}, Body Battery ${it.bodyBatteryLow ?: "-"}–${it.bodyBatteryHigh ?: "-"}, стресс ${it.stressAvg ?: "-"}, шаги ${it.steps ?: "-"}")
            }
        }
        return sb.toString().ifBlank { "(данных пока нет)" }
    }

    private suspend fun maybeUpdateMemory() {
        val count = db.chat().count()
        if (count - settings.coachMemoryUpdatedAtCount < 10) return
        runCatching {
            val recent = db.chat().last(minOf(30, count - settings.coachMemoryUpdatedAtCount + 4))
            val transcript = recent.joinToString("\n") { "${if (it.role == "user") "Человек" else "Коуч"}: ${it.content}" }
            val updated = claude.complete(
                system = """
Ты ведёшь долговременную память коуча о человеке. Обнови память по новому фрагменту разговора.
Храни только устойчивое и полезное: факты о жизни и здоровье, цели, ценности, триггеры срывов, что помогает, договорённости и их статус, важные события с датами.
Не храни пересказ разговоров. Максимум 25 коротких пунктов, по-русски, списком через «- ». Устаревшее удаляй.
Верни только обновлённый список.
""".trimIndent(),
                messages = listOf(Msg("user", "Текущая память:\n${settings.coachMemory.ifBlank { "(пусто)" }}\n\nНовый фрагмент (${LocalDate.now()}):\n$transcript")),
                maxTokens = 1200,
            )
            if (updated.isNotBlank()) {
                settings.coachMemory = updated.trim()
                settings.coachMemoryUpdatedAtCount = count
            }
        }
    }

    suspend fun clearChat() {
        db.chat().clear()
        settings.coachMemoryUpdatedAtCount = 0
    }
}
