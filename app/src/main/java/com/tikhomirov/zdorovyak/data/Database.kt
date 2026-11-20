package com.tikhomirov.zdorovyak.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {
    @Query("SELECT * FROM weight ORDER BY day DESC, id DESC")
    fun weights(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight ORDER BY day DESC, id DESC LIMIT :n")
    suspend fun lastWeights(n: Int): List<WeightEntry>

    @Query("SELECT * FROM weight WHERE day >= :from ORDER BY day, id")
    suspend fun weightsSince(from: Long): List<WeightEntry>

    @Insert suspend fun insertWeight(e: WeightEntry): Long
    @Delete suspend fun deleteWeight(e: WeightEntry)

    @Query("SELECT * FROM measurement ORDER BY day DESC, id DESC")
    fun measurements(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurement ORDER BY day DESC, id DESC LIMIT :n")
    suspend fun lastMeasurements(n: Int): List<Measurement>

    @Insert suspend fun insertMeasurement(m: Measurement): Long
    @Delete suspend fun deleteMeasurement(m: Measurement)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food WHERE day = :day ORDER BY time")
    fun forDay(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food WHERE day BETWEEN :from AND :to ORDER BY day, time")
    suspend fun range(from: Long, to: Long): List<FoodEntry>

    @Insert suspend fun insert(e: FoodEntry): Long
    @Delete suspend fun delete(e: FoodEntry)

    @Query("SELECT * FROM food_item ORDER BY lastUsed DESC LIMIT 30")
    fun recentItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_item WHERE name LIKE '%' || :q || '%' ORDER BY lastUsed DESC LIMIT 30")
    suspend fun search(q: String): List<FoodItem>

    @Query("SELECT * FROM food_item WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): FoodItem?

    @Query("SELECT * FROM food_item WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): FoodItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(i: FoodItem): Long

    @Query("UPDATE food_item SET lastUsed = :t WHERE id = :id")
    suspend fun touchItem(id: Long, t: Long)

    @Query("SELECT * FROM food WHERE day = :day ORDER BY time")
    suspend fun forDayNow(day: Long): List<FoodEntry>

    @Query("SELECT day, SUM(kcal) AS kcal FROM food WHERE day BETWEEN :from AND :to GROUP BY day")
    suspend fun kcalByDay(from: Long, to: Long): List<DayKcal>
}

data class DayKcal(val day: Long, val kcal: Double)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM program WHERE active = 1 ORDER BY createdAt DESC LIMIT 1")
    fun activeProgram(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE active = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeProgramNow(): ProgramEntity?

    @Query("UPDATE program SET active = 0")
    suspend fun deactivateAll()

    @Insert suspend fun insertProgram(p: ProgramEntity): Long

    @Query("SELECT * FROM exercise_state")
    fun states(): Flow<List<ExerciseStateEntity>>

    @Query("SELECT * FROM exercise_state")
    suspend fun statesNow(): List<ExerciseStateEntity>

    @Upsert suspend fun upsertState(s: ExerciseStateEntity)

    @Query("DELETE FROM exercise_state")
    suspend fun clearStates()

    @Insert suspend fun insertWorkout(w: WorkoutEntity): Long

    @Query("UPDATE workout SET finishedAt = :t WHERE id = :id")
    suspend fun finishWorkout(id: Long, t: Long)

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finishedWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :n")
    suspend fun lastWorkouts(n: Int): List<WorkoutEntity>

    @Query("DELETE FROM workout WHERE finishedAt IS NULL")
    suspend fun dropUnfinished()

    @Insert suspend fun insertSet(s: SetLog): Long

    @Query("SELECT * FROM set_log WHERE workoutId = :w ORDER BY exerciseId, setIndex")
    suspend fun setsFor(w: Long): List<SetLog>

    @Insert suspend fun insertFeedback(f: ExerciseFeedback): Long

    @Query("SELECT * FROM exercise_feedback WHERE workoutId = :w")
    suspend fun feedbackFor(w: Long): List<ExerciseFeedback>

    @Insert suspend fun insertOther(a: OtherActivity): Long
    @Delete suspend fun deleteOther(a: OtherActivity)

    @Query("SELECT * FROM other_activity ORDER BY day DESC, id DESC")
    fun others(): Flow<List<OtherActivity>>

    @Query("SELECT * FROM other_activity WHERE day BETWEEN :from AND :to")
    suspend fun othersRange(from: Long, to: Long): List<OtherActivity>
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY time")
    fun all(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM (SELECT * FROM chat ORDER BY time DESC LIMIT :n) ORDER BY time")
    suspend fun last(n: Int): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat")
    suspend fun count(): Int

    @Insert suspend fun insert(m: ChatMessage): Long

    @Query("DELETE FROM chat")
    suspend fun clear()
}

@Dao
interface GarminDao {
    @Upsert suspend fun upsertDay(d: GarminDay)
    @Upsert suspend fun upsertActivity(a: GarminActivity)

    @Query("SELECT * FROM garmin_day ORDER BY day DESC LIMIT :n")
    fun lastDays(n: Int): Flow<List<GarminDay>>

    @Query("SELECT * FROM garmin_day ORDER BY day DESC LIMIT :n")
    suspend fun lastDaysNow(n: Int): List<GarminDay>

    @Query("SELECT * FROM garmin_day WHERE day = :day")
    fun day(day: Long): Flow<GarminDay?>

    @Query("SELECT * FROM garmin_activity WHERE day BETWEEN :from AND :to ORDER BY day DESC")
    suspend fun activitiesRange(from: Long, to: Long): List<GarminActivity>

    @Query("SELECT * FROM garmin_activity ORDER BY day DESC LIMIT :n")
    fun lastActivities(n: Int): Flow<List<GarminActivity>>
}

@Database(
    entities = [
        WeightEntry::class, Measurement::class, FoodEntry::class, FoodItem::class,
        ProgramEntity::class, ExerciseStateEntity::class, WorkoutEntity::class, SetLog::class,
        ExerciseFeedback::class, OtherActivity::class, ChatMessage::class,
        GarminDay::class, GarminActivity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun body(): BodyDao
    abstract fun food(): FoodDao
    abstract fun training(): TrainingDao
    abstract fun chat(): ChatDao
    abstract fun garmin(): GarminDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "zdorovyak.db").addMigrations(MIGRATION_1_2).build()

        /**
         * v2: у продукта в справочнике появился источник данных. Старые продукты считаем введёнными руками,
         * кроме тех, что попали туда из анализа фото тарелки: это оценки ИИ.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_item ADD COLUMN source TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("UPDATE food_item SET source = 'off' WHERE barcode IS NOT NULL")
                db.execSQL("UPDATE food_item SET source = 'ai' WHERE barcode IS NULL AND name IN (SELECT name FROM food WHERE source = 'photo') AND name NOT IN (SELECT name FROM food WHERE source = 'manual')")
            }
        }
    }
}
