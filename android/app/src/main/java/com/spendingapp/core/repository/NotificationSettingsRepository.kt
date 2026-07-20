package com.spendingapp.core.repository

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSettings(
    val allEnabled: Boolean = true,
    val budgetEnabled: Boolean = true,
    val goalEnabled: Boolean = true,
    val cashReminderEnabled: Boolean = false,
    val cashReminderIntervalDays: Int = 7,
)

class NotificationSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<NotificationSettings> = settingsState.asStateFlow()

    fun getSettings(): NotificationSettings = settingsState.value

    private fun readSettings(): NotificationSettings = NotificationSettings(
        allEnabled = preferences.getBoolean(KEY_ALL_ENABLED, true),
        budgetEnabled = preferences.getBoolean(KEY_BUDGET_ENABLED, true),
        goalEnabled = preferences.getBoolean(KEY_GOAL_ENABLED, true),
        cashReminderEnabled = preferences.getBoolean(KEY_CASH_REMINDER_ENABLED, false),
        cashReminderIntervalDays = preferences.getInt(KEY_CASH_REMINDER_INTERVAL_DAYS, DEFAULT_CASH_REMINDER_INTERVAL_DAYS),
    )

    fun saveSettings(settings: NotificationSettings) {
        preferences.edit()
            .putBoolean(KEY_ALL_ENABLED, settings.allEnabled)
            .putBoolean(KEY_BUDGET_ENABLED, settings.budgetEnabled)
            .putBoolean(KEY_GOAL_ENABLED, settings.goalEnabled)
            .putBoolean(KEY_CASH_REMINDER_ENABLED, settings.cashReminderEnabled)
            .putInt(KEY_CASH_REMINDER_INTERVAL_DAYS, settings.cashReminderIntervalDays.coerceIn(1, 30))
            .apply()
        settingsState.value = readSettings()
    }

    fun update(transform: (NotificationSettings) -> NotificationSettings): NotificationSettings {
        val updated = transform(getSettings())
        saveSettings(updated)
        return updated
    }

    private companion object {
        const val KEY_ALL_ENABLED = "all_enabled"
        const val KEY_BUDGET_ENABLED = "budget_enabled"
        const val KEY_GOAL_ENABLED = "goal_enabled"
        const val KEY_CASH_REMINDER_ENABLED = "cash_reminder_enabled"
        const val KEY_CASH_REMINDER_INTERVAL_DAYS = "cash_reminder_interval_days"
        const val DEFAULT_CASH_REMINDER_INTERVAL_DAYS = 7
    }
}
