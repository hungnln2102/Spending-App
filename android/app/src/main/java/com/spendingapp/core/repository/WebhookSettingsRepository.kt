package com.spendingapp.core.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.webhookSettingsDataStore by preferencesDataStore(name = "webhook_settings")

class WebhookSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.webhookSettingsDataStore

    fun saveWebhookUrl(url: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_WEBHOOK_URL] = url.trim()
            }
        }
    }

    fun getWebhookUrl(): String = runBlocking {
        dataStore.data.first()[KEY_WEBHOOK_URL] ?: DEFAULT_WEBHOOK_URL
    }

    fun clearWebhookUrl() {
        runBlocking {
            dataStore.edit { preferences ->
                preferences.remove(KEY_WEBHOOK_URL)
            }
        }
    }

    private companion object {
        val KEY_WEBHOOK_URL = stringPreferencesKey("webhook_url")
        const val DEFAULT_WEBHOOK_URL = "https://admin.mavrykpremium.com/bot/payment_sepay/"
    }
}
