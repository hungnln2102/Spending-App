package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReportingRepositoryTest {
    private lateinit var database: SpendingDatabase
    private lateinit var repository: ReportingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReportingRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dashboardSummaryUsesLocalDatabaseAndIgnoresIgnoredTransactions() = runBlocking {
        val accountId = insertAccount(balance = 700_000)
        val foodId = insertCategory("Ăn uống")
        val travelId = insertCategory("Di chuyển")
        val month = BudgetRepository.currentMonth()
        database.budgetDao().insert(BudgetEntity(categoryId = foodId, month = month, limitAmount = 100_000, warningThresholdPercent = 80))
        database.goalDao().insert(GoalEntity(name = "Quỹ cute", targetAmount = 1_000_000, currentAmount = 250_000, priority = GoalPriority.HIGH, status = GoalStatus.ACTIVE))
        insertTransaction(accountId, null, TransactionType.INCOME, TransactionStatus.CATEGORIZED, 500_000, monthStart(month))
        insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 80_000, monthStart(month))
        insertTransaction(accountId, travelId, TransactionType.EXPENSE, TransactionStatus.IGNORED, 999_000, monthStart(month))
        insertTransaction(accountId, null, TransactionType.EXPENSE, TransactionStatus.PENDING_CATEGORY, 20_000, monthStart(month))

        val summary = repository.observeDashboardSummary().first()

        assertEquals(700_000L, summary.totalBalance)
        assertEquals(500_000L, summary.incomeAmount)
        assertEquals(100_000L, summary.expenseAmount)
        assertEquals(3, summary.transactionCount)
        assertEquals(1, summary.pendingCategoryCount)
        assertEquals(1, summary.categorySpending.size)
        assertEquals("Ăn uống", summary.categorySpending.first().categoryName)
        assertEquals(80_000L, summary.categorySpending.first().amount)
        assertEquals(BudgetStatus.WARNING, summary.budgetStatuses.first().status)
        assertNotNull(summary.featuredGoal)
        assertTrue(summary.monthlyBars.isNotEmpty())
        assertTrue(summary.balanceTrend.isNotEmpty())
    }

    @Test
    fun monthComparisonHandlesMissingPreviousMonthWithoutDivision() = runBlocking {
        val accountId = insertAccount(balance = 300_000)
        val foodId = insertCategory("Ăn uống")
        val month = BudgetRepository.currentMonth()
        insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 120_000, monthStart(month))
        insertTransaction(accountId, null, TransactionType.INCOME, TransactionStatus.CATEGORIZED, 300_000, monthStart(month))

        val comparison = repository.observeDashboardSummary().first().monthComparison

        assertEquals(300_000L, comparison.currentIncomeAmount)
        assertEquals(0L, comparison.previousIncomeAmount)
        assertEquals(120_000L, comparison.currentExpenseAmount)
        assertEquals(0L, comparison.previousExpenseAmount)
        assertEquals(300_000L, comparison.incomeDeltaAmount)
        assertEquals(120_000L, comparison.expenseDeltaAmount)
        assertEquals("Ăn uống", comparison.biggestIncreaseCategory?.categoryName)
    }

    @Test
    fun monthComparisonFindsBiggestIncreaseAndDecreaseCategories() = runBlocking {
        val accountId = insertAccount(balance = 300_000)
        val foodId = insertCategory("Ăn uống")
        val travelId = insertCategory("Di chuyển")
        val currentMonth = BudgetRepository.currentMonth()
        val previousMonth = YearMonth.parse(currentMonth).minusMonths(1).toString()
        insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 200_000, monthStart(currentMonth))
        insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 50_000, monthStart(previousMonth))
        insertTransaction(accountId, travelId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 20_000, monthStart(currentMonth))
        insertTransaction(accountId, travelId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 90_000, monthStart(previousMonth))

        val comparison = repository.observeDashboardSummary().first().monthComparison

        assertEquals("Ăn uống", comparison.biggestIncreaseCategory?.categoryName)
        assertEquals(150_000L, comparison.biggestIncreaseCategory?.deltaAmount)
        assertEquals("Di chuyển", comparison.biggestDecreaseCategory?.categoryName)
        assertEquals(-70_000L, comparison.biggestDecreaseCategory?.deltaAmount)
    }

    private suspend fun insertAccount(balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            type = AccountType.BANK,
            name = "Bank",
            balance = balance,
        ),
    )

    private suspend fun insertCategory(name: String): Long = database.categoryDao().insert(
        CategoryEntity(
            name = name,
            type = CategoryType.EXPENSE,
        ),
    )

    private suspend fun insertTransaction(
        accountId: Long,
        categoryId: Long?,
        type: TransactionType,
        status: TransactionStatus,
        amount: Long,
        occurredAt: Long,
    ): Long = database.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            categoryId = categoryId,
            type = type,
            status = status,
            source = TransactionSource.MANUAL,
            amount = amount,
            occurredAt = occurredAt,
        ),
    )

    private fun monthStart(month: String): Long = BudgetRepository.monthRangeMillis(month).first
}

