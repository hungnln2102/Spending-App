package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationSettingsRepositoryTest {
    private lateinit var repository: NotificationSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE).edit().clear().commit()
        repository = NotificationSettingsRepository(context)
    }

    @Test
    fun savesNotificationTogglesAndReminderInterval() {
        repository.saveSettings(
            NotificationSettings(
                allEnabled = false,
                budgetEnabled = false,
                goalEnabled = false,
                cashReminderEnabled = true,
                cashReminderIntervalDays = 99,
            ),
        )

        val settings = repository.getSettings()
        val flowSettings = repository.observeSettings().value
        assertFalse(settings.allEnabled)
        assertFalse(settings.budgetEnabled)
        assertFalse(settings.goalEnabled)
        assertTrue(settings.cashReminderEnabled)
        assertEquals(30, settings.cashReminderIntervalDays)
        assertEquals(settings, flowSettings)
    }
}
