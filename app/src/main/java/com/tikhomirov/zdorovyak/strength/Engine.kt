package com.tikhomirov.zdorovyak.strength

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
data class PlanExercise(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int = 90,
    val alternativeId: String? = null,
)

@Serializable
data class PlanDay(val name: String, val exercises: List<PlanExercise>)

@Serializable
data class ProgramPlan(val days: List<PlanDay>, val rationale: String = "", val source: String = "ai")

enum class Feel(val ru: String, val rir: Double) { HARD("Тяжело", 0.5), NORMAL("Нормально", 2.5), EASY("Легко", 4.0) }

/** Прогрессионное состояние упражнения (чистая модель, без Room). */
data class ExState(
    val exerciseId: String,
    val weightKg: Double,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetReps: Int,
    val hardStreak: Int = 0,
    val easyStreak: Int = 0,
    val calibrated: Boolean = false,
    val calibrationSessions: Int = 0,
    val variantId: String? = null,
)

data class Outcome(val state: ExState, val message: String)

/** Входные данные одной тренировки по упражнению. */
data class ExResult(
    val reps: List<Int>,        // фактические повторы по подходам
    val weightKg: Double,       // фактический вес
    val feel: Feel,
    val pain: Int = 0,
    val afterHeavyLabour: Boolean = false, // накануне был тяжёлый физтруд на те же мышцы
)

/**
 * Правила прогрессии из strength-module-spec.md. ИИ ничего здесь не решает:
 * всё детерминированно и покрыто юнит-тестами.
 */
object Engine {
    const val MAX_SETS = 4

    /** Эффективное ощущение: объективные повторы важнее кнопки. */
    fun effectiveFeel(s: ExState, r: ExResult): Feel {
        val allTop = r.reps.isNotEmpty() && r.reps.all { it >= s.repMax }
        val belowMin = r.reps.any { it < s.repMin }
        return when {
            belowMin -> Feel.HARD
            r.feel == Feel.NORMAL && allTop -> Feel.EASY
            else -> r.feel
        }
    }

    fun nextUp(weights: List<Double>, current: Double): Double? = weights.firstOrNull { it > current + 1e-6 }
    fun nextDown(weights: List<Double>, current: Double): Double? = weights.lastOrNull { it < current - 1e-6 }
    fun roundDownTo(weights: List<Double>, target: Double): Double =
        weights.lastOrNull { it <= target + 1e-6 } ?: weights.firstOrNull() ?: 0.0

    /** Оценка 1ПМ: Эпли с поправкой на запас повторов. */
    fun e1rm(weight: Double, reps: Int, rir: Double): Double = weight * (1 + (reps + rir) / 30.0)

    /** Рабочий вес на ~10 повторов с запасом 2–3 ≈ 70% e1RM, вниз до доступного шага. */
    fun workingWeightFromTrial(weights: List<Double>, weight: Double, reps: Int, feel: Feel): Double =
        roundDownTo(weights, 0.70 * e1rm(weight, reps, feel.rir))

