package com.spendingapp.core.repository

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class ThemeSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    private val settingsState = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<ThemeSettings> = settingsState.asStateFlow()

    fun getSettings(): ThemeSettings = settingsState.value

    fun saveSettings(settings: ThemeSettings): ThemeSettings {
        preferences.edit { putString(KEY_MODE, settings.mode.name) }
        settingsState.value = settings
        return settings
    }

    private fun readSettings(): ThemeSettings {
        val mode = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        return ThemeSettings(mode = mode)
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
