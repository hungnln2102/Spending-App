package com.spendingapp.core.repository

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackgroundSyncSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val wifiOnly: Boolean = false,
)

class BackgroundSyncSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("background_sync_settings", Context.MODE_PRIVATE)
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<BackgroundSyncSettings> = settingsState.asStateFlow()

    fun getSettings(): BackgroundSyncSettings = settingsState.value

    private fun readSettings(): BackgroundSyncSettings = BackgroundSyncSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        intervalHours = preferences.getInt(KEY_INTERVAL_HOURS, 12).coerceIn(6, 24),
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, false),
    )

    fun saveSettings(settings: BackgroundSyncSettings): BackgroundSyncSettings {
        val normalized = settings.copy(intervalHours = settings.intervalHours.coerceIn(6, 24))
        preferences.edit {
            putBoolean(KEY_ENABLED, normalized.enabled)
            putInt(KEY_INTERVAL_HOURS, normalized.intervalHours)
            putBoolean(KEY_WIFI_ONLY, normalized.wifiOnly)
        }
        settingsState.value = normalized
        return normalized
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL_HOURS = "interval_hours"
        private const val KEY_WIFI_ONLY = "wifi_only"
    }
}
