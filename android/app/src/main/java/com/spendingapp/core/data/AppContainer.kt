package com.spendingapp.core.data

import android.content.Context
import androidx.room.Room
import com.spendingapp.core.database.DatabaseMigrations
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.domain.BudgetChecker
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.notification.CashReminderScheduler
import com.spendingapp.core.notification.LocalNotificationService
import com.spendingapp.core.repository.AccountRepository
import com.spendingapp.core.repository.BackgroundSyncSettingsRepository
import com.spendingapp.core.repository.BudgetRepository
import com.spendingapp.core.repository.CategoryRepository
import com.spendingapp.core.repository.GoalRepository
import com.spendingapp.core.repository.NotificationSettingsRepository
import com.spendingapp.core.repository.ReportingRepository
import com.spendingapp.core.repository.ThemeSettingsRepository
import com.spendingapp.core.repository.TransactionRepository
import com.spendingapp.core.repository.WebhookSettingsRepository
import com.spendingapp.core.security.SecureTokenStorage
import com.spendingapp.core.sync.AutoSyncCoordinator
import com.spendingapp.core.sync.SePayBackgroundSyncScheduler
import com.spendingapp.core.sync.SePayApiClient
import com.spendingapp.core.sync.SePaySyncService
import com.spendingapp.core.sync.TransactionImportPipeline
import com.spendingapp.core.sync.WebhookEndpointTester

class AppContainer(context: Context) {
    val database: SpendingDatabase = Room.databaseBuilder(
        context.applicationContext,
        SpendingDatabase::class.java,
        "spending-app.db",
    ).addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4).build()

    val balanceService = BalanceService(database)
    val budgetChecker = BudgetChecker(database)
    val domainEventPublisher = DomainEventPublisher(database)
    val transactionImportPipeline = TransactionImportPipeline(database, balanceService, budgetChecker, domainEventPublisher)
    val databaseSeeder = DatabaseSeeder(database)
    val transactionRepository = TransactionRepository(database, transactionImportPipeline, domainEventPublisher)
    val accountRepository = AccountRepository(database, balanceService, domainEventPublisher)
    val budgetRepository = BudgetRepository(database, domainEventPublisher)
    val categoryRepository = CategoryRepository(database, domainEventPublisher)
    val goalRepository = GoalRepository(database, domainEventPublisher)
    val reportingRepository = ReportingRepository(database)
    val notificationSettingsRepository = NotificationSettingsRepository(context)
    val backgroundSyncSettingsRepository = BackgroundSyncSettingsRepository(context)
    val themeSettingsRepository = ThemeSettingsRepository(context)
    val localNotificationService = LocalNotificationService(context.applicationContext, notificationSettingsRepository)
    val cashReminderScheduler = CashReminderScheduler(context.applicationContext)
    val secureTokenStorage = SecureTokenStorage(context)
    val sePayApiClient = SePayApiClient()
    val sePaySyncService = SePaySyncService(database, sePayApiClient, transactionImportPipeline, domainEventPublisher)
    val autoSyncCoordinator = AutoSyncCoordinator(context.applicationContext, database, { secureTokenStorage.getSePayToken() }, sePaySyncService)
    val sePayBackgroundSyncScheduler = SePayBackgroundSyncScheduler(context.applicationContext)
    val webhookSettingsRepository = WebhookSettingsRepository(context)
    val webhookEndpointTester = WebhookEndpointTester()
}



















