package com.tikhomirov.zdorovyak.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Даты храним как epochDay (LocalDate.toEpochDay), время как epoch millis. */

@Entity(tableName = "weight", indices = [Index("day")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val note: String = "",
)

@Entity(tableName = "measurement", indices = [Index("day")])
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val waist: Double? = null,
    val chest: Double? = null,
    val hips: Double? = null,
    val arm: Double? = null,
    val thigh: Double? = null,
    val neck: Double? = null,
)

@Entity(tableName = "food", indices = [Index("day")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val time: Long,
    val meal: String,          // Завтрак / Обед / Ужин / Перекус
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val source: String = "manual", // manual / barcode / photo
    val barcode: String? = null,
)

/**
 * Справочник продуктов: на 100 г. Пополняется из штрихкодов, этикеток, ручного ввода и встроенной базы.
 * source — откуда цифры: user (введено руками) / label (фото этикетки) / off (Open Food Facts) /
 * base (встроенная база) / ai (оценка Claude). Более надёжный источник не перезаписывается менее надёжным.
 */
@Entity(tableName = "food_item", indices = [Index(value = ["barcode"]), Index(value = ["name"])])
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    val barcode: String? = null,
    val lastUsed: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "user") val source: String = "user",
)

@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val json: String,          // ProgramPlan в JSON
    val active: Boolean = true,
)

/** Состояние прогрессии по одному упражнению. */
@Entity(tableName = "exercise_state")
data class ExerciseStateEntity(
    @PrimaryKey val exerciseId: String,
    val weightKg: Double,      // 0 для упражнений с весом тела
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetReps: Int,
    val hardStreak: Int = 0,
    val easyStreak: Int = 0,
    val calibrated: Boolean = false,
    val calibrationSessions: Int = 0,
    val lastUpdated: Long = 0,
    val variantId: String? = null, // если перешли на облегчённый/усложнённый вариант
)

@Entity(tableName = "workout", indices = [Index("day")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val dayName: String,       // A / B / C
    val calibration: Boolean = false,
    val note: String = "",
)

@Entity(tableName = "set_log", indices = [Index("workoutId")])
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
)

@Entity(tableName = "exercise_feedback", indices = [Index("workoutId")])
data class ExerciseFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val feel: String,          // HARD / NORMAL / EASY
    val pain: Int = 0,         // 0..10
)

/** «Прочая активность»: физический труд по дому и т.п. Нагрузка = RPE × активные минуты. */
@Entity(tableName = "other_activity", indices = [Index("day")])
data class OtherActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val title: String,
    val activeMinutes: Int,
    val rpe: Int,              // 1..10
    val zones: String,         // через запятую: LEGS,BACK,ARMS,CORE,SHOULDERS
    val load: Int,             // RPE × мин
    val replacesWorkout: Boolean = false,
)

@Entity(tableName = "chat")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val role: String,          // user / assistant
    val content: String,
)

@Entity(tableName = "garmin_day")
data class GarminDay(
    @PrimaryKey val day: Long,
    val steps: Int? = null,
    val restingHr: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepScore: Int? = null,
    val hrvAvg: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val stressAvg: Int? = null,
    val activeKcal: Int? = null,
    val syncedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "garmin_activity")
data class GarminActivity(
    @PrimaryKey val activityId: Long,
    val day: Long,
    val type: String,
    val name: String,
    val durationMin: Double,
    val distanceKm: Double?,
    val avgHr: Int?,
    val kcal: Int?,
    val trainingLoad: Double?,
)
