package com.tikhomirov.zdorovyak.nutrition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Расчёт норм КБЖУ. Чистый Kotlin без Android — покрыт юнит-тестами (TargetsTest).
 *
 * Схема (см. docs/research/nutrition-improvements-plan.md):
 *  1. Старт: BMR по Mifflin-St Jeor × коэффициент активности = расход (TDEE).
 *  2. Когда накопится ≥ 10 полных дней питания и ≥ 4 взвешиваний за 21 день —
 *     адаптивный расход как в MacroFactor: средние ккал − изменение тренда веса × 7700 / дней.
 *  3. Норма = расход ± поправка под цель; белок г/кг, жиры ≥ 0,8 г/кг, углеводы — остаток.
 */
enum class Sex(val title: String) { M("Мужской"), F("Женский") }

enum class Activity(val factor: Double, val title: String, val hint: String) {
    SEDENTARY(1.2, "Минимум", "Сидячая работа, почти без тренировок"),
    LIGHT(1.375, "Лёгкая", "Сидячая работа + 2–3 тренировки в неделю"),
    MODERATE(1.55, "Средняя", "Тренировки + много хожу или регулярный физтруд"),
    HIGH(1.725, "Высокая", "Тяжёлый физический труд почти каждый день"),
}

enum class GoalMode(val title: String) { LOSE("Снижать вес"), MAINTAIN("Держать вес"), GAIN("Набирать") }

data class TargetInput(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Double,          // сглаженный тренд или последнее взвешивание
    val targetWeightKg: Double?,
    val activity: Activity,
    val goal: GoalMode,
    val rateKgPerWeek: Double,     // темп для LOSE/GAIN, положительное число
    val proteinPerKg: Double,
)

data class Targets(
    val kcal: Int, val protein: Int, val fat: Int, val carbs: Int,
    val bmr: Int, val tdee: Int,
    /** Насколько урезали дефицит/профицит ограничителями (для пояснения). */
    val note: String = "",
)

object NutritionMath {
    /** Энергия 1 кг изменения массы тела (смесь жира и тощей массы), ккал. */
    const val KCAL_PER_KG = 7700.0

