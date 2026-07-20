package com.spendingapp.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendingapp.core.data.AppContainer
import com.spendingapp.core.repository.BackgroundSyncSettings
import java.util.concurrent.TimeUnit

class SePayBackgroundSyncWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        val token = container.secureTokenStorage.getSePayToken() ?: return Result.success()
        return runCatching {
            container.sePaySyncService.sync(token)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class SePayBackgroundSyncScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun apply(settings: BackgroundSyncSettings) {
        if (!settings.enabled) {
            cancel()
            return
        }
        schedule(settings.intervalHours, settings.wifiOnly)
    }

    fun schedule(intervalHours: Int, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<SePayBackgroundSyncWorker>(
            intervalHours.coerceIn(6, 24).toLong(),
            TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build(),
        ).setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.MINUTES,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "sepay_background_sync"
    }
}
