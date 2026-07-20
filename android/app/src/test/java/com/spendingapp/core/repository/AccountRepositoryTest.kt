package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountRepositoryTest {
    private lateinit var database: SpendingDatabase
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AccountRepository(
            database = database,
            balanceService = BalanceService(database),
            eventPublisher = DomainEventPublisher(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateAccountBalanceCreatesAdjustmentTransactionAndBalanceLog() = runBlocking {
        val accountId = database.accountDao().insert(AccountEntity(name = "Tiền mặt", type = AccountType.CASH, balance = 100_000))

        repository.updateAccountBalance(accountId, 120_000, "Đếm lại ví")

        val account = database.accountDao().getById(accountId)!!
        val transaction = database.transactionDao().observeTransactions().first().single()
        val log = database.balanceLogDao().observeByAccount(accountId).first().single()
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(120_000, account.balance)
        assertEquals(TransactionType.ADJUSTMENT, transaction.type)
        assertEquals(TransactionStatus.ADJUSTED, transaction.status)
        assertEquals(20_000, transaction.amount)
        assertEquals("Đếm lại ví", transaction.description)
        assertEquals(transaction.id, log.transactionId)
        assertEquals(20_000, log.changedAmount)
        assertEquals("Đếm lại ví", log.reason)
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_CREATED && it.aggregateId == transaction.id })
        assertTrue(events.any { it.type == DomainEventType.ACCOUNT_BALANCE_CHANGED && it.aggregateId == accountId })
    }

    @Test
    fun updateAccountBalanceRequiresReasonWhenBalanceChanges() = runBlocking {
        val accountId = database.accountDao().insert(AccountEntity(name = "Tiền mặt", type = AccountType.CASH, balance = 100_000))

        try {
            repository.updateAccountBalance(accountId, 90_000, " ")
            fail("Expected blank reason to fail")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Vui lòng nhập lý do cập nhật số dư", expected.message)
        }
    }
}


