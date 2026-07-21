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

private val Context.backgroundSyncSettingsDataStore by preferencesDataStore(name = "background_sync_settings")

data class BackgroundSyncSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val wifiOnly: Boolean = false,
)

class BackgroundSyncSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.backgroundSyncSettingsDataStore
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<BackgroundSyncSettings> = settingsState.asStateFlow()

    fun getSettings(): BackgroundSyncSettings = settingsState.value

    private fun readSettings(): BackgroundSyncSettings = runBlocking {
        val preferences = dataStore.data.first()
        BackgroundSyncSettings(
            enabled = preferences[KEY_ENABLED] ?: false,
            intervalHours = (preferences[KEY_INTERVAL_HOURS] ?: 12).coerceIn(6, 24),
            wifiOnly = preferences[KEY_WIFI_ONLY] ?: false,
        )
    }

    fun saveSettings(settings: BackgroundSyncSettings): BackgroundSyncSettings {
        val normalized = settings.copy(intervalHours = settings.intervalHours.coerceIn(6, 24))
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_ENABLED] = normalized.enabled
                preferences[KEY_INTERVAL_HOURS] = normalized.intervalHours
                preferences[KEY_WIFI_ONLY] = normalized.wifiOnly
            }
        }
        settingsState.value = normalized
        return normalized
    }

    internal fun clearForTest() {
        runBlocking {
            dataStore.edit { preferences -> preferences.clear() }
        }
        settingsState.value = BackgroundSyncSettings()
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_INTERVAL_HOURS = intPreferencesKey("interval_hours")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }
}
