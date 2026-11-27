package com.tikhomirov.zdorovyak.coach

import com.tikhomirov.zdorovyak.strength.CalDay
import com.tikhomirov.zdorovyak.strength.DayStatus
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Вид подсказки. Группа — для выключения в настройках, пауза — сколько дней не повторять тот же вид. */
enum class NudgeKind(val group: NudgeGroup, val cooldownDays: Int, val ru: String) {
    OVEREAT(NudgeGroup.FOOD, 3, "больше нормы"),
    UNDEREAT(NudgeGroup.FOOD, 3, "сильно меньше нормы"),
    LOW_PROTEIN(NudgeGroup.FOOD, 4, "мало белка"),
    NO_LOGGING(NudgeGroup.FOOD, 3, "не записывал еду"),
    MISSED_TRAINING(NudgeGroup.TRAINING, 3, "пропуск тренировки"),
    NO_WEIGH(NudgeGroup.BODY, 7, "давно не взвешивался"),
    PRAISE(NudgeGroup.PRAISE, 4, "похвала"),
}

enum class NudgeGroup(val ru: String) { FOOD("Питание"), TRAINING("Тренировки"), BODY("Вес"), PRAISE("Хорошие новости") }

/** Итог дня по питанию (уже с нормой дня, включая активность). */
data class FoodDay(val day: Long, val kcal: Double, val protein: Double, val entries: Int, val kcalTarget: Double, val proteinTarget: Double)

data class NudgeInput(
    val today: Long,
    val food: List<FoodDay>,          // последние ~14 дней, любые
    val calendar: List<CalDay>,       // календарь силовых
    val lastWeighDay: Long?,
    val weightTrendKgPerWeek: Double?, // отрицательный — снижается
    val goalLose: Boolean,
    val history: List<NudgeRecord>,   // что уже отправляли
    val muted: Set<NudgeGroup> = emptySet(),
)

/** Найденный повод: вид + факты для ИИ (и для запасного шаблона). */
data class NudgeCandidate(val kind: NudgeKind, val facts: String, val priority: Int, val fallbackTitle: String, val fallbackText: String)

