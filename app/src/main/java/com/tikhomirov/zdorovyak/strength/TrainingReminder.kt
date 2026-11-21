package com.tikhomirov.zdorovyak.strength

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikhomirov.zdorovyak.App
import com.tikhomirov.zdorovyak.MainActivity
import com.tikhomirov.zdorovyak.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Напоминание в день тренировки: раз в день в выбранный час проверяем календарь.
 * Если сегодня плановая силовая и она ещё не сделана/не отмечена — уведомление. Потом ставим себя на завтра.
 */
class TrainingReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val profile = app.settings.profile.value
        runCatching {
            val today = LocalDate.now().toEpochDay()
            val day = app.training.calendarNow(profile, today, today).firstOrNull()
            if (day?.status == DayStatus.TODAY) {
                val s = app.training.nextSession(profile)
                notify(applicationContext, "Сегодня силовая${s?.let { " · день ${it.dayName}" } ?: ""}",
                    "≈${profile.sessionMinutes} мин. Не получается — отметьте пропуск или перенесите в календаре.")
            }
        }
        schedule(applicationContext, profile.remindHour)
        return Result.success()
    }

    companion object {
        private const val WORK = "training_reminder"
        private const val CHANNEL = "training"

        fun schedule(ctx: Context, hour: Int) {
            val wm = WorkManager.getInstance(ctx)
            if (hour !in 0..23) { wm.cancelUniqueWork(WORK); return }
            val now = LocalDateTime.now()
            var at = now.toLocalDate().atTime(hour, 0)
            if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
            val req = OneTimeWorkRequestBuilder<TrainingReminder>()
                .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        }

        fun canNotify(ctx: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun notify(ctx: Context, title: String, text: String) {
            if (!canNotify(ctx)) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Тренировки", NotificationManager.IMPORTANCE_DEFAULT))
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_dumbbell)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi).setAutoCancel(true)
                .build()
            nm.notify(1001, n)
        }
    }
}
