package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.sync.ImportResult
import com.spendingapp.core.sync.TransactionImportPipeline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TransactionRepositoryTest {
    private lateinit var database: SpendingDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val eventPublisher = DomainEventPublisher(database)
        repository = TransactionRepository(
            database = database,
            importPipeline = TransactionImportPipeline(
                database = database,
                balanceService = BalanceService(database),
                eventPublisher = eventPublisher,
            ),
            eventPublisher = eventPublisher,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateExpenseAmountCorrectsBalanceAndCreatesAuditEvent() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val result = repository.addManualTransaction(accountId, null, TransactionType.EXPENSE, 20_000, "coffee", 1_700_000_000_000)
        val transaction = database.transactionDao().getById((result as ImportResult.Imported).transactionId)!!

        repository.updateTransaction(transaction, amount = 35_000, description = "coffee and cake")

        val account = database.accountDao().getById(accountId)!!
        val updated = database.transactionDao().getById(transaction.id)!!
        val logs = database.balanceLogDao().observeByAccount(accountId).first()
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(65_000, account.balance)
        assertEquals(35_000, updated.amount)
        assertEquals("coffee and cake", updated.description)
        assertTrue(logs.any { it.reason == "transaction_update" && it.changedAmount == -15_000L })
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_UPDATED && it.aggregateId == transaction.id })
    }

    @Test
    fun updateExpenseAccountMovesBalanceImpactBetweenAccounts() = runBlocking {
        val cashId = insertAccount(name = "Cash", balance = 100_000)
        val bankId = insertAccount(name = "Bank", balance = 50_000)
        val result = repository.addManualTransaction(cashId, null, TransactionType.EXPENSE, 20_000, null, 1_700_000_000_000)
        val transaction = database.transactionDao().getById((result as ImportResult.Imported).transactionId)!!

        repository.updateTransaction(transaction, accountId = bankId)

        assertEquals(100_000, database.accountDao().getById(cashId)!!.balance)
        assertEquals(30_000, database.accountDao().getById(bankId)!!.balance)
    }



    @Test
    fun updateCategoryAndDateRefreshesDynamicBudgetAndReport() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val foodId = insertCategory("Ăn uống")
        val shoppingId = insertCategory("Mua sắm")
        database.budgetDao().insert(BudgetEntity(categoryId = foodId, month = BudgetRepository.currentMonth(), limitAmount = 100_000, warningThresholdPercent = 80))
        database.budgetDao().insert(BudgetEntity(categoryId = shoppingId, month = BudgetRepository.currentMonth(), limitAmount = 100_000, warningThresholdPercent = 80))
        val currentMonthStart = BudgetRepository.monthRangeMillis(BudgetRepository.currentMonth()).first
        val previousMonthStart = BudgetRepository.monthRangeMillis(java.time.YearMonth.parse(BudgetRepository.currentMonth()).minusMonths(1).toString()).first
        val result = repository.addManualTransaction(accountId, foodId, TransactionType.EXPENSE, 30_000, null, currentMonthStart)
        val transaction = database.transactionDao().getById((result as ImportResult.Imported).transactionId)!!
        val reportingRepository = ReportingRepository(database)

        repository.updateTransaction(transaction, categoryId = shoppingId, occurredAt = previousMonthStart)

        val summary = reportingRepository.observeDashboardSummary().first()
        assertEquals(0, summary.expenseAmount)
        assertTrue(summary.budgetStatuses.all { it.spentAmount == 0L })
        assertTrue(summary.categorySpending.none { it.categoryName == "Ăn uống" || it.categoryName == "Mua sắm" })
    }

    @Test
    fun categorizingPendingExpensePublishesEventAndRunsBudgetChecker() = runBlocking {
        val accountId = insertAccount(balance = 200_000)
        val foodId = insertCategory("Ăn uống")
        val month = BudgetRepository.currentMonth()
        val budgetId = database.budgetDao().insert(BudgetEntity(categoryId = foodId, month = month, limitAmount = 100_000, warningThresholdPercent = 80))
        val result = repository.addManualTransaction(accountId, null, TransactionType.EXPENSE, 90_000, null, BudgetRepository.monthRangeMillis(month).first)
        val transaction = database.transactionDao().getById((result as ImportResult.Imported).transactionId)!!

        repository.updateTransaction(transaction, categoryId = foodId)

        val categorized = database.transactionDao().getById(transaction.id)!!
        val budget = database.budgetDao().getByCategoryAndMonth(foodId, month)!!
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(TransactionStatus.CATEGORIZED, categorized.status)
        assertEquals(budgetId, budget.id)
        assertEquals("WARNING", budget.lastWarningLevel)
        assertEquals(90_000L, budget.lastWarningSpentAmount)
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_CATEGORIZED && it.aggregateId == transaction.id })
    }

    @Test
    fun ignoreExpenseRestoresBalanceAndHidesTransactionFromActiveList() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val result = repository.addManualTransaction(accountId, null, TransactionType.EXPENSE, 20_000, null, 1_700_000_000_000)
        val transaction = database.transactionDao().getById((result as ImportResult.Imported).transactionId)!!

        repository.ignoreTransaction(transaction)

        val account = database.accountDao().getById(accountId)!!
        val visibleTransactions = database.transactionDao().observeTransactions().first()
        val ignored = database.transactionDao().getById(transaction.id)!!
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(100_000, account.balance)
        assertEquals(0, visibleTransactions.size)
        assertEquals(com.spendingapp.core.model.TransactionStatus.IGNORED, ignored.status)
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_IGNORED && it.aggregateId == transaction.id })
    }


    private suspend fun insertCategory(name: String): Long = database.categoryDao().insert(
        CategoryEntity(
            name = name,
            type = CategoryType.EXPENSE,
        ),
    )

    private suspend fun insertAccount(name: String = "Wallet", balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            name = name,
            type = AccountType.CASH,
            balance = balance,
        ),
    )
}
