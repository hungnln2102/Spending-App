package com.spendingapp.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.spendingapp.core.database.SpendingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoSyncCoordinator(
    private val context: Context,
    private val database: SpendingDatabase,
    private val tokenProvider: () -> String?,
    private val sePaySyncService: SePaySyncService,
    private val minimumIntervalMillis: Long = DEFAULT_MINIMUM_INTERVAL_MILLIS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val networkChecker: (Context) -> Boolean = { it.hasNetworkConnection() },
) {
    fun syncOnAppOpen(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        scope.launch {
            runCatching { syncIfEligible() }
        }
    }

    suspend fun syncIfEligible(): AutoSyncDecision {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AutoSyncDecision.SKIPPED_NO_TOKEN
        if (!networkChecker(context)) return AutoSyncDecision.SKIPPED_NO_NETWORK
        val bankAccount = database.accountDao().getFirstBankAccount()
            ?: return AutoSyncDecision.SKIPPED_NO_BANK_ACCOUNT
        val state = database.syncStateDao().getBySourceAndAccount(SEP_PAY_SOURCE, bankAccount.id)
        val lastSyncedAt = state?.lastSyncedAt
        if (lastSyncedAt != null && nowProvider() - lastSyncedAt < minimumIntervalMillis) {
            return AutoSyncDecision.SKIPPED_TOO_SOON
        }
        sePaySyncService.sync(token)
        return AutoSyncDecision.STARTED
    }

    companion object {
        const val SEP_PAY_SOURCE = "sepay_api"
        const val DEFAULT_MINIMUM_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}

private fun Context.hasNetworkConnection(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

enum class AutoSyncDecision {
    STARTED,
    SKIPPED_NO_TOKEN,
    SKIPPED_NO_NETWORK,
    SKIPPED_NO_BANK_ACCOUNT,
    SKIPPED_TOO_SOON,
}
