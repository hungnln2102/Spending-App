package com.spendingapp.core.notification

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.repository.NotificationSettings
import com.spendingapp.core.repository.NotificationSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalNotificationServiceTest {
    private lateinit var context: Context
    private lateinit var settingsRepository: NotificationSettingsRepository
    private lateinit var service: LocalNotificationService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settingsRepository = NotificationSettingsRepository(context)
        service = LocalNotificationService(context, settingsRepository)
        service.createChannels()
    }

    @Test
    fun createChannelsRegistersBudgetGoalAndReminderChannels() {
        val shadowManager = shadowOf(context.getSystemService(NotificationManager::class.java))

        assertEquals(3, shadowManager.notificationChannels.size)
    }

    @Test
    fun budgetNotificationRespectsSettings() {
        settingsRepository.saveSettings(NotificationSettings(allEnabled = false, budgetEnabled = true))

        service.notifyBudgetResult(BudgetCheckResult.WarningTriggered(sampleBudget(), 90_000))

        val shadowManager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(0, shadowManager.allNotifications.size)
    }

    private fun sampleBudget(): BudgetEntity = BudgetEntity(
        id = 7,
        categoryId = 1,
        month = "2026-07",
        limitAmount = 100_000,
        warningThresholdPercent = 80,
    )
}
