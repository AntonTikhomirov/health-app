package com.tikhomirov.zdorovyak.strength

import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.ExerciseFeedback
import com.tikhomirov.zdorovyak.data.ExerciseStateEntity
import com.tikhomirov.zdorovyak.data.OtherActivity
import com.tikhomirov.zdorovyak.data.Profile
import com.tikhomirov.zdorovyak.data.ProgramEntity
import com.tikhomirov.zdorovyak.data.SetLog
import com.tikhomirov.zdorovyak.data.TrainingMark
import com.tikhomirov.zdorovyak.data.WorkoutEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class SessionItem(
    val plan: PlanExercise,
    val exercise: Exercise,        // с учётом варианта (облегчённый/усложнённый)
    val state: ExState,
    val sets: Int,                 // с учётом разгрузки
    val skippedReason: String? = null,
)

data class SessionPlan(
    val dayName: String,
    val items: List<SessionItem>,
    val calibration: Boolean,
    val deload: Boolean,
    val labourNote: String?,
    val breakNote: String?,
    val programWeek: Int,
)

class TrainingRepo(private val db: AppDatabase, private val claude: ClaudeClient) {
    private val dao = db.training()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parsePlan(p: ProgramEntity?): ProgramPlan? = p?.let { runCatching { json.decodeFromString<ProgramPlan>(it.json) }.getOrNull() }

    // ---------- Составление программы ----------

    suspend fun buildProgram(profile: Profile, onStatus: (String) -> Unit = {}): Pair<ProgramPlan, List<String>> {
        val banned = Catalog.jointsFromText(profile.limitations)
        val inv = profile.inventory
        val avail = Catalog.available(inv).filter { it.joints.none { j -> j in banned } }
        var plan: ProgramPlan? = null
        val log = mutableListOf<String>()
        if (claude.hasKey) {
            val catalogText = avail.joinToString("\n") { "${it.id} | ${it.nameRu} | ${it.pattern} | ${if (it.isLarge) "крупное" else "мелкое"}" }
            val system = """
Ты составляешь силовую программу для одного человека: мужчина, ${profile.age} лет, рост ${profile.heightCm} см, опыт: ${profile.experience}.
Цель: ${profile.goal}. Тренировок в неделю: ${profile.trainingDays}, длительность до ${profile.sessionMinutes} мин.
Инвентарь: ${inv.describe()}. Ограничения: ${profile.limitations.ifBlank { "нет" }}.

Правила:
- Используй только упражнения из списка ниже, по их id. Не придумывай новых.
- Фулбоди; за неделю закрой паттерны: SQUAT или LUNGE, HINGE, PUSH_H, PUSH_V, PULL_H, PULL_V, CORE.
- Старт: 2–3 подхода, диапазон повторов 8–12 для крупных и 10–15 для мелких упражнений, отдых 60–120 с.
- 4–6 рабочих подходов на группу мышц в неделю. Не больше 6 упражнений за тренировку.
- Для каждого упражнения дай alternativeId из того же списка: вариант на случай дискомфорта или занятого снаряда.
- Не назначай веса. Не ставь работу до отказа.
- Ровно ${profile.trainingDays} дня, назови их A, B, C, D по порядку.
Верни только JSON без пояснений по схеме:
{"days":[{"name":"A","exercises":[{"exerciseId":"goblet_squat","sets":3,"repMin":8,"repMax":12,"restSec":90,"alternativeId":"split_squat_db"}]}],"rationale":"коротко по-русски, почему так"}

Доступные упражнения (id | название | паттерн | размер):
$catalogText
""".trimIndent()
            val messages = mutableListOf(Msg("user", "Составь программу."))
            for (attempt in 1..3) {
                onStatus("ИИ составляет программу, попытка $attempt…")
                try {
                    val obj = claude.completeJson(system, messages, maxTokens = 3000)
                    val candidate = json.decodeFromJsonElement(ProgramPlan.serializer(), obj).copy(source = "ai")
                    val errs = PlanValidator.validate(candidate, inv, profile.trainingDays, profile.sessionMinutes, banned)
                    if (errs.isEmpty()) { plan = candidate; break }
                    log += "Попытка $attempt: ${errs.joinToString("; ")}"
                    messages += Msg("assistant", json.encodeToString(candidate))
                    messages += Msg("user", "План не прошёл проверку:\n- ${errs.joinToString("\n- ")}\nИсправь и верни полный JSON.")
                } catch (e: Exception) {
                    log += "Попытка $attempt: ${e.message}"
                }
            }
        } else log += "Ключ Claude не задан — использую базовый план."
        val final = plan ?: PlanValidator.fallbackPlan(inv, profile.trainingDays, banned)
        saveProgram(final, inv)
        return final to log
    }

