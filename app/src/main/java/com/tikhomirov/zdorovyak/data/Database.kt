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

    @Query("SELECT * FROM body_photo ORDER BY day DESC, pose")
    fun photos(): Flow<List<BodyPhoto>>

    @Query("SELECT * FROM body_photo WHERE day = :day AND pose = :pose")
    suspend fun photo(day: Long, pose: String): BodyPhoto?

    @Query("SELECT MAX(day) FROM body_photo")
    suspend fun lastPhotoDay(): Long?

    @Upsert suspend fun upsertPhoto(p: BodyPhoto)
    @Delete suspend fun deletePhoto(p: BodyPhoto)
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

    @Query("UPDATE workout SET note = :note WHERE id = :id")
    suspend fun setWorkoutNote(id: Long, note: String)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL AND day = :day ORDER BY startedAt")
    suspend fun workoutsOn(day: Long): List<WorkoutEntity>

    /** Подходы по упражнению в последней тренировке до [before] (для сравнения «в прошлый раз»). */
    @Query("""SELECT s.* FROM set_log s WHERE s.exerciseId = :ex AND s.workoutId = (
        SELECT w.id FROM workout w JOIN set_log s2 ON s2.workoutId = w.id
        WHERE s2.exerciseId = :ex AND w.finishedAt IS NOT NULL AND w.startedAt < :before
        ORDER BY w.startedAt DESC LIMIT 1) ORDER BY s.setIndex""")
    suspend fun previousSets(ex: String, before: Long): List<SetLog>

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finishedWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :n")
    suspend fun lastWorkouts(n: Int): List<WorkoutEntity>

    @Query("SELECT * FROM workout WHERE finishedAt IS NOT NULL AND day BETWEEN :from AND :to")
    suspend fun workoutsRange(from: Long, to: Long): List<WorkoutEntity>

    @Query("SELECT * FROM exercise_feedback WHERE workoutId IN (:ids)")
    suspend fun feedbackForAll(ids: List<Long>): List<ExerciseFeedback>

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

    @Query("SELECT * FROM training_mark")
    fun marks(): Flow<List<TrainingMark>>

    @Query("SELECT * FROM training_mark")
    suspend fun marksNow(): List<TrainingMark>

    @Upsert suspend fun upsertMark(m: TrainingMark)

    @Query("DELETE FROM training_mark WHERE day = :day")
    suspend fun deleteMark(day: Long)
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

@Dao
interface LabDao {
    @Query("SELECT * FROM lab_report ORDER BY day DESC, id DESC")
    fun reports(): Flow<List<LabReport>>

    @Query("SELECT * FROM lab_report WHERE id = :id")
    suspend fun report(id: Long): LabReport?

    @Query("SELECT * FROM lab_result ORDER BY day, id")
    fun results(): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_result ORDER BY day, id")
    suspend fun resultsNow(): List<LabResult>

    @Query("SELECT * FROM lab_result WHERE reportId = :reportId ORDER BY id")
    suspend fun resultsFor(reportId: Long): List<LabResult>

    @Insert suspend fun insertReport(r: LabReport): Long
    @androidx.room.Update suspend fun updateReport(r: LabReport)
    @Insert suspend fun insertResults(r: List<LabResult>)
    @androidx.room.Update suspend fun updateResult(r: LabResult)
    @Delete suspend fun deleteResult(r: LabResult)

    @Query("UPDATE lab_result SET day = :day WHERE reportId = :reportId")
    suspend fun moveResults(reportId: Long, day: Long)

    @Query("DELETE FROM lab_result WHERE reportId = :reportId")
    suspend fun deleteResultsFor(reportId: Long)

    @Query("DELETE FROM lab_report WHERE id = :id")
    suspend fun deleteReport(id: Long)
}

@Database(
    entities = [
        WeightEntry::class, Measurement::class, FoodEntry::class, FoodItem::class,
        ProgramEntity::class, ExerciseStateEntity::class, WorkoutEntity::class, SetLog::class,
        ExerciseFeedback::class, OtherActivity::class, ChatMessage::class,
        GarminDay::class, GarminActivity::class,
        LabReport::class, LabResult::class,
        TrainingMark::class, BodyPhoto::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun body(): BodyDao
    abstract fun food(): FoodDao
    abstract fun training(): TrainingDao
    abstract fun chat(): ChatDao
    abstract fun garmin(): GarminDao
    abstract fun labs(): LabDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "zdorovyak.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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
    
        /** v3 (0.3.0): раздел «Анализы». Только новые таблицы, старые данные не трогаем. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `lab_report` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `lab` TEXT NOT NULL, `title` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mime` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `interpretation` TEXT, `interpretedAt` INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_report_day` ON `lab_report` (`day`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `lab_result` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `value` REAL, `valueText` TEXT NOT NULL, `unit` TEXT NOT NULL, `refLow` REAL, `refHigh` REAL, `refText` TEXT NOT NULL, `flag` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_result_reportId` ON `lab_result` (`reportId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_result_code` ON `lab_result` (`code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_result_day` ON `lab_result` (`day`)")
            }
        }

        /** v4 (0.4.0): календарь тренировок — отметки «сделал / пропустил / перенёс». Только новая таблица. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `training_mark` (`day` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            }
        }

        /** v5 (0.5.0): фото прогресса тела. Только новая таблица. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `body_photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `pose` TEXT NOT NULL, `filePath` TEXT NOT NULL, `takenAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_body_photo_day_pose` ON `body_photo` (`day`, `pose`)")
            }
        }
    }
}