    fun progress(ex: Exercise, s: ExState, r: ExResult, weights: List<Double>, freezeIncreases: Boolean = false): Outcome {
        // Боль — первым делом
        if (r.pain >= 5) {
            val alt = ex.easier
            return Outcome(
                s.copy(hardStreak = 0, easyStreak = 0, variantId = alt ?: s.variantId,
                    weightKg = if (s.weightKg > 0) roundDownTo(weights, s.weightKg * 0.8) else 0.0),
                "Боль ${r.pain}/10: упражнение снимаем${alt?.let { " (замена: ${Catalog[it]?.nameRu})" } ?: ""}. Если боль острая или повторяется — к врачу."
            )
        }
        if (r.pain in 3..4) {
            val w = if (s.weightKg > 0) roundDownTo(weights, s.weightKg * 0.9) else 0.0
            return Outcome(s.copy(weightKg = w, hardStreak = 0, easyStreak = 0),
                "Дискомфорт ${r.pain}/10: вес −10%. Повторится — заменим упражнение.")
        }

        var feel = effectiveFeel(s, r)
        val base = s.copy(weightKg = if (r.weightKg > 0) r.weightKg else s.weightKg)
        val bodyweight = weights.size <= 1 && weights.firstOrNull() == 0.0

        if (feel == Feel.HARD && r.afterHeavyLabour) {
            return Outcome(base.copy(easyStreak = 0), "Тяжело после физработы — не штрафуем, держим цель.")
        }
        if (freezeIncreases && feel != Feel.HARD) {
            return Outcome(base.copy(hardStreak = 0), "Неделя без повышений (много «тяжело» или разгрузка).")
        }

        return when (feel) {
            Feel.HARD -> {
                val streak = base.hardStreak + 1
                if (streak >= 2) {
                    if (bodyweight) {
                        val easier = ex.easier
                        if (easier != null) Outcome(base.copy(variantId = easier, targetReps = base.repMin, hardStreak = 0, easyStreak = 0),
                            "Тяжело два раза подряд: переходим на «${Catalog[easier]?.nameRu}».")
                        else Outcome(base.copy(targetReps = base.repMin, hardStreak = 0, easyStreak = 0), "Тяжело два раза: цель — нижняя граница.")
                    } else {
                        val down = roundDownTo(weights, base.weightKg * 0.92).let { if (it >= base.weightKg) nextDown(weights, base.weightKg) ?: it else it }
                        Outcome(base.copy(weightKg = down, targetReps = base.repMin, hardStreak = 0, easyStreak = 0),
                            "Тяжело два раза подряд: вес ${fmt(base.weightKg)} → ${fmt(down)} кг, повторы на низ диапазона.")
                    }
                } else Outcome(base.copy(hardStreak = streak, easyStreak = 0), "Тяжело: ничего не повышаем.")
            }
            Feel.NORMAL -> {
                val b = base.copy(hardStreak = 0, easyStreak = 0)
                if (b.targetReps < b.repMax) Outcome(b.copy(targetReps = b.targetReps + 1), "Нормально: +1 повтор (цель ${b.targetReps + 1}).")
                else topOfRange(ex, b, weights, capPct = 0.05, bodyweight = bodyweight, label = "Нормально")
            }
            Feel.EASY -> {
                val b = base.copy(hardStreak = 0, easyStreak = base.easyStreak + 1)
                if (bodyweight) {
                    if (b.targetReps < b.repMax) Outcome(b.copy(targetReps = min(b.repMax, b.targetReps + 2)), "Легко: +2 повтора.")
                    else topOfRange(ex, b, weights, 0.0, true, "Легко")
                } else {
                    val atTop = r.reps.isNotEmpty() && r.reps.all { it >= b.repMax }
                    val pct = when {
                        ex.isLarge && atTop && b.easyStreak >= 2 -> 0.075
                        ex.isLarge -> 0.05
                        else -> 0.025
                    }
                    val up = nextUp(weights, b.weightKg)
                    if (up != null && up <= b.weightKg * (1 + pct) + 1e-6) {
                        Outcome(b.copy(weightKg = up), "Легко: вес ${fmt(b.weightKg)} → ${fmt(up)} кг (+${((up / b.weightKg - 1) * 100).roundToInt()}%).")
                    } else if (b.targetReps < b.repMax) {
                        Outcome(b.copy(targetReps = min(b.repMax, b.targetReps + 2)), "Легко, но шаг веса крупный: растим повторы (цель ${min(b.repMax, b.targetReps + 2)}).")
                    } else topOfRange(ex, b, weights, pct, false, "Легко")
                }
            }
        }
    }

