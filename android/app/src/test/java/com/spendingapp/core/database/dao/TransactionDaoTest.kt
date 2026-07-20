package com.spendingapp.core.database.dao

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TransactionDaoTest {
    private lateinit var database: SpendingDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeFilteredFiltersByAccountCategoryTypeStatusAndRange() = runBlocking {
        val accountId = database.accountDao().insert(AccountEntity(name = "Wallet", type = AccountType.CASH, balance = 0))
        val otherAccountId = database.accountDao().insert(AccountEntity(name = "Bank", type = AccountType.BANK, balance = 0))
        val foodId = database.categoryDao().insert(CategoryEntity(name = "Ăn uống", type = CategoryType.EXPENSE))
        val travelId = database.categoryDao().insert(CategoryEntity(name = "Di chuyển", type = CategoryType.EXPENSE))
        val targetId = insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 100_000, 2_000)
        insertTransaction(accountId, travelId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 50_000, 2_100)
        insertTransaction(otherAccountId, foodId, TransactionType.EXPENSE, TransactionStatus.CATEGORIZED, 60_000, 2_200)
        insertTransaction(accountId, foodId, TransactionType.INCOME, TransactionStatus.CATEGORIZED, 70_000, 2_300)
        insertTransaction(accountId, foodId, TransactionType.EXPENSE, TransactionStatus.IGNORED, 80_000, 2_400)

        val filtered = database.transactionDao().observeFiltered(
            accountId = accountId,
            categoryId = foodId,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.CATEGORIZED,
            fromOccurredAt = 1_900,
            toOccurredAt = 2_050,
        ).first()

        assertEquals(listOf(targetId), filtered.map { it.id })
    }

    @Test
    fun observeFilteredExcludesIgnoredUnlessRequested() = runBlocking {
        val accountId = database.accountDao().insert(AccountEntity(name = "Wallet", type = AccountType.CASH, balance = 0))
        val ignoredId = insertTransaction(accountId, null, TransactionType.EXPENSE, TransactionStatus.IGNORED, 10_000, 1_000)

        val defaultFiltered = database.transactionDao().observeFiltered(accountId = accountId).first()
        val includeIgnored = database.transactionDao().observeFiltered(accountId = accountId, includeIgnored = true).first()

        assertEquals(emptyList<Long>(), defaultFiltered.map { it.id })
        assertEquals(listOf(ignoredId), includeIgnored.map { it.id })
    }

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
}
