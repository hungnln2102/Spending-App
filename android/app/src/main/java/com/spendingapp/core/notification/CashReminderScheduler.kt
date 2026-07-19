package com.spendingapp.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class CashReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun schedule(intervalDays: Int) {
        val request = PeriodicWorkRequestBuilder<CashReminderWorker>(
            intervalDays.coerceIn(1, 30).toLong(),
            TimeUnit.DAYS,
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
        const val WORK_NAME = "cash_balance_reminder"
    }
}
