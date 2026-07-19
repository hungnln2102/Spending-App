package com.spendingapp.core.domain

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetCheckerTest {
    private lateinit var database: SpendingDatabase
    private lateinit var checker: BudgetChecker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checker = BudgetChecker(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun underThresholdDoesNotTriggerWarning() = runBlocking {
        val categoryId = insertCategory()
        insertBudget(categoryId, limitAmount = 100_000, warningThresholdPercent = 80)
        val transaction = insertExpense(categoryId, amount = 50_000)

        val result = checker.checkAfterTransaction(transaction)

        assertTrue(result is BudgetCheckResult.UnderThreshold)
        assertEquals(50_000, (result as BudgetCheckResult.UnderThreshold).spentAmount)
    }

    @Test
    fun thresholdTriggersWarning() = runBlocking {
        val categoryId = insertCategory()
        insertBudget(categoryId, limitAmount = 100_000, warningThresholdPercent = 80)
        val transaction = insertExpense(categoryId, amount = 80_000)

        val result = checker.checkAfterTransaction(transaction)

        assertTrue(result is BudgetCheckResult.WarningTriggered)
        assertEquals(80_000, (result as BudgetCheckResult.WarningTriggered).spentAmount)
        val budget = database.budgetDao().getByCategoryAndMonth(categoryId, currentMonth())!!
        assertEquals(BudgetWarningLevel.WARNING.name, budget.lastWarningLevel)
    }

    @Test
    fun limitTriggersExceeded() = runBlocking {
        val categoryId = insertCategory()
        insertBudget(categoryId, limitAmount = 100_000, warningThresholdPercent = 80)
        val transaction = insertExpense(categoryId, amount = 120_000)

        val result = checker.checkAfterTransaction(transaction)

        assertTrue(result is BudgetCheckResult.Exceeded)
        assertEquals(120_000, (result as BudgetCheckResult.Exceeded).spentAmount)
    }

    @Test
    fun repeatedSameLevelDoesNotSpam() = runBlocking {
        val categoryId = insertCategory()
        insertBudget(categoryId, limitAmount = 100_000, warningThresholdPercent = 80)
        val transaction = insertExpense(categoryId, amount = 80_000)

        val first = checker.checkAfterTransaction(transaction)
        val second = checker.checkAfterTransaction(transaction)

        assertTrue(first is BudgetCheckResult.WarningTriggered)
        assertTrue(second is BudgetCheckResult.AlreadySent)
    }

    @Test
    fun ignoredOrIncomeTransactionsDoNotCount() = runBlocking {
        val categoryId = insertCategory()
        insertBudget(categoryId, limitAmount = 100_000, warningThresholdPercent = 80)
        insertExpense(categoryId, amount = 120_000, status = TransactionStatus.IGNORED)
        val income = insertTransaction(categoryId, TransactionType.INCOME, amount = 120_000)

        val result = checker.checkAfterTransaction(income)

        assertTrue(result is BudgetCheckResult.NoBudget)
        val spent = database.transactionDao().sumExpenseForCategoryBetween(categoryId, monthStart(), monthEnd())
        assertEquals(0, spent)
    }

    private suspend fun insertCategory(): Long = database.categoryDao().insert(
        CategoryEntity(
            name = "Food",
            type = CategoryType.EXPENSE,
            icon = "food",
            color = "#000000",
        ),
    )

    private suspend fun insertBudget(categoryId: Long, limitAmount: Long, warningThresholdPercent: Int): Long = database.budgetDao().insert(
        BudgetEntity(
            categoryId = categoryId,
            month = currentMonth(),
            limitAmount = limitAmount,
            warningThresholdPercent = warningThresholdPercent,
        ),
    )

    private suspend fun insertExpense(
        categoryId: Long,
        amount: Long,
        status: TransactionStatus = TransactionStatus.CATEGORIZED,
    ): TransactionEntity = insertTransaction(categoryId, TransactionType.EXPENSE, amount, status)

    private suspend fun insertTransaction(
        categoryId: Long,
        type: TransactionType,
        amount: Long,
        status: TransactionStatus = TransactionStatus.CATEGORIZED,
    ): TransactionEntity {
        val accountId = database.accountDao().insert(
            AccountEntity(
                type = AccountType.CASH,
                name = "Cash",
                balance = 1_000_000,
            ),
        )
        val transaction = TransactionEntity(
            accountId = accountId,
            categoryId = categoryId,
            type = type,
            status = status,
            source = TransactionSource.MANUAL,
            amount = amount,
            occurredAt = monthStart() + 1_000,
        )
        val id = database.transactionDao().insert(transaction)
        return transaction.copy(id = id)
    }

    private fun currentMonth(): String = LocalDate.now().withDayOfMonth(1).toString().substring(0, 7)

    private fun monthStart(): Long = LocalDate.now()
        .withDayOfMonth(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun monthEnd(): Long = LocalDate.now()
        .withDayOfMonth(1)
        .plusMonths(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli() - 1
}