    fun bmrMifflin(sex: Sex, weightKg: Double, heightCm: Int, age: Int): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.M) 5 else -161

    fun formulaTdee(i: TargetInput): Double = bmrMifflin(i.sex, i.weightKg, i.heightCm, i.ageYears) * i.activity.factor

    /**
     * Нормы от расхода. Ограничители: дефицит не больше 25 % расхода и норма не ниже основного обмена (BMR);
     * профицит не больше 15 %.
     */
    fun targets(i: TargetInput, tdee: Double = formulaTdee(i)): Targets {
        val bmr = bmrMifflin(i.sex, i.weightKg, i.heightCm, i.ageYears)
        val rate = i.rateKgPerWeek.coerceIn(0.0, 1.5)
        val wanted = when (i.goal) {
            GoalMode.LOSE -> -rate * KCAL_PER_KG / 7
            GoalMode.MAINTAIN -> 0.0
            GoalMode.GAIN -> rate * KCAL_PER_KG / 7
        }
        var delta = wanted
        val notes = mutableListOf<String>()
        if (delta < -0.25 * tdee) { delta = -0.25 * tdee; notes += "дефицит ограничен 25 % расхода" }
        if (delta > 0.15 * tdee) { delta = 0.15 * tdee; notes += "профицит ограничен 15 % расхода" }
        var kcal = tdee + delta
        if (kcal < bmr) { kcal = bmr; notes += "норма не ниже основного обмена" }
        kcal = (kcal / 10).roundToInt() * 10.0

        // Белок от «опорного» веса: при ожирении (ИМТ > 30) и заданной цели — от целевого веса
        val bmi = i.weightKg / (i.heightCm / 100.0).pow(2)
        val refWeight = if (bmi > 30 && i.targetWeightKg != null && i.targetWeightKg < i.weightKg) i.targetWeightKg else i.weightKg
        val protein = (i.proteinPerKg.coerceIn(0.8, 2.5) * refWeight).roundToInt()
        var fat = max(0.8 * refWeight, 0.25 * kcal / 9).roundToInt()
        var carbs = ((kcal - protein * 4 - fat * 9) / 4).roundToInt()
        if (carbs < 50) {
            // Очень низкая норма: жиры ужимаем до 0,6 г/кг, остальное — углеводы
            fat = max((0.6 * refWeight).roundToInt(), ((kcal - protein * 4 - 50 * 4) / 9).roundToInt())
            carbs = max(0, ((kcal - protein * 4 - fat * 9) / 4).roundToInt())
        }
        return Targets(kcal.roundToInt(), protein, fat, carbs, bmr.roundToInt(), tdee.roundToInt(), notes.joinToString("; "))
    }

    /**
     * Сглаженный тренд веса: экспоненциальное сглаживание по дням, α = 0,1 за день.
     * Пропуски учитываются: за n дней без взвешивания вес «подтягивается» как за n шагов.
     * Несколько взвешиваний в один день усредняются.
     */
    fun weightTrend(weighIns: List<Pair<Long, Double>>, alpha: Double = 0.1): List<Pair<Long, Double>> {
        val byDay = weighIns.groupBy({ it.first }, { it.second }).mapValues { it.value.average() }.toSortedMap()
        val out = ArrayList<Pair<Long, Double>>(byDay.size)
        var prevDay: Long? = null
        var trend = 0.0
        for ((day, w) in byDay) {
            if (prevDay == null) trend = w
            else {
                val n = (day - prevDay).toInt().coerceAtLeast(1)
                val a = 1 - (1 - alpha).pow(n)
                trend += a * (w - trend)
            }
            out += day to trend
            prevDay = day
        }
        return out
    }

    /** Значение тренда на день (последняя точка не позже этого дня). */
    fun trendAt(trend: List<Pair<Long, Double>>, day: Long): Double? = trend.lastOrNull { it.first <= day }?.second

    data class Adaptive(
        val tdee: Double?,            // null — данных не хватает, расход не меняем
        val observedTdee: Double?,
        val loggedDays: Int,
        val weighIns: Int,
        val avgIntake: Double?,
        val trendChangeKgPerWeek: Double?,
        val explanation: String,
    )

    const val WINDOW_DAYS = 21
    const val MIN_LOGGED_DAYS = 10
    const val MIN_WEIGH_INS = 4
    const val BLEND = 0.3
    const val MAX_WEEKLY_CHANGE = 150.0

    /**
     * Адаптивный расход по факту.
     * @param intakeByDay ккал по дням (только дни, где что-то записано)
     * @param weighIns все взвешивания (день, кг)
     * @param today сегодняшний день: окно — [today − 21; today − 1], сегодня ещё не закончился
     * @param prevTdee текущая оценка расхода (формула или прошлая адаптивная)
     * @param fullDayKcal день считается полным, если записано не меньше этого (≈ 60 % нормы)
     */
    fun adaptiveTdee(
        intakeByDay: Map<Long, Double>,
        weighIns: List<Pair<Long, Double>>,
        today: Long,
        prevTdee: Double,
        fullDayKcal: Double,
        firstTime: Boolean = false,
    ): Adaptive {
        val from = today - WINDOW_DAYS
        val to = today - 1
        val logged = intakeByDay.filter { (d, k) -> d in from..to && k >= fullDayKcal }
        val wIn = weighIns.filter { it.first in from..to }
        val wDays = wIn.map { it.first }.distinct().size
        if (logged.size < MIN_LOGGED_DAYS || wDays < MIN_WEIGH_INS) {
            val need = buildList {
                if (logged.size < MIN_LOGGED_DAYS) add("полных дней питания ${logged.size} из $MIN_LOGGED_DAYS")
                if (wDays < MIN_WEIGH_INS) add("взвешиваний $wDays из $MIN_WEIGH_INS")
            }.joinToString(", ")
            return Adaptive(null, null, logged.size, wDays, logged.values.averageOrNull(), null,
                "Пока расход по формуле. Для расчёта по факту за 3 недели не хватает: $need.")
        }
        // Тренд считаем по всей истории, чтобы начало окна было уже «прогретым»
        val trend = weightTrend(weighIns.filter { it.first <= to })
        val firstW = wIn.minOf { it.first }
        val lastW = wIn.maxOf { it.first }
        val startTrend = trendAt(trend, firstW)!!
        val endTrend = trendAt(trend, lastW)!!
        val span = (lastW - firstW).coerceAtLeast(7).toDouble()
        val avg = logged.values.average()
        val observed = avg - (endTrend - startTrend) * KCAL_PER_KG / span
        // Первый переход с формулы на факт: формула может ошибаться на сотни ккал, доверяем факту сильнее
        val blend = if (firstTime) 0.5 else BLEND
        val maxChange = if (firstTime) 2 * MAX_WEEKLY_CHANGE else MAX_WEEKLY_CHANGE
        val blended = prevTdee + blend * (observed - prevTdee)
        val change = (blended - prevTdee).coerceIn(-maxChange, maxChange)
        val newTdee = prevTdee + change
        val perWeek = (endTrend - startTrend) / span * 7
        val dir = when {
            abs(perWeek) < 0.05 -> "вес стоит"
            perWeek < 0 -> "тренд ${fmt1(perWeek)} кг/нед"
            else -> "тренд +${fmt1(perWeek)} кг/нед"
        }
        val text = "За ${logged.size} полных дней в среднем ${avg.roundToInt()} ккал, $dir → расход по факту ≈ ${observed.roundToInt()} ккал. " +
            "Оценка сдвинута осторожно: ${prevTdee.roundToInt()} → ${newTdee.roundToInt()} ккал" +
            (if (abs(blended - prevTdee) > maxChange) " (не больше ±${maxChange.toInt()} за раз)." else ".")
        return Adaptive(newTdee, observed, logged.size, wDays, avg, perWeek, text)
    }

    private fun Collection<Double>.averageOrNull() = if (isEmpty()) null else average()
    private fun fmt1(v: Double) = ("%.2f".format(java.util.Locale.US, v)).trimEnd('0').trimEnd('.').replace('.', ',')
}
