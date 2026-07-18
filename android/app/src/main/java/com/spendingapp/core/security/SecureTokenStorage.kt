package com.spendingapp.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "secure_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveSePayToken(token: String) {
        preferences.edit().putString(KEY_SEPAY_TOKEN, token.trim()).apply()
    }

    fun getSePayToken(): String? = preferences.getString(KEY_SEPAY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clearSePayToken() {
        preferences.edit().remove(KEY_SEPAY_TOKEN).apply()
    }

    fun hasSePayToken(): Boolean = getSePayToken() != null

    fun saveWebhookApiKey(apiKey: String) {
        preferences.edit().putString(KEY_WEBHOOK_API_KEY, apiKey.trim()).apply()
    }

    fun getWebhookApiKey(): String? = preferences.getString(KEY_WEBHOOK_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun clearWebhookApiKey() {
        preferences.edit().remove(KEY_WEBHOOK_API_KEY).apply()
    }

    fun hasWebhookApiKey(): Boolean = getWebhookApiKey() != null

    private companion object {
        const val KEY_SEPAY_TOKEN = "sepay_token"
        const val KEY_WEBHOOK_API_KEY = "webhook_api_key"
    }
}
