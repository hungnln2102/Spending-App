package com.spendingapp

import android.app.Application
import com.spendingapp.core.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendingApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.localNotificationService.createChannels()
        val notificationSettings = container.notificationSettingsRepository.getSettings()
        if (notificationSettings.cashReminderEnabled) {
            container.cashReminderScheduler.schedule(notificationSettings.cashReminderIntervalDays)
        } else {
            container.cashReminderScheduler.cancel()
        }
        container.sePayBackgroundSyncScheduler.apply(container.backgroundSyncSettingsRepository.getSettings())
        applicationScope.launch {
            container.databaseSeeder.seedIfNeeded()
        }
    }
}