    /** Верх диапазона взят: вес шагом (если укладывается в лимит), иначе подход, иначе вес шагом с повторами вниз. */
    private fun topOfRange(ex: Exercise, s: ExState, weights: List<Double>, capPct: Double, bodyweight: Boolean, label: String): Outcome {
        if (bodyweight) {
            val harder = ex.harder
            return when {
                s.sets < MAX_SETS -> Outcome(s.copy(sets = s.sets + 1, targetReps = s.repMin), "$label, верх диапазона: +1 подход.")
                harder != null -> Outcome(s.copy(variantId = harder, sets = max(2, s.sets - 1), targetReps = s.repMin),
                    "$label, верх диапазона: переходим на «${Catalog[harder]?.nameRu}».")
                else -> Outcome(s, "$label: держим, замедляйте опускание (3 с).")
            }
        }
        val up = nextUp(weights, s.weightKg)
        return when {
            up == null -> if (s.sets < MAX_SETS) Outcome(s.copy(sets = s.sets + 1), "$label: тяжелее веса нет, +1 подход.")
                else Outcome(s, "$label: инвентарь исчерпан, замедляйте негатив или добавьте паузу.")
            up <= s.weightKg * (1 + max(capPct, 0.05)) + 1e-6 ->
                Outcome(s.copy(weightKg = up, targetReps = s.repMin), "$label, верх диапазона: вес ${fmt(s.weightKg)} → ${fmt(up)} кг, повторы на низ.")
            s.sets < MAX_SETS -> Outcome(s.copy(sets = s.sets + 1, targetReps = s.repMin),
                "$label, верх диапазона, но шаг ${fmt(up - s.weightKg)} кг слишком крупный: +1 подход.")
            else -> Outcome(s.copy(weightKg = up, targetReps = s.repMin, sets = max(2, s.sets - 1)),
                "$label: подходы исчерпаны, вес ${fmt(s.weightKg)} → ${fmt(up)} кг, повторы и подходы вниз.")
        }
    }

    /** Прикидка. Первая сессия — оценка по пробным подходам, 2–3 — поправка. */
    fun calibrate(ex: Exercise, s: ExState, r: ExResult, weights: List<Double>): Outcome {
        val bodyweight = weights.size <= 1 && weights.firstOrNull() == 0.0
        val n = s.calibrationSessions + 1
        val done = n >= 2
        if (bodyweight) {
            val best = r.reps.maxOrNull() ?: 0
            val maxWithReserve = best + feelRir(r.feel)
            return when {
                maxWithReserve < 6 && ex.easier != null -> Outcome(s.copy(variantId = ex.easier, calibrationSessions = n, calibrated = done),
                    "Меньше 6 повторов: берём облегчённый вариант «${Catalog[ex.easier]?.nameRu}».")
                maxWithReserve > 20 && ex.harder != null -> Outcome(s.copy(variantId = ex.harder, calibrationSessions = n, calibrated = done),
                    "Больше 20 повторов: берём усложнённый вариант «${Catalog[ex.harder]?.nameRu}».")
                else -> {
                    val target = (best * 0.8).roundToInt().coerceIn(s.repMin, s.repMax)
                    Outcome(s.copy(targetReps = target, calibrationSessions = n, calibrated = done), "Цель: $target повторов.")
                }
            }
        }
        val w = if (r.weightKg > 0) r.weightKg else s.weightKg
        val reps = r.reps.maxOrNull() ?: 0
        if (n == 1) {
            val work = workingWeightFromTrial(weights, w, reps, r.feel)
            val final = if (reps >= 15) roundDownTo(weights, w * 1.15).coerceAtLeast(work) else work
            return Outcome(s.copy(weightKg = final, targetReps = s.repMin, calibrationSessions = n),
                "Оценка 1ПМ ≈ ${fmt(e1rm(w, reps, r.feel.rir))} кг → рабочий вес ${fmt(final)} кг.")
        }
        val adj = when (r.feel) {
            Feel.EASY -> nextUp(weights, w)?.takeIf { it <= w * 1.20 + 1e-6 } ?: w // первая оценка обычно занижена
            Feel.HARD -> roundDownTo(weights, w * 0.9)
            Feel.NORMAL -> w
        }
        return Outcome(s.copy(weightKg = adj, targetReps = s.repMin, calibrationSessions = n, calibrated = done),
            if (adj == w) "Вес подтверждён: ${fmt(w)} кг." else "Поправка: ${fmt(w)} → ${fmt(adj)} кг.")
    }

    private fun feelRir(f: Feel): Int = when (f) { Feel.HARD -> 0; Feel.NORMAL -> 2; Feel.EASY -> 4 }

    /** Больше половины упражнений «тяжело» → следующая тренировка без повышений. */
    fun shouldFreeze(feels: List<Feel>): Boolean = feels.isNotEmpty() && feels.count { it == Feel.HARD } * 2 > feels.size

