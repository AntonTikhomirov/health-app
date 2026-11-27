package com.tikhomirov.zdorovyak.coach

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikhomirov.zdorovyak.App
import com.tikhomirov.zdorovyak.MainActivity
import com.tikhomirov.zdorovyak.R
import com.tikhomirov.zdorovyak.strength.TrainingReminder
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Раз в день в выбранный час: проверить поводы и, если есть, прислать одну подсказку. Потом ставит себя на завтра. */
class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        runCatching { app.nudges.runDaily()?.let { notify(applicationContext, it.title, it.text) } }
        schedule(applicationContext, app.settings.nudgesEnabled, app.settings.nudgeHour)
        return Result.success()
    }

    companion object {
        private const val WORK = "coach_nudge"
        private const val CHANNEL = "coach"

        fun schedule(ctx: Context, enabled: Boolean, hour: Int) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(WORK); return }
            val now = LocalDateTime.now()
            var at = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
            if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
            val req = OneTimeWorkRequestBuilder<NudgeWorker>()
                .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        }

        fun notify(ctx: Context, title: String, text: String) {
            if (!TrainingReminder.canNotify(ctx)) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Подсказки коуча", NotificationManager.IMPORTANCE_DEFAULT))
            val pi = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_dumbbell)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi).setAutoCancel(true)
                .build()
            nm.notify(1003, n)
        }
    }
}
