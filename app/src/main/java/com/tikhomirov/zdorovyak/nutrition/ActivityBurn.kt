package com.tikhomirov.zdorovyak.nutrition

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Расход на активность за день — то, что прибавляется к базовой норме калорий.
 * Чистый Kotlin, покрыт ActivityBurnTest.
 *
 * Считаем ЧИСТЫЙ расход: (MET − 1) × вес × часы. Единица — это покой, он уже сидит в базовой норме (BMR × образ жизни),
 * иначе посчитали бы его дважды. MET — из 2024 Adult Compendium of Physical Activities (pacompendium.com):
 *  - силовая по ощущениям: легко 3,5 (02054, 8–15 повторов), нормально 5,0 (02052, базовые с весом),
 *    тяжело 6,0 (02050, vigorous);
 *  - прочая активность (физтруд): MET = 1,5 + 0,55 × RPE → RPE 3 ≈ 3,2 (лёгкий ремонт), 5 ≈ 4,3 (садовые работы),
 *    7 ≈ 5,4 (копать, носить), 9 ≈ 6,5 (тяжёлый труд);
 *  - Garmin: его калории за тренировку приоритетнее оценки (часы видят пульс). Garmin отдаёт полные калории
 *    вместе с покоем — вычитаем BMR за те же минуты.
 * Тренировку Garmin «силовая» в тот же день считаем той же тренировкой, что записана в приложении, — не дублируем.
 */
object ActivityBurn {
    const val MAX_SESSION_MIN = 120
    const val MAX_DAY_KCAL = 2000

    enum class Source(val ru: String) { GARMIN("Garmin"), ESTIMATE("оценка") }

    data class Item(val title: String, val minutes: Int, val kcal: Int, val source: Source)

    data class Day(val items: List<Item>) {
        val kcal: Int get() = items.sumOf { it.kcal }.coerceAtMost(MAX_DAY_KCAL)
        val isEmpty: Boolean get() = items.isEmpty()
    }

    /** Силовая из приложения: длительность и ощущения по упражнениям (HARD / NORMAL / EASY). */
    data class Strength(val minutes: Int, val feels: List<String>, val title: String = "Силовая")

    /** Прочая активность: активные минуты и RPE 1–10. */
    data class Labour(val title: String, val minutes: Int, val rpe: Int)

    /** Тренировка из Garmin. kcal — полные калории (с покоем), как их отдаёт Garmin. */
    data class Watch(val type: String, val name: String, val minutes: Double, val kcal: Int?)

    fun net(met: Double, weightKg: Double, minutes: Double): Double = max(0.0, (met - 1) * weightKg * minutes / 60)

    fun strengthMet(feels: List<String>): Double {
        if (feels.isEmpty()) return 5.0
        return feels.map { when (it) { "HARD" -> 6.0; "EASY" -> 3.5; else -> 5.0 } }.average()
    }

    fun labourMet(rpe: Int): Double = (1.5 + 0.55 * rpe.coerceIn(1, 10)).coerceIn(2.0, 8.0)

    /** MET для тренировки Garmin без калорий (бывает у ручных записей). */
    fun watchMet(type: String): Double = when {
        "strength" in type -> 5.0
        "run" in type -> 9.0
        "cycl" in type || "bik" in type -> 7.0
        "swim" in type -> 7.0
        "hik" in type -> 6.0
        "walk" in type -> 3.5
        "yoga" in type || "breath" in type || "pilates" in type -> 2.5
        else -> 4.0
    }

    fun isStrength(type: String) = "strength" in type

    fun day(
        weightKg: Double,
        bmr: Double,
        strength: List<Strength>,
        labour: List<Labour>,
        watch: List<Watch>,
    ): Day {
        val items = mutableListOf<Item>()
        val restPerMin = bmr / 1440.0
        val watchStrength = watch.count { isStrength(it.type) }
        watch.forEach { w ->
            val min = w.minutes.coerceIn(0.0, 600.0)
            val kcal = w.kcal?.let { max(0.0, it - restPerMin * min) } ?: net(watchMet(w.type), weightKg, min)
            items += Item(w.name.ifBlank { w.type }, min.roundToInt(), kcal.roundToInt(), if (w.kcal != null) Source.GARMIN else Source.ESTIMATE)
        }
        // Силовые из приложения: столько, сколько не покрыто записями Garmin
        strength.drop(watchStrength).forEach { s ->
            val min = s.minutes.coerceIn(0, MAX_SESSION_MIN)
            items += Item(s.title, min, net(strengthMet(s.feels), weightKg, min.toDouble()).roundToInt(), Source.ESTIMATE)
        }
        labour.forEach { l ->
            items += Item(l.title, l.minutes, net(labourMet(l.rpe), weightKg, l.minutes.toDouble()).roundToInt(), Source.ESTIMATE)
        }
        return Day(items.filter { it.kcal > 0 })
    }

    /**
     * Норма дня = базовая + активность. Прибавка уходит в углеводы: это топливо для работы и восстановления,
     * белок от активности почти не зависит (он и так 1,8 г/кг), жиры — по минимуму.
     */
    fun dayTargets(base: Targets, burnKcal: Int): Targets =
        if (burnKcal <= 0) base else base.copy(kcal = base.kcal + burnKcal, carbs = base.carbs + (burnKcal / 4.0).roundToInt())
}
