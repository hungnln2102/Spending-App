package com.spendingapp.core.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.domain.BudgetChecker
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.SyncStatus
import com.spendingapp.core.model.TransactionSource
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SePaySyncServiceTest {
    private lateinit var database: SpendingDatabase
    private lateinit var fakeClient: FakeSePayClient
    private lateinit var service: SePaySyncService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeClient = FakeSePayClient()
        val eventPublisher = DomainEventPublisher(database)
        service = SePaySyncService(
            database = database,
            apiClient = fakeClient,
            importPipeline = TransactionImportPipeline(
                database = database,
                balanceService = BalanceService(database),
                budgetChecker = BudgetChecker(database),
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
    fun missingBankAccountFailsBeforeCallingApi() = runBlocking {
        try {
            service.sync("token")
            fail("Expected sync to require a bank account")
        } catch (error: SePaySyncException) {
            assertTrue(error.message.orEmpty().contains("Ngân hàng"))
        }
        assertEquals(0, fakeClient.callCount)
    }

    @Test
    fun emptyApiResponseUpdatesSyncStateSuccess() = runBlocking {
        insertBankAccount(balance = 100_000)
        fakeClient.transactions = emptyList()

        val result = service.sync("token")

        val state = database.syncStateDao().observeAll().first().first()
        assertEquals(0, result.imported)
        assertEquals(0, result.duplicated)
        assertEquals(SyncStatus.SUCCESS, state.status)
    }

    @Test
    fun syncImportsIncomeAndExpenseAndUpdatesBalance() = runBlocking {
        val accountId = insertBankAccount(balance = 100_000)
        fakeClient.transactions = listOf(
            sePayTransaction("in-1", 50_000, SePayTransactionType.IN),
            sePayTransaction("out-1", 30_000, SePayTransactionType.OUT),
        )

        val result = service.sync("token")

        val account = database.accountDao().getById(accountId)!!
        val transactions = database.transactionDao().observeTransactions().first()
        val income = transactions.first { it.externalTransactionId == "in-1" }
        val expense = transactions.first { it.externalTransactionId == "out-1" }
        val events = database.domainEventDao().observeAll().first()
        assertEquals(2, result.imported)
        assertEquals(0, result.duplicated)
        assertEquals(120_000, account.balance)
        assertEquals(2, transactions.size)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals(TransactionType.EXPENSE, expense.type)
        assertEquals(TransactionSource.SEPAY_API, income.source)
        assertEquals(TransactionSource.SEPAY_API, expense.source)
        assertEquals(TransactionStatus.CATEGORIZED, income.status)
        assertEquals(TransactionStatus.PENDING_CATEGORY, expense.status)
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_EXPENSE_DETECTED && it.aggregateId == expense.id })
    }

    @Test
    fun syncAgainCountsDuplicatesAndDoesNotChangeBalanceTwice() = runBlocking {
        val accountId = insertBankAccount(balance = 100_000)
        fakeClient.transactions = listOf(sePayTransaction("dup-1", 50_000, SePayTransactionType.IN))

        val first = service.sync("token")
        val second = service.sync("token")

        val account = database.accountDao().getById(accountId)!!
        assertEquals(1, first.imported)
        assertEquals(1, second.duplicated)
        assertEquals(150_000, account.balance)
    }

    @Test
    fun apiFailureStoresFailedStateAndEvent() = runBlocking {
        insertBankAccount(balance = 100_000)
        fakeClient.error = SePaySyncException("invalid token")

        try {
            service.sync("bad-token")
            fail("Expected sync failure")
        } catch (error: SePaySyncException) {
            assertEquals("invalid token", error.message)
        }

        val state = database.syncStateDao().observeAll().first().first()
        val events = database.domainEventDao().observeAll().first()
        assertEquals(SyncStatus.FAILED, state.status)
        assertEquals("invalid token", state.lastError)
        assertTrue(events.any { it.type == DomainEventType.SYNC_FAILED })
    }

    private suspend fun insertBankAccount(balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            type = AccountType.BANK,
            name = "Bank",
            balance = balance,
            externalAccountId = "123456789",
        ),
    )

    private fun sePayTransaction(
        id: String,
        amount: Long,
        type: SePayTransactionType,
    ): SePayTransactionDto = SePayTransactionDto(
        externalId = id,
        referenceNumber = "ref-$id",
        accountNumber = "123456789",
        amount = amount,
        type = type,
        description = "transaction $id",
        transactionTimeMillis = 1_700_000_000_000,
    )

    private class FakeSePayClient : SePayTransactionsClient {
        var transactions: List<SePayTransactionDto> = emptyList()
        var error: Throwable? = null
        var callCount: Int = 0

        override fun fetchTransactions(
            token: String,
            fromDate: LocalDate?,
            toDate: LocalDate?,
            accountNumber: String?,
        ): List<SePayTransactionDto> {
            callCount++
            error?.let { throw it }
            return transactions
        }
    }
}
