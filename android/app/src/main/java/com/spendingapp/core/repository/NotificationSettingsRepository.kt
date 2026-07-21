package com.spendingapp.core.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.notificationSettingsDataStore by preferencesDataStore(name = "notification_settings")

data class NotificationSettings(
    val allEnabled: Boolean = true,
    val budgetEnabled: Boolean = true,
    val goalEnabled: Boolean = true,
    val cashReminderEnabled: Boolean = false,
    val cashReminderIntervalDays: Int = 7,
)

class NotificationSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.notificationSettingsDataStore
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<NotificationSettings> = settingsState.asStateFlow()

    fun getSettings(): NotificationSettings = settingsState.value

    private fun readSettings(): NotificationSettings = runBlocking {
        val preferences = dataStore.data.first()
        NotificationSettings(
            allEnabled = preferences[KEY_ALL_ENABLED] ?: true,
            budgetEnabled = preferences[KEY_BUDGET_ENABLED] ?: true,
            goalEnabled = preferences[KEY_GOAL_ENABLED] ?: true,
            cashReminderEnabled = preferences[KEY_CASH_REMINDER_ENABLED] ?: false,
            cashReminderIntervalDays = (preferences[KEY_CASH_REMINDER_INTERVAL_DAYS] ?: DEFAULT_CASH_REMINDER_INTERVAL_DAYS).coerceIn(1, 30),
        )
    }

    fun saveSettings(settings: NotificationSettings) {
        val normalized = settings.copy(cashReminderIntervalDays = settings.cashReminderIntervalDays.coerceIn(1, 30))
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_ALL_ENABLED] = normalized.allEnabled
                preferences[KEY_BUDGET_ENABLED] = normalized.budgetEnabled
                preferences[KEY_GOAL_ENABLED] = normalized.goalEnabled
                preferences[KEY_CASH_REMINDER_ENABLED] = normalized.cashReminderEnabled
                preferences[KEY_CASH_REMINDER_INTERVAL_DAYS] = normalized.cashReminderIntervalDays
            }
        }
        settingsState.value = normalized
    }

    fun update(transform: (NotificationSettings) -> NotificationSettings): NotificationSettings {
        val updated = transform(getSettings())
        saveSettings(updated)
        return getSettings()
    }

    internal fun clearForTest() {
        runBlocking {
            dataStore.edit { preferences -> preferences.clear() }
        }
        settingsState.value = NotificationSettings()
    }

    private companion object {
        val KEY_ALL_ENABLED = booleanPreferencesKey("all_enabled")
        val KEY_BUDGET_ENABLED = booleanPreferencesKey("budget_enabled")
        val KEY_GOAL_ENABLED = booleanPreferencesKey("goal_enabled")
        val KEY_CASH_REMINDER_ENABLED = booleanPreferencesKey("cash_reminder_enabled")
        val KEY_CASH_REMINDER_INTERVAL_DAYS = intPreferencesKey("cash_reminder_interval_days")
        const val DEFAULT_CASH_REMINDER_INTERVAL_DAYS = 7
    }
}
