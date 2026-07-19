package com.spendingapp.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendingapp.core.repository.NotificationSettingsRepository

class CashReminderWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val settingsRepository = NotificationSettingsRepository(applicationContext)
        val notificationService = LocalNotificationService(applicationContext, settingsRepository)
        notificationService.notifyCashReminder()
        return Result.success()
    }
}