    /** Разгрузка: каждая 5-я неделя программы. Подходы −40%. */
    fun isDeloadWeek(programWeek: Int): Boolean = programWeek > 0 && (programWeek + 1) % 5 == 0
    fun deloadSets(sets: Int): Int = max(1, (sets * 0.6).roundToInt())

    /** Поправка после перерыва. */
    fun afterBreak(daysSinceLast: Long, s: ExState, weights: List<Double>): Pair<ExState, String?> = when {
        daysSinceLast > 28 -> s.copy(calibrated = false, calibrationSessions = 0) to "Перерыв больше 4 недель — повторная прикидка."
        daysSinceLast > 14 && s.weightKg > 0 -> s.copy(weightKg = roundDownTo(weights, s.weightKg * 0.9)) to "Перерыв 2–4 недели — вес −10%."
        else -> s to null
    }

    /** Стартовый вес для прикидки, если человек сам не выбрал: консервативно. */
    fun starterWeight(ex: Exercise, weights: List<Double>): Double {
        if (weights.size <= 1) return weights.firstOrNull() ?: 0.0
        val guess = when (ex.pattern) {
            Pattern.SQUAT, Pattern.HINGE -> 12.0
            Pattern.LUNGE -> 6.0
            Pattern.PUSH_H, Pattern.PULL_H -> 10.0
            Pattern.PUSH_V -> 6.0
            Pattern.CARRY -> 14.0
            else -> 4.0
        } * (if (ex.load == Load.BARBELL) 2.5 else 1.0)
        return weights.minByOrNull { abs(it - guess) } ?: weights.first()
    }
}

/** Валидация плана от ИИ. Пустой список = план годится. */
object PlanValidator {
    private val required = listOf(
        setOf(Pattern.SQUAT, Pattern.LUNGE) to "присед/выпад",
        setOf(Pattern.HINGE) to "наклон (hinge)",
        setOf(Pattern.PUSH_H) to "жим горизонтальный",
        setOf(Pattern.PUSH_V) to "жим вертикальный",
        setOf(Pattern.PULL_H) to "тяга горизонтальная",
        setOf(Pattern.PULL_V) to "тяга вертикальная",
        setOf(Pattern.CORE) to "кор",
    )

    fun validate(plan: ProgramPlan, inv: Inventory, daysPerWeek: Int, minutes: Int, banned: Set<Joint>): List<String> {
        val errs = mutableListOf<String>()
        val avail = Catalog.available(inv).map { it.id }.toSet()
        if (plan.days.size !in 2..4) errs += "Тренировок в неделю должно быть 2–4, сейчас ${plan.days.size}."
        if (plan.days.size != daysPerWeek) errs += "Нужно ровно $daysPerWeek тренировочных дня, сейчас ${plan.days.size}."
        val all = plan.days.flatMap { it.exercises }
        all.forEach { pe ->
            val ex = Catalog[pe.exerciseId]
            when {
                ex == null -> errs += "Нет в каталоге: ${pe.exerciseId}."
                pe.exerciseId !in avail -> errs += "${pe.exerciseId} недоступно с этим инвентарём."
                ex.joints.any { it in banned } -> errs += "${pe.exerciseId} нагружает сустав из ограничений."
            }
            if (pe.alternativeId != null && Catalog[pe.alternativeId] == null) errs += "alternativeId ${pe.alternativeId} нет в каталоге."
            if (pe.sets !in 1..4) errs += "${pe.exerciseId}: подходов ${pe.sets}, допустимо 1–4."
            if (pe.repMin < 5 || pe.repMax > 20 || pe.repMin > pe.repMax) errs += "${pe.exerciseId}: диапазон ${pe.repMin}–${pe.repMax} вне 5–20."
        }
        plan.days.forEach { d ->
            if (d.exercises.size > 7) errs += "День ${d.name}: больше 7 упражнений."
            val estMin = d.exercises.sumOf { it.sets * (40 + it.restSec) } / 60.0 + 8 // +разминка
            if (estMin > minutes * 1.15) errs += "День ${d.name}: ~${estMin.roundToInt()} мин при лимите $minutes."
        }
        val patterns = all.mapNotNull { Catalog[it.exerciseId]?.pattern }.toSet()
        required.forEach { (ps, name) ->
            if (ps.none { it in patterns }) {
                // Если паттерн в принципе недоступен с инвентарём — не ошибка
                val possible = Catalog.available(inv).any { it.pattern in ps && it.joints.none { j -> j in banned } }
                if (possible) errs += "За неделю не закрыт паттерн: $name."
            }
        }
        // Подходы на группу мышц в неделю (основная мышца упражнения)
        val perMuscle = mutableMapOf<Muscle, Int>()
        all.forEach { pe -> Catalog[pe.exerciseId]?.muscles?.firstOrNull()?.let { perMuscle[it] = (perMuscle[it] ?: 0) + pe.sets } }
        perMuscle.forEach { (m, n) -> if (n > 12) errs += "Слишком много подходов на $m: $n (максимум 12)." }
        // Тяжёлый hinge не в соседние дни (план по кругу)
        if (plan.days.size >= 2) {
            val heavyHinge = plan.days.map { d -> d.exercises.any { Catalog[it.exerciseId]?.let { e -> e.pattern == Pattern.HINGE && e.isLarge && e.load != Load.BODYWEIGHT } == true } }
            if (daysPerWeek >= 4 && heavyHinge.zipWithNext().any { (a, b) -> a && b }) errs += "Тяжёлый наклон в соседние дни."
        }
        return errs
    }

