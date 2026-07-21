package com.spendingapp.core.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.themeSettingsDataStore by preferencesDataStore(name = "theme_settings")

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class ThemeSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.themeSettingsDataStore
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<ThemeSettings> = settingsState.asStateFlow()

    fun getSettings(): ThemeSettings = settingsState.value

    fun saveSettings(settings: ThemeSettings): ThemeSettings {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_MODE] = settings.mode.name
            }
        }
        settingsState.value = settings
        return settings
    }

    private fun readSettings(): ThemeSettings = runBlocking {
        val preferences = dataStore.data.first()
        val mode = runCatching {
            ThemeMode.valueOf(preferences[KEY_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        ThemeSettings(mode = mode)
    }

    internal fun clearForTest() {
        runBlocking {
            dataStore.edit { preferences -> preferences.clear() }
        }
        settingsState.value = ThemeSettings()
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("mode")
    }
}
