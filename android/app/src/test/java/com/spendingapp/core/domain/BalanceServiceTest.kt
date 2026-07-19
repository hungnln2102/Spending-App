package com.spendingapp.core.domain

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BalanceServiceTest {
    private lateinit var database: SpendingDatabase
    private lateinit var service: BalanceService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = BalanceService(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun incomeIncreasesBalanceAndCreatesLog() = runBlocking {
        val accountId = insertAccount(balance = 100_000)

        service.increaseBalance(accountId, 50_000, transactionId = 10, reason = "income_test")

        val account = database.accountDao().getById(accountId)!!
        val logs = database.balanceLogDao().observeByAccount(accountId).first()
        assertEquals(150_000, account.balance)
        assertEquals(1, logs.size)
        assertEquals(100_000, logs.first().beforeBalance)
        assertEquals(150_000, logs.first().afterBalance)
        assertEquals(50_000, logs.first().changedAmount)
    }

    @Test
    fun expenseDecreasesBalanceAndCreatesLog() = runBlocking {
        val accountId = insertAccount(balance = 100_000)

        service.decreaseBalance(accountId, 40_000, transactionId = 11, reason = "expense_test")

        val account = database.accountDao().getById(accountId)!!
        val logs = database.balanceLogDao().observeByAccount(accountId).first()
        assertEquals(60_000, account.balance)
        assertEquals(1, logs.size)
        assertEquals(-40_000, logs.first().changedAmount)
    }

    @Test
    fun adjustmentSetsBalanceAndCreatesLog() = runBlocking {
        val accountId = insertAccount(balance = 100_000)

        service.adjustBalance(accountId, 125_000, transactionId = null, reason = "cash_count")

        val account = database.accountDao().getById(accountId)!!
        val logs = database.balanceLogDao().observeByAccount(accountId).first()
        assertEquals(125_000, account.balance)
        assertEquals(25_000, logs.first().changedAmount)
        assertEquals("cash_count", logs.first().reason)
    }

    @Test
    fun rollbackKeepsBalanceWhenExpenseWouldGoNegative() = runBlocking {
        val accountId = insertAccount(balance = 30_000)

        try {
            service.decreaseBalance(accountId, 40_000, transactionId = 12, reason = "too_much")
            fail("Expected negative balance update to fail")
        } catch (error: IllegalArgumentException) {
            assertEquals("Balance cannot become negative", error.message)
        }

        val account = database.accountDao().getById(accountId)!!
        val logs = database.balanceLogDao().observeByAccount(accountId).first()
        assertEquals(30_000, account.balance)
        assertEquals(0, logs.size)
    }

    private suspend fun insertAccount(balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            type = AccountType.CASH,
            name = "Test cash",
            balance = balance,
        ),
    )
}
