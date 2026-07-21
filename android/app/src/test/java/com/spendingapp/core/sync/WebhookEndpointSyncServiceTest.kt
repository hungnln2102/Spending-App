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
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.SyncStatus
import com.spendingapp.core.model.TransactionSource
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
class WebhookEndpointSyncServiceTest {
    private lateinit var database: SpendingDatabase
    private lateinit var fakeClient: FakeWebhookEndpointClient
    private lateinit var service: WebhookEndpointSyncService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeClient = FakeWebhookEndpointClient()
        val eventPublisher = DomainEventPublisher(database)
        service = WebhookEndpointSyncService(
            database = database,
            endpointClient = fakeClient,
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
    fun blankEndpointFailsBeforeCallingClient() = runBlocking {
        insertBankAccount(balance = 100_000)

        try {
            service.sync("   ")
            fail("Expected endpoint URL validation")
        } catch (error: SePaySyncException) {
            assertTrue(error.message.orEmpty().contains("webhook endpoint URL"))
        }
        assertEquals(0, fakeClient.callCount)
    }

    @Test
    fun syncImportsWebhookEndpointTransactionsThroughPipeline() = runBlocking {
        val accountId = insertBankAccount(balance = 100_000)
        fakeClient.transactions = listOf(
            transaction("webhook-in-1", 70_000, SePayTransactionType.IN),
            transaction("webhook-out-1", 25_000, SePayTransactionType.OUT),
        )

        val result = service.sync("https://example.com/hooks/transactions", "secret")

        val account = database.accountDao().getById(accountId)!!
        val transactions = database.transactionDao().observeTransactions().first()
        val syncState = database.syncStateDao().observeAll().first().first()
        assertEquals(2, result.imported)
        assertEquals(0, result.duplicated)
        assertEquals(145_000, account.balance)
        assertEquals(TransactionSource.WEBHOOK_ENDPOINT, transactions.first().source)
        assertEquals(SyncStatus.SUCCESS, syncState.status)
        assertEquals(WebhookEndpointSyncService.SOURCE, syncState.source)
        assertEquals("https://example.com/hooks/transactions", fakeClient.endpointUrl)
        assertEquals("secret", fakeClient.apiKey)
    }

    @Test
    fun syncAgainCountsDuplicatesAndDoesNotChangeBalanceTwice() = runBlocking {
        val accountId = insertBankAccount(balance = 100_000)
        fakeClient.transactions = listOf(transaction("dup-webhook", 50_000, SePayTransactionType.IN))

        val first = service.sync("https://example.com/hooks/transactions")
        val second = service.sync("https://example.com/hooks/transactions")

        val account = database.accountDao().getById(accountId)!!
        assertEquals(1, first.imported)
        assertEquals(1, second.duplicated)
        assertEquals(150_000, account.balance)
    }

    @Test
    fun clientFailureStoresFailedSyncState() = runBlocking {
        insertBankAccount(balance = 100_000)
        fakeClient.error = SePaySyncException("unauthorized")

        try {
            service.sync("https://example.com/hooks/transactions")
            fail("Expected sync failure")
        } catch (error: SePaySyncException) {
            assertEquals("unauthorized", error.message)
        }

        val syncState = database.syncStateDao().observeAll().first().first()
        assertEquals(SyncStatus.FAILED, syncState.status)
        assertEquals("unauthorized", syncState.lastError)
    }

    private suspend fun insertBankAccount(balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            type = AccountType.BANK,
            name = "Bank",
            balance = balance,
            externalAccountId = "123456789",
        ),
    )

    private fun transaction(id: String, amount: Long, type: SePayTransactionType): SePayTransactionDto = SePayTransactionDto(
        externalId = id,
        referenceNumber = "ref-$id",
        accountNumber = "123456789",
        amount = amount,
        type = type,
        description = "transaction $id",
        transactionTimeMillis = 1_700_000_000_000,
    )

    private class FakeWebhookEndpointClient : WebhookEndpointClient {
        var transactions: List<SePayTransactionDto> = emptyList()
        var error: Throwable? = null
        var callCount: Int = 0
        var endpointUrl: String? = null
        var apiKey: String? = null

        override fun fetchTransactions(endpointUrl: String, apiKey: String?): List<SePayTransactionDto> {
            callCount++
            this.endpointUrl = endpointUrl
            this.apiKey = apiKey
            error?.let { throw it }
            return transactions
        }
    }
}
