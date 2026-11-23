package com.tikhomirov.zdorovyak.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikhomirov.zdorovyak.App
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.BodyPhoto
import com.tikhomirov.zdorovyak.data.Measurement
import com.tikhomirov.zdorovyak.data.WeightEntry
import com.tikhomirov.zdorovyak.strength.TrainingReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/** Ракурсы. Порядок съёмки: фронт → бок → спина. */
enum class Pose(val ru: String, val hint: String) {
    FRONT("Анфас", "Лицом к камере, руки чуть в стороны, стопы на ширине таза"),
    SIDE("Бок", "Правым боком, руки вдоль тела или скрещены на груди"),
    BACK("Спина", "Спиной к камере, руки чуть в стороны"),
}

/**
 * Фото прогресса: хранение и подписи. Только локально — файлы во внутренней памяти приложения,
 * в резервную копию Android не попадают (allowBackup = false), в Claude не отправляются.
 */
class BodyPhotos(private val ctx: Context, private val db: AppDatabase) {
    private val dir get() = File(ctx.filesDir, "body_photos").apply { mkdirs() }

    fun tempFile(): File = File(ctx.cacheDir, "body_${System.currentTimeMillis()}.jpg")

    /** Сохранить снимок: переносим во внутреннюю папку, тот же ракурс в тот же день заменяем. */
    suspend fun save(day: Long, pose: Pose, tmp: File) = withContext(Dispatchers.IO) {
        val dst = File(dir, "${LocalDate.ofEpochDay(day)}_${pose.name}_${System.currentTimeMillis()}.jpg")
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, overwrite = true); tmp.delete() }
        db.body().photo(day, pose.name)?.let { old -> File(old.filePath).delete(); db.body().deletePhoto(old) }
        db.body().upsertPhoto(BodyPhoto(day = day, pose = pose.name, filePath = dst.absolutePath))
    }

    suspend fun delete(p: BodyPhoto) = withContext(Dispatchers.IO) {
        File(p.filePath).delete()
        db.body().deletePhoto(p)
    }

    companion object {
        /** Загрузка с уменьшением и поворотом по EXIF (камера пишет ориентацию в метаданные, а не в пиксели). */
        fun load(path: String, maxSide: Int = 1400): Bitmap? = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val m = Matrix()
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                else -> return bmp
            }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }.getOrNull()

        /** Ближайшее взвешивание к дате фото (± 4 дня), иначе null — подпись без веса честнее, чем чужой вес. */
        fun weightNear(day: Long, weights: List<WeightEntry>): Double? =
            weights.filter { abs(it.day - day) <= 4 }.minByOrNull { abs(it.day - day) }?.weightKg

        fun waistNear(day: Long, ms: List<Measurement>): Double? =
            ms.filter { it.waist != null && abs(it.day - day) <= 7 }.minByOrNull { abs(it.day - day) }?.waist

        /** Пора ли фотографироваться: никогда не снимали или прошло N дней. */
        fun due(lastDay: Long?, everyDays: Int, today: Long = LocalDate.now().toEpochDay()): Boolean =
            everyDays > 0 && (lastDay == null || today - lastDay >= everyDays)
    }
}

/** Напоминание о фото: раз в день в 9:00 проверяем, не пора ли. Не снимали ни разу — не дёргаем, подскажет экран «Сегодня». */
class PhotoReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val every = app.settings.profile.value.photoEveryDays
        runCatching {
            val last = app.db.body().lastPhotoDay()
            if (last != null && BodyPhotos.due(last, every)) {
                val days = LocalDate.now().toEpochDay() - last
                TrainingReminder.notify(applicationContext, "Фото прогресса", "Прошло $days дн. с прошлых фото. Утром, натощак, при том же свете — 3 ракурса за пару минут.", id = 1002)
            }
        }
        schedule(applicationContext, every)
        return Result.success()
    }

    companion object {
        private const val WORK = "photo_reminder"
        fun schedule(ctx: Context, everyDays: Int) {
            val wm = WorkManager.getInstance(ctx)
            if (everyDays <= 0) { wm.cancelUniqueWork(WORK); return }
            val now = LocalDateTime.now()
            var at = now.toLocalDate().atTime(9, 0)
            if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PhotoReminder>().setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS).build())
        }
    }
}
