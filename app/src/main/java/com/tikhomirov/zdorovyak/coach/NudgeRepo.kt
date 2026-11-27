package com.tikhomirov.zdorovyak.coach

import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.ChatMessage
import com.tikhomirov.zdorovyak.data.Settings
import com.tikhomirov.zdorovyak.nutrition.NutritionMath
import com.tikhomirov.zdorovyak.nutrition.NutritionRepo
import com.tikhomirov.zdorovyak.strength.TrainingRepo
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Собирает данные для [Nudges], просит ИИ сформулировать подсказку, ведёт журнал. */
class NudgeRepo(
    private val db: AppDatabase,
    private val claude: ClaudeClient,
    private val settings: Settings,
    private val nutrition: NutritionRepo,
    private val training: TrainingRepo,
) {
    suspend fun input(today: Long = LocalDate.now().toEpochDay()): NudgeInput {
        val p = settings.profile.value
        val from = today - 14
        val food = db.food().range(from, today).groupBy { it.day }
        val burn = runCatching { nutrition.burnRange(from, today, p) }.getOrDefault(emptyMap())
        val addBurn = p.activityToTarget && p.autoTargets
        val days = (from..today).map { d ->
            val list = food[d].orEmpty()
            val add = if (addBurn) burn[d]?.kcal ?: 0 else 0
            FoodDay(d, list.sumOf { it.kcal }, list.sumOf { it.protein }, list.size, (p.kcalTarget + add).toDouble(), p.proteinTarget.toDouble())
        }
        val weighIns = db.body().weightsSince(today - 60).map { it.day to it.weightKg }
        val trend = NutritionMath.weightTrend(weighIns).let { tr ->
            if (tr.size < 2) null else {
                val last = tr.last(); val prev = tr.firstOrNull { it.first >= last.first - 21 } ?: tr.first()
                val span = (last.first - prev.first).toDouble()
                if (span >= 10) (last.second - prev.second) / span * 7 else null
            }
        }
        val cal = training.calendarNow(p, today - 21, today + 7)
        return NudgeInput(
            today = today, food = days, calendar = cal,
            lastWeighDay = db.body().lastWeights(1).firstOrNull()?.day,
            weightTrendKgPerWeek = trend, goalLose = p.goalMode == "LOSE",
            history = settings.nudges.value,
            muted = settings.nudgeMuted.mapNotNull { runCatching { NudgeGroup.valueOf(it) }.getOrNull() }.toSet(),
        )
    }

    /** Проверить и, если есть повод, создать подсказку. Возвращает её (или null — повода нет / лимит). */
    suspend fun runDaily(today: Long = LocalDate.now().toEpochDay()): NudgeRecord? {
        if (!settings.nudgesEnabled) return null
        val c = Nudges.pick(input(today)) ?: return null
        val (title, text) = phrase(c)
        val rec = NudgeRecord(today, c.kind.name, title, text)
        settings.saveNudges(settings.nudges.value + rec)
        return rec
    }

    /** ИИ формулирует; без ключа или при ошибке — запасной шаблон. */
    suspend fun phrase(c: NudgeCandidate): Pair<String, String> {
        if (!claude.hasKey) return c.fallbackTitle to c.fallbackText
        return runCatching {
            val p = settings.profile.value
            val recent = settings.nudges.value.takeLast(5).joinToString("\n") { "- ${LocalDate.ofEpochDay(it.day)}: ${it.text}" }
            val obj = claude.completeJson(
                system = """
Ты коуч ${p.name} (${p.age} лет) в его личном приложении здоровья. Напиши одно короткое push-уведомление по поводу ниже.
Тон: тёплый, уважительный, разговор двух взрослых. Мотивационное интервьюирование: признать, нормализовать, предложить один маленький конкретный шаг или задать один вопрос.
Нельзя: стыдить, упрекать, «ты должен», сарказм, слова «срыв», «провал», «лень», разговоры про внешность и «жир», драматизация, восклицательные знаки подряд, эмодзи.
Если повод — похвала: конкретно назови, что получилось, без пафоса.
Учитывай контекст из памяти коуча (например, тяжёлую физработу, болезнь, поездку) — если он объясняет ситуацию, скажи об этом мягко.
Не повторяй формулировки прошлых подсказок.
Заголовок до 40 символов, текст до 200 символов, по-русски.
Верни только JSON: {"title":"...","text":"..."}

Память коуча:
${settings.coachMemory.ifBlank { "(пусто)" }}

Прошлые подсказки:
${recent.ifBlank { "(не было)" }}
""".trimIndent(),
                messages = listOf(Msg("user", "Повод: ${c.kind.ru}. Факты: ${c.facts}")),
                maxTokens = 400,
            )
            val t = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
            val x = obj["text"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (t.isBlank() || x.isBlank()) c.fallbackTitle to c.fallbackText else t.take(60) to x.take(300)
        }.getOrDefault(c.fallbackTitle to c.fallbackText)
    }

    fun dismiss(rec: NudgeRecord) {
        settings.saveNudges(settings.nudges.value.map { if (it.time == rec.time) it.copy(dismissed = true) else it })
    }

    /** «Ответить коучу»: подсказка становится репликой коуча в чате, чтобы разговор продолжился с неё. */
    suspend fun toChat(rec: NudgeRecord) {
        db.chat().insert(ChatMessage(time = System.currentTimeMillis(), role = "assistant", content = "${rec.title}. ${rec.text}"))
        dismiss(rec)
    }
}