    private suspend fun saveProgram(plan: ProgramPlan, inv: Inventory) {
        dao.deactivateAll()
        dao.dropUnfinished()
        dao.insertProgram(ProgramEntity(createdAt = System.currentTimeMillis(), json = json.encodeToString(plan)))
        val existing = dao.statesNow().associateBy { it.exerciseId }
        plan.days.flatMap { it.exercises }.distinctBy { it.exerciseId }.forEach { pe ->
            val ex = Catalog[pe.exerciseId] ?: return@forEach
            val old = existing[pe.exerciseId]
            // Прогресс переносится по id упражнения, диапазоны берём из нового плана
            val st = old?.copy(sets = pe.sets, repMin = pe.repMin, repMax = pe.repMax,
                targetReps = old.targetReps.coerceIn(pe.repMin, pe.repMax))
                ?: ExerciseStateEntity(
                    exerciseId = pe.exerciseId,
                    weightKg = Engine.starterWeight(ex, inv.weightsFor(ex.load)),
                    sets = pe.sets, repMin = pe.repMin, repMax = pe.repMax, targetReps = pe.repMin,
                )
            dao.upsertState(st)
        }
    }

    // ---------- Сегодняшняя тренировка ----------

    suspend fun nextSession(profile: Profile): SessionPlan? {
        val prog = dao.activeProgramNow() ?: return null
        val plan = parsePlan(prog) ?: return null
        val done = dao.lastWorkouts(500)
        val sinceProgram = done.filter { it.startedAt >= prog.createdAt }
        val day = plan.days[sinceProgram.size % plan.days.size]
        val today = LocalDate.now().toEpochDay()
        val programWeek = ((today - LocalDate.ofEpochDay(prog.createdAt / 86_400_000).toEpochDay()) / 7).toInt()
        val deload = Engine.isDeloadWeek(programWeek)
        val states = dao.statesNow().associateBy { it.exerciseId }
        val inv = profile.inventory

        // Перерыв
        val lastDay = done.firstOrNull()?.day
        val gap = lastDay?.let { today - it } ?: 0
        var breakNote: String? = null

        // Прочая активность за последние 2 дня
        val others = dao.othersRange(today - 2, today)
        val heavyZones = others.filter { it.load >= 400 || it.zones.contains("LEGS") || it.zones.contains("BACK") }
        val labourNote = if (heavyZones.isNotEmpty())
            "Недавно: ${heavyZones.joinToString { "${it.title} (${it.load} ед.)" }}. Ноги и спину сегодня не грузим: присед/наклон пропущены, остальное по плану."
        else null

        val items = day.exercises.map { pe ->
            var st = states[pe.exerciseId]?.toModel() ?: ExState(pe.exerciseId, 0.0, pe.sets, pe.repMin, pe.repMax, pe.repMin)
            val baseEx = Catalog[pe.exerciseId]!!
            val ex = st.variantId?.let { Catalog[it] } ?: baseEx
            val exWeights = inv.weightsFor(ex.load)
            if (exWeights.any { it > 0 } && st.weightKg <= 0.0) {
                // Перешли с варианта без веса на вариант с отягощением — нужна новая прикидка
                st = st.copy(weightKg = Engine.starterWeight(ex, exWeights), calibrated = false, calibrationSessions = 0)
            } else if (exWeights.none { it > 0 } && st.weightKg > 0.0) {
                st = st.copy(weightKg = 0.0)
            }
            if (gap > 14 && st.calibrated) {
                val (s2, note) = Engine.afterBreak(gap, st, inv.weightsFor(ex.load))
                st = s2; breakNote = note
                dao.upsertState(st.toEntity())
            }
            val skip = if (labourNote != null && ex.pattern in setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE, Pattern.CARRY))
                "после физработы" else null
            SessionItem(pe, ex, st, if (deload) Engine.deloadSets(st.sets) else st.sets, skip)
        }
        val calibration = items.any { !it.state.calibrated }
        return SessionPlan(day.name, items, calibration, deload && !calibration, labourNote, breakNote, programWeek)
    }

    data class ItemResult(val item: SessionItem, val weight: Double, val reps: List<Int>, val feel: Feel, val pain: Int)

    suspend fun finishSession(profile: Profile, session: SessionPlan, results: List<ItemResult>, startedAt: Long): List<String> {
        val today = LocalDate.now().toEpochDay()
        val wid = dao.insertWorkout(WorkoutEntity(day = today, startedAt = startedAt, dayName = session.dayName, calibration = session.calibration))
        val msgs = mutableListOf<String>()
        val freeze = session.deload || Engine.shouldFreeze(results.map { it.feel })
        if (freeze && !session.deload) msgs += "Больше половины упражнений «тяжело»: в этот раз ничего не повышаем."
        val heavyLabour = session.labourNote != null
        results.forEach { r ->
            r.reps.forEachIndexed { i, reps -> dao.insertSet(SetLog(workoutId = wid, exerciseId = r.item.exercise.id, setIndex = i, weightKg = r.weight, reps = reps)) }
            dao.insertFeedback(ExerciseFeedback(workoutId = wid, exerciseId = r.item.exercise.id, feel = r.feel.name, pain = r.pain))
            val weights = profile.inventory.weightsFor(r.item.exercise.load)
            val res = ExResult(r.reps, r.weight, r.feel, r.pain,
                afterHeavyLabour = heavyLabour && r.item.exercise.pattern in setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE))
            val out = if (!r.item.state.calibrated) Engine.calibrate(r.item.exercise, r.item.state, res, weights)
                else Engine.progress(r.item.exercise, r.item.state, res, weights, freezeIncreases = freeze)
            dao.upsertState(out.state.toEntity())
            msgs += "${r.item.exercise.nameRu}: ${out.message}"
        }
        dao.finishWorkout(wid, System.currentTimeMillis())
        return msgs
    }

    // ---------- Календарь ----------

    /** Какой день программы следующий: по кругу, считая тренировки с момента создания программы. */
    fun nextIndex(prog: ProgramEntity?, workouts: List<WorkoutEntity>): Int =
        prog?.let { p -> workouts.count { it.finishedAt != null && it.startedAt >= p.createdAt } } ?: 0

    /** Календарь [from]..[to] из того, что уже загружено экраном (Flow), — без запросов к базе. */
    fun calendar(
        profile: Profile, prog: ProgramEntity?, workouts: List<WorkoutEntity>, others: List<OtherActivity>,
        marks: List<TrainingMark>, from: Long, to: Long, today: Long = LocalDate.now().toEpochDay(),
    ): List<CalDay> {
        val plan = parsePlan(prog)
        val start = prog?.let { java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() } ?: Long.MAX_VALUE
        return Schedule.build(
            from, to, today, profile.weekdays,
            workouts = workouts.filter { it.finishedAt != null }.groupBy({ it.day }, { it.dayName }),
            replaced = others.filter { it.replacesWorkout }.map { it.day }.toSet(),
            marks = marks.associate { it.day to it.status },
            planDays = plan?.days?.map { it.name }.orEmpty(),
            nextIndex = nextIndex(prog, workouts),
            start = start,
        )
    }

    suspend fun calendarNow(profile: Profile, from: Long, to: Long): List<CalDay> =
        calendar(profile, dao.activeProgramNow(), dao.lastWorkouts(1000), dao.othersRange(from, to), dao.marksNow(), from, to)

    suspend fun mark(day: Long, status: String, note: String = "") = dao.upsertMark(TrainingMark(day, status, note))

    /** Снять отметку. Если это был перенос — убрать и «переехавший» день. */
    suspend fun clearMark(day: Long) {
        val all = dao.marksNow()
        all.filter { it.status == Mark.EXTRA && it.note == "из $day" }.forEach { dao.deleteMark(it.day) }
        dao.deleteMark(day)
    }

    /** Перенести тренировку с [day] на следующий день (если он и так плановый — просто освобождаем [day]). */
    suspend fun moveToNextDay(day: Long, weekdays: Set<Int>) {
        val marks = dao.marksNow().associate { it.day to it.status }
        dao.upsertMark(TrainingMark(day, Mark.MOVED))
        val next = day + 1
        if (!Schedule.isPlanned(next, weekdays, marks)) dao.upsertMark(TrainingMark(next, Mark.EXTRA, "из $day"))
    }

    suspend fun addOther(day: Long, title: String, minutes: Int, rpe: Int, zones: Set<String>, replaces: Boolean) {
        dao.insertOther(OtherActivity(day = day, title = title, activeMinutes = minutes, rpe = rpe,
            zones = zones.joinToString(","), load = minutes * rpe, replacesWorkout = replaces))
    }
}

fun ExerciseStateEntity.toModel() = ExState(exerciseId, weightKg, sets, repMin, repMax, targetReps, hardStreak, easyStreak, calibrated, calibrationSessions, variantId)
fun ExState.toEntity() = ExerciseStateEntity(exerciseId, weightKg, sets, repMin, repMax, targetReps, hardStreak, easyStreak, calibrated, calibrationSessions, System.currentTimeMillis(), variantId)