@Serializable
data class NudgeRecord(
    val day: Long,
    val kind: String,
    val title: String,
    val text: String,
    val dismissed: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

/**
 * Проактивные подсказки. Правила простые и детерминированные: ИИ только формулирует текст.
 * Анти-спам: одна подсказка в день, не больше 4 в неделю, у каждого вида пауза, выключенные группы не трогаем.
 * Питание оцениваем по завершённым дням (вчера и раньше): сегодняшний ужин может быть ещё не записан.
 */
object Nudges {
    const val MAX_PER_WEEK = 4

    fun pick(inp: NudgeInput): NudgeCandidate? {
        if (inp.history.any { it.day == inp.today }) return null
        if (inp.history.count { it.day > inp.today - 7 } >= MAX_PER_WEEK) return null
        // Баланс: если две последние подсказки были «про проблемы», а есть за что похвалить — хвалим
        val lastTwoCritical = inp.history.sortedByDescending { it.day }.take(2).let { l -> l.size == 2 && l.none { it.kind == NudgeKind.PRAISE.name } }
        return candidates(inp)
            .map { if (lastTwoCritical && it.kind == NudgeKind.PRAISE) it.copy(priority = it.priority + 50) else it }
            .filter { it.kind.group !in inp.muted }
            .filter { c -> inp.history.none { it.kind == c.kind.name && it.day > inp.today - c.kind.cooldownDays } }
            .maxByOrNull { it.priority }
    }

    fun candidates(inp: NudgeInput): List<NudgeCandidate> {
        val out = mutableListOf<NudgeCandidate>()
        val t = inp.today
        val byDay = inp.food.associateBy { it.day }
        // Завершённые дни с нормальным учётом (хотя бы 2 записи)
        val logged = (1..7).mapNotNull { byDay[t - it] }.filter { it.entries >= 2 && it.kcalTarget > 0 }
        val last3 = logged.filter { it.day >= t - 3 }

        // 1. Больше нормы: 2 из 3 последних дней выше на 12%+, или вчера выше на 25%+
        val over = last3.filter { it.kcal > it.kcalTarget * 1.12 }
        val y = byDay[t - 1]?.takeIf { it.entries >= 2 }
        if (over.size >= 2 || (y != null && y.kcal > y.kcalTarget * 1.25)) {
            val days = if (over.size >= 2) over else listOfNotNull(y)
            val avgOver = days.map { it.kcal - it.kcalTarget }.average().roundToInt()
            out += NudgeCandidate(NudgeKind.OVEREAT,
                "Дни выше нормы: ${days.joinToString { "${it.kcal.roundToInt()} при норме ${it.kcalTarget.roundToInt()}" }}; в среднем +$avgOver ккал.",
                priority = 60,
                fallbackTitle = "Пара дней выше нормы",
                fallbackText = "Последние дни вышли примерно на $avgOver ккал выше нормы. Это не катастрофа — просто сигнал. Что сегодня легче всего поправить: перекус, порцию или вечер?")
        }

        // 2. Сильно меньше нормы (при похудении это бьёт по мышцам и срывает): 2 из 3 дней < 70% нормы
        val under = last3.filter { it.kcal < it.kcalTarget * 0.7 }
        if (under.size >= 2) {
            out += NudgeCandidate(NudgeKind.UNDEREAT,
                "Дни сильно ниже нормы: ${under.joinToString { "${it.kcal.roundToInt()} при норме ${it.kcalTarget.roundToInt()}" }}.",
                priority = 55,
                fallbackTitle = "Похоже, едите слишком мало",
                fallbackText = "Пару дней калорий заметно меньше нормы. Резкий дефицит в нашем возрасте съедает мышцы и заканчивается срывом. Если это не забытые записи — добавьте нормальный приём пищи с белком.")
        }

        // 3. Мало белка: в среднем за 3 записанных дня < 80% цели
        if (last3.size >= 2) {
            val avgP = last3.map { it.protein }.average()
            val target = last3.map { it.proteinTarget }.average()
            if (target > 0 && avgP < target * 0.8) {
                val gap = (target - avgP).roundToInt()
                out += NudgeCandidate(NudgeKind.LOW_PROTEIN,
                    "Белок в среднем ${avgP.roundToInt()} г при цели ${target.roundToInt()} г за ${last3.size} дня, не хватает ~$gap г в день.",
                    priority = 50,
                    fallbackTitle = "Белка не хватает",
                    fallbackText = "Белка в среднем ${avgP.roundToInt()} г из ${target.roundToInt()}. Для силы и мышц это главный кирпич. Самый простой ход: творог, яйца или порция мяса к одному из приёмов — и разрыв почти закрыт.")
            }
        }

        // 4. Не записывал еду 2 дня подряд, хотя до этого записывал
        val gapDays = (1..2).all { (byDay[t - it]?.entries ?: 0) == 0 }
        val loggedBefore = (3..10).any { (byDay[t - it]?.entries ?: 0) > 0 }
        if (gapDays && loggedBefore) {
            out += NudgeCandidate(NudgeKind.NO_LOGGING, "Два дня без записей питания, до этого записи были.",
                priority = 40,
                fallbackTitle = "Пара дней без записей",
                fallbackText = "Два дня нет записей еды. Бывает. Не нужно восстанавливать всё — просто запишите сегодняшний ужин, и мы снова в деле.")
        }

        // 5. Пропуски силовых: не отмеченные пропуски за 7 дней, или 2+ за 14
        val missed7 = inp.calendar.filter { it.day in (t - 7) until t && (it.status == DayStatus.MISSED || it.status == DayStatus.SKIPPED) }
        val missed14 = inp.calendar.count { it.day in (t - 14) until t && (it.status == DayStatus.MISSED || it.status == DayStatus.SKIPPED) }
        val todayDone = inp.calendar.firstOrNull { it.day == t }?.completed == true
        if (!todayDone && (missed7.any { it.status == DayStatus.MISSED } || missed14 >= 2)) {
            val next = inp.calendar.firstOrNull { it.day >= t && (it.status == DayStatus.PLANNED || it.status == DayStatus.TODAY) }
            out += NudgeCandidate(NudgeKind.MISSED_TRAINING,
                "Пропущено силовых: за 7 дней ${missed7.size}, за 14 дней $missed14. Ближайшая по плану: ${next?.let { if (it.day == t) "сегодня" else "через ${it.day - t} дн." } ?: "нет"}.",
                priority = if (missed14 >= 2) 65 else 45,
                fallbackTitle = "Давно не было силовой",
                fallbackText = "Последние тренировки выпали — так бывает у всех. Не нужно наверстывать: следующая по плану, можно даже облегчённую. Главное — не дать паузе стать привычкой.")
        }

        // 6. Давно не взвешивался
        if (inp.lastWeighDay != null && t - inp.lastWeighDay >= 8) {
            out += NudgeCandidate(NudgeKind.NO_WEIGH, "Последнее взвешивание ${t - inp.lastWeighDay} дней назад. Нормы КБЖУ пересчитываются по весу.",
                priority = 25,
                fallbackTitle = "Встанем на весы?",
                fallbackText = "Взвешивания не было ${t - inp.lastWeighDay} дней. Без него нормы считаются по старому весу. Завтра утром натощак — 10 секунд.")
        }

        // 7. Похвала — если есть за что, и нет проблем важнее (у неё самый низкий приоритет, кроме случая «серия»)
        val praise = mutableListOf<String>()
        val doneWeek = inp.calendar.count { it.day in (t - 7) until t && it.completed }
        val plannedWeek = inp.calendar.count { it.day in (t - 7) until t && it.planned && it.status != DayStatus.EXCUSED && it.status != DayStatus.MOVED }
        if (plannedWeek >= 2 && doneWeek >= plannedWeek) praise += "все $plannedWeek плановые силовые за неделю сделаны"
        val week = logged.filter { it.day >= t - 7 }
        val proteinHits = week.count { it.proteinTarget > 0 && it.protein >= it.proteinTarget * 0.9 }
        if (week.size >= 5 && proteinHits >= 5) praise += "белок в норме $proteinHits дней из ${week.size}"
        val inRange = week.count { it.kcal in it.kcalTarget * 0.85..it.kcalTarget * 1.08 }
        if (week.size >= 5 && inRange >= 5) praise += "калории в коридоре нормы $inRange дней из ${week.size}"
        inp.weightTrendKgPerWeek?.let { if (inp.goalLose && it <= -0.2 && it >= -1.0) praise += "вес снижается примерно на ${"%.1f".format(-it).replace('.', ',')} кг в неделю — здоровый темп" }
        if (praise.isNotEmpty()) {
            out += NudgeCandidate(NudgeKind.PRAISE, "Хорошее: ${praise.joinToString("; ")}.",
                priority = 20 + praise.size * 5,
                fallbackTitle = "Хорошая неделя",
                fallbackText = praise.first().replaceFirstChar { it.uppercase() } + ". Это и есть результат: не рывки, а повторение. Продолжаем в том же темпе.")
        }
        return out
    }
}
