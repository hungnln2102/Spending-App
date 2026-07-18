package com.spendingapp.core.data

import android.content.Context
import androidx.room.Room
import com.spendingapp.core.database.DatabaseMigrations
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.domain.BudgetChecker
import com.spendingapp.core.repository.AccountRepository
import com.spendingapp.core.repository.BudgetRepository
import com.spendingapp.core.repository.GoalRepository
import com.spendingapp.core.repository.TransactionRepository
import com.spendingapp.core.repository.WebhookSettingsRepository
import com.spendingapp.core.security.SecureTokenStorage
import com.spendingapp.core.sync.SePayApiClient
import com.spendingapp.core.sync.SePaySyncService
import com.spendingapp.core.sync.TransactionImportPipeline
import com.spendingapp.core.sync.WebhookEndpointTester

class AppContainer(context: Context) {
    val database: SpendingDatabase = Room.databaseBuilder(
        context.applicationContext,
        SpendingDatabase::class.java,
        "spending-app.db",
    ).addMigrations(DatabaseMigrations.MIGRATION_1_2).build()

    val balanceService = BalanceService(database)
    val budgetChecker = BudgetChecker(database)
    val transactionImportPipeline = TransactionImportPipeline(database, balanceService, budgetChecker)
    val databaseSeeder = DatabaseSeeder(database)
    val transactionRepository = TransactionRepository(database, transactionImportPipeline)
    val accountRepository = AccountRepository(database, balanceService)
    val budgetRepository = BudgetRepository(database)
    val goalRepository = GoalRepository(database)
    val secureTokenStorage = SecureTokenStorage(context)
    val sePayApiClient = SePayApiClient()
    val sePaySyncService = SePaySyncService(database, sePayApiClient, transactionImportPipeline)
    val webhookSettingsRepository = WebhookSettingsRepository(context)
    val webhookEndpointTester = WebhookEndpointTester()
}











