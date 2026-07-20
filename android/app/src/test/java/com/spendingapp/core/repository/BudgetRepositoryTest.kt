package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.CategoryType
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
class BudgetRepositoryTest {
    private lateinit var database: SpendingDatabase
    private lateinit var repository: BudgetRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BudgetRepository(database, DomainEventPublisher(database))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveBudgetCreatesAndUpdatesUniqueCategoryMonth() = runBlocking {
        val categoryId = insertCategory()
        val month = "2026-07"

        repository.saveBudget(categoryId, month, 1_000_000, 80)
        repository.saveBudget(categoryId, month, 1_500_000, 70)

        val budgets = database.budgetDao().observeByMonth(month).first()
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(1, budgets.size)
        assertEquals(1_500_000, budgets.first().limitAmount)
        assertEquals(70, budgets.first().warningThresholdPercent)
        assertTrue(budgets.first().notificationEnabled)
        assertTrue(events.any { it.type == DomainEventType.BUDGET_CREATED })
        assertTrue(events.any { it.type == DomainEventType.BUDGET_UPDATED })
    }

    @Test
    fun deleteBudgetRemovesBudgetAndPublishesEvent() = runBlocking {
        val categoryId = insertCategory()
        val month = "2026-07"
        repository.saveBudget(categoryId, month, 1_000_000, 80)
        val budgetId = database.budgetDao().observeByMonth(month).first().first().id

        repository.deleteBudget(categoryId, month)

        val budgets = database.budgetDao().observeByMonth(month).first()
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(0, budgets.size)
        assertTrue(events.any { it.type == DomainEventType.BUDGET_DELETED && it.aggregateId == budgetId })
    }

    private suspend fun insertCategory(): Long = database.categoryDao().insert(
        CategoryEntity(
            name = "Ăn uống",
            type = CategoryType.EXPENSE,
        ),
    )
}