    /** Детерминированный запасной план: если нет ключа или ИИ трижды ошибся. */
    fun fallbackPlan(inv: Inventory, daysPerWeek: Int, banned: Set<Joint>): ProgramPlan {
        val avail = Catalog.available(inv).filter { it.joints.none { j -> j in banned } }
        fun pick(p: Pattern, skip: Set<String> = emptySet()): Exercise? {
            val c = avail.filter { it.pattern == p && it.id !in skip }
            return c.firstOrNull { it.load != Load.BODYWEIGHT && it.load != Load.BAND } ?: c.firstOrNull()
        }
        fun pe(e: Exercise?, sets: Int = 3) = e?.let {
            val small = !it.isLarge
            PlanExercise(it.id, sets, if (small) 10 else 8, if (small) 15 else 12, if (it.isLarge) 90 else 60,
                avail.firstOrNull { a -> a.pattern == it.pattern && a.id != it.id }?.id)
        }
        val squat = pick(Pattern.SQUAT); val lunge = pick(Pattern.LUNGE)
        val hinge = pick(Pattern.HINGE); val hinge2 = pick(Pattern.HINGE, setOfNotNull(hinge?.id))
        val pushH = pick(Pattern.PUSH_H); val pushV = pick(Pattern.PUSH_V)
        val pullH = pick(Pattern.PULL_H); val pullV = pick(Pattern.PULL_V)
        val core = pick(Pattern.CORE); val core2 = pick(Pattern.CORE, setOfNotNull(core?.id))
        val a = PlanDay("A", listOfNotNull(pe(squat), pe(pushH), pe(pullH), pe(hinge2 ?: hinge, 2), pe(core, 2)))
        val b = PlanDay("B", listOfNotNull(pe(hinge), pe(pushV), pe(pullV ?: pullH), pe(lunge ?: squat, 2), pe(core2 ?: core, 2)))
        val c = PlanDay("C", listOfNotNull(pe(lunge ?: squat), pe(pullH), pe(pushH, 2), pe(pullV ?: pullH, 2), pe(core, 2)))
        val days = when (daysPerWeek) {
            2 -> listOf(a, b)
            4 -> listOf(
                PlanDay("A", listOfNotNull(pe(squat), pe(hinge), pe(lunge, 2), pe(core, 2))),
                PlanDay("B", listOfNotNull(pe(pushH), pe(pullH), pe(pushV), pe(pullV ?: pullH))),
                PlanDay("C", listOfNotNull(pe(lunge ?: squat), pe(hinge2 ?: hinge, 2), pe(core2 ?: core, 2))),
                PlanDay("D", listOfNotNull(pe(pushV, 2), pe(pullV ?: pullH), pe(pushH, 2), pe(pullH))),
            )
            else -> listOf(a, b, c)
        }
        return ProgramPlan(days, "Базовый фулбоди-план без ИИ: каждый день закрывает присед/наклон, жим и тягу, кор.", source = "fallback")
    }
}
