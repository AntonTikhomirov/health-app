package com.tikhomirov.zdorovyak.strength

import com.tikhomirov.zdorovyak.data.ExerciseFeedback
import com.tikhomirov.zdorovyak.data.SetLog
import com.tikhomirov.zdorovyak.data.WorkoutEntity

/** Одно упражнение в дневнике: подходы, ощущение, сравнение с прошлым разом. */
data class LoggedExercise(
    val exerciseId: String,
    val name: String,
    val sets: List<Pair<Double, Int>>,   // вес × повторы
    val feel: Feel?,
    val pain: Int,
    val previous: List<Pair<Double, Int>>,
) {
    val tonnage: Double get() = sets.sumOf { it.first * it.second }
    val totalReps: Int get() = sets.sumOf { it.second }
    /** Сравнение с прошлым разом: тот же вес — по повторам, иначе — по весу. */
    val trend: Int get() {
        if (previous.isEmpty() || sets.isEmpty()) return 0
        val w = sets.maxOf { it.first }; val pw = previous.maxOf { it.first }
        return when {
            w > pw + 1e-6 -> 1
            w < pw - 1e-6 -> -1
            else -> totalReps.compareTo(previous.sumOf { it.second })
        }
    }
}

data class PlannedExercise(val name: String, val sets: Int, val reps: Int, val weightKg: Double, val calibrated: Boolean)

/** Запись дневника тренировок, собранная из workout + set_log + exercise_feedback. */
data class WorkoutLog(
    val workout: WorkoutEntity,
    val exercises: List<LoggedExercise>,
) {
    val minutes: Long get() = workout.finishedAt?.let { (it - workout.startedAt) / 60000 } ?: 0
    val sets: Int get() = exercises.sumOf { it.sets.size }
    val tonnage: Double get() = exercises.sumOf { it.tonnage }
    /** Выводы движка, сохранённые после тренировки (по строке на упражнение). */
    val notes: List<String> get() = workout.note.lines().filter { it.isNotBlank() }

    fun summary(): String = buildString {
        append("${exercises.size} упр. · $sets подх.")
        if (tonnage > 0) append(" · ${if (tonnage >= 1000) "%.1f т".format(tonnage / 1000).replace('.', ',') else "${tonnage.toInt()} кг"}")
        if (minutes > 0) append(" · $minutes мин")
    }

    companion object {
        suspend fun build(
            w: WorkoutEntity, sets: List<SetLog>, feedback: List<ExerciseFeedback>,
            previous: suspend (String) -> List<SetLog>,
        ): WorkoutLog {
            val fb = feedback.associateBy { it.exerciseId }
            // Порядок упражнений — как выполнялись (по id первой записи)
            val ids = (sets.sortedBy { it.id }.map { it.exerciseId } + feedback.map { it.exerciseId }).distinct()
            val ex = ids.map { id ->
                val mine = sets.filter { it.exerciseId == id }.sortedBy { it.setIndex }
                LoggedExercise(
                    exerciseId = id,
                    name = Catalog[id]?.nameRu ?: id,
                    sets = mine.map { it.weightKg to it.reps },
                    feel = fb[id]?.feel?.let { f -> Feel.entries.firstOrNull { it.name == f } },
                    pain = fb[id]?.pain ?: 0,
                    previous = previous(id).map { it.weightKg to it.reps },
                )
            }
            return WorkoutLog(w, ex)
        }
    }
}
