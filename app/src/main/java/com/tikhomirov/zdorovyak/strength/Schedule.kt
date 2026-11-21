package com.tikhomirov.zdorovyak.strength

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Статус дня в календаре тренировок. */
enum class DayStatus(val ru: String) {
    DONE("сделано"),            // есть записанная тренировка
    DONE_MANUAL("сделано"),     // отмечено «сделал, без записи»
    REPLACED("физработа"),      // вместо тренировки — прочая активность
    SKIPPED("пропуск"),         // осознанный пропуск по неуважительной причине
    EXCUSED("уважительно"),     // болезнь, поездка — не портит дисциплину
    MISSED("не сделано"),       // день прошёл, тренировки нет
    MOVED("перенесено"),        // перенесено на другой день
    TODAY("сегодня"),
    PLANNED("по плану"),
    REST("отдых"),
}

/** Отметки, которые ставит человек (таблица training_mark). */
object Mark {
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
    const val EXCUSED = "EXCUSED"
    const val MOVED = "MOVED"     // день освобождён, тренировка уехала
    const val EXTRA = "EXTRA"     // сюда переехала тренировка с другого дня
}

data class CalDay(
    val day: Long,
    val planned: Boolean,
    val status: DayStatus,
    /** День программы (A/B/C): сделанный — по факту, будущий — по очереди. */
    val planDay: String?,
    val note: String = "",
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)
    val completed: Boolean get() = status == DayStatus.DONE || status == DayStatus.DONE_MANUAL || status == DayStatus.REPLACED
}

data class WeekStat(val weekStart: Long, val planned: Int, val done: Int) {
    val complete: Boolean get() = planned > 0 && done >= planned
}

data class Discipline(
    val thisWeek: WeekStat,
    /** Сколько недель подряд (до текущей) план выполнен полностью. */
    val streakWeeks: Int,
    /** Доля выполненных плановых тренировок за последние 4 недели (включая текущую по сегодняшний день). */
    val adherence: Double?,
    val missedLast28: Int,
)

/**
 * Календарь силовых: дни недели выбирает человек (Пн-Ср-Пт и т.п.), программа идёт по кругу A → B → C.
 * Пропуск не «съедает» день программы: следующая тренировка — та, что не сделана, просто в ближайший плановый день.
 * Логика чистая (без Room), чтобы её можно было проверить тестами.
 */
object Schedule {
    val ru: Locale = Locale.forLanguageTag("ru")

    fun short(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, ru).replaceFirstChar { it.uppercase() }.removeSuffix(".")

    fun describe(weekdays: Collection<Int>): String = weekdays.sorted().joinToString("-") { short(DayOfWeek.of(it)) }

    /** Подряд идущие дни (без 48 ч отдыха), включая Вс→Пн. */
    fun backToBack(weekdays: Collection<Int>): List<Pair<Int, Int>> {
        val s = weekdays.toSortedSet()
        return s.mapNotNull { d -> val n = if (d == 7) 1 else d + 1; if (n in s && s.size < 7) d to n else null }
    }

    /** Разумные расписания по умолчанию: тренировки через день. */
    fun defaultFor(n: Int): List<Int> = when (n) {
        1 -> listOf(3)
        2 -> listOf(2, 5)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        else -> listOf(1, 2, 4, 5, 6).take(n.coerceIn(1, 7))
    }

    fun isPlanned(day: Long, weekdays: Set<Int>, marks: Map<Long, String>): Boolean = when (marks[day]) {
        Mark.MOVED -> false
        Mark.EXTRA -> true
        else -> LocalDate.ofEpochDay(day).dayOfWeek.value in weekdays
    }

    /**
     * Дни [from]..[to]. [workouts] — день → названия сделанных тренировок; [replaced] — дни с «вместо тренировки»;
     * [planDays] — A, B, C; [nextIndex] — индекс следующего дня программы; [start] — с какого дня считаем план
     * (создание программы): раньше дни не считаются пропущенными.
     */
    fun build(
        from: Long, to: Long, today: Long,
        weekdays: Set<Int>,
        workouts: Map<Long, List<String>>,
        replaced: Set<Long>,
        marks: Map<Long, String>,
        planDays: List<String>,
        nextIndex: Int,
        start: Long,
    ): List<CalDay> {
        var idx = nextIndex
        val out = ArrayList<CalDay>()
        for (d in from..to) {
            val planned = d >= start && isPlanned(d, weekdays, marks)
            val mark = marks[d]
            val w = workouts[d].orEmpty()
            val status = when {
                w.isNotEmpty() -> DayStatus.DONE
                mark == Mark.DONE -> DayStatus.DONE_MANUAL
                d >= start && mark == Mark.MOVED -> DayStatus.MOVED
                !planned -> DayStatus.REST
                d in replaced -> DayStatus.REPLACED
                mark == Mark.EXCUSED -> DayStatus.EXCUSED
                mark == Mark.SKIPPED -> DayStatus.SKIPPED
                d < today -> DayStatus.MISSED
                d == today -> DayStatus.TODAY
                else -> DayStatus.PLANNED
            }
            val planDay = when {
                w.isNotEmpty() -> w.joinToString("+")
                (status == DayStatus.TODAY || status == DayStatus.PLANNED) && planDays.isNotEmpty() -> planDays[idx++ % planDays.size]
                else -> null
            }
            out += CalDay(d, planned, status, planDay)
        }
        return out
    }

    fun weekStart(day: Long): Long = LocalDate.ofEpochDay(day).with(DayOfWeek.MONDAY).toEpochDay()

    /** Неделя: сколько плановых дней уже наступило (сегодня — только если сделано) и сколько из них закрыто. */
    fun weekStat(days: List<CalDay>, weekStart: Long, today: Long): WeekStat {
        val week = days.filter { it.day in weekStart until weekStart + 7 }
        val target = week.count { d ->
            d.planned && d.status != DayStatus.EXCUSED && (d.day < today || (d.day == today && d.completed))
        }
        // Внеплановая тренировка тоже засчитывается: сделал в четверг вместо среды — неделя закрыта
        val done = week.count { it.completed && it.day <= today }
        return WeekStat(weekStart, target, done)
    }

    /** Полный план недели (для «2 из 3 на этой неделе»). */
    fun weekPlanTotal(days: List<CalDay>, weekStart: Long): Int =
        days.count { it.day in weekStart until weekStart + 7 && it.planned && it.status != DayStatus.EXCUSED }

    fun discipline(days: List<CalDay>, today: Long): Discipline {
        val ws = weekStart(today)
        val thisWeek = weekStat(days, ws, today)
        var streak = 0
        var w = ws - 7
        while (true) {
            val s = weekStat(days, w, today)
            if (days.none { it.day == w }) break
            if (s.planned == 0) { w -= 7; continue }   // неделя целиком «уважительная» — не рвёт серию
            if (s.done >= s.planned) streak++ else break
            w -= 7
        }
        val recent = (0..3).map { weekStat(days, ws - 7L * it, today) }
        val planned = recent.sumOf { it.planned }
        val done = recent.sumOf { minOf(it.done, it.planned) }
        val missed = days.count { it.day in (today - 27)..today && (it.status == DayStatus.MISSED || it.status == DayStatus.SKIPPED) }
        return Discipline(thisWeek, streak, if (planned > 0) done.toDouble() / planned else null, missed)
    }

    /** Ближайший плановый день, начиная с [from] (для «перенести на завтра» и напоминаний). */
    fun nextPlanned(from: Long, weekdays: Set<Int>, marks: Map<Long, String>): Long? =
        (from until from + 14).firstOrNull { isPlanned(it, weekdays, marks) }
}
