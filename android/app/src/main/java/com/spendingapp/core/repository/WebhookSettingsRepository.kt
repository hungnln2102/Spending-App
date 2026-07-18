package com.spendingapp.core.repository

import android.content.Context

class WebhookSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("webhook_settings", Context.MODE_PRIVATE)

    fun saveWebhookUrl(url: String) {
        preferences.edit().putString(KEY_WEBHOOK_URL, url.trim()).apply()
    }

    fun getWebhookUrl(): String = preferences.getString(KEY_WEBHOOK_URL, DEFAULT_WEBHOOK_URL).orEmpty()

    fun clearWebhookUrl() {
        preferences.edit().remove(KEY_WEBHOOK_URL).apply()
    }

    private companion object {
        const val KEY_WEBHOOK_URL = "webhook_url"
        const val DEFAULT_WEBHOOK_URL = "https://admin.mavrykpremium.com/bot/payment_sepay/"
    }
}
