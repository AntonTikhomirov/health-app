package com.tikhomirov.zdorovyak.garmin

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikhomirov.zdorovyak.App
import java.util.concurrent.TimeUnit

/** Фоновая синхронизация Garmin раз в 6 часов при наличии сети. */
class GarminSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (!app.garmin.isLoggedIn) return Result.success()
        return runCatching { app.garmin.sync(3) }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<GarminSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("garmin_sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
