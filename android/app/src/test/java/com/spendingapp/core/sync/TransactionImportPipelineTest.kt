package com.spendingapp.core.sync

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
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
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
class TransactionImportPipelineTest {
    private lateinit var database: SpendingDatabase
    private lateinit var pipeline: TransactionImportPipeline

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pipeline = TransactionImportPipeline(
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
    fun newIncomeImportsAndIncreasesBalance() = runBlocking {
        val accountId = insertAccount(balance = 100_000)

        val result = pipeline.import(sampleInput(accountId, amount = 25_000, externalId = "ext-1"))

        val account = database.accountDao().getById(accountId)!!
        val transactions = database.transactionDao().observeTransactions().first()
        assertTrue(result is ImportResult.Imported)
        assertEquals(125_000, account.balance)
        assertEquals(1, transactions.size)
        assertEquals(TransactionStatus.CATEGORIZED, transactions.first().status)
    }

    @Test
    fun duplicateExternalIdDoesNotChangeBalanceTwice() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val input = sampleInput(accountId, amount = 25_000, externalId = "ext-duplicate")

        val first = pipeline.import(input)
        val second = pipeline.import(input)

        val account = database.accountDao().getById(accountId)!!
        val transactions = database.transactionDao().observeTransactions().first()
        assertTrue(first is ImportResult.Imported)
        assertTrue(second is ImportResult.Duplicate)
        assertEquals(125_000, account.balance)
        assertEquals(1, transactions.size)
    }

    @Test
    fun duplicateReferenceAmountDateAccountDoesNotChangeBalanceTwice() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val firstInput = sampleInput(accountId, amount = 25_000, externalId = null, reference = "REF-1", occurredAt = 1_000_000)
        val secondInput = sampleInput(accountId, amount = 25_000, externalId = null, reference = "REF-1", occurredAt = 1_000_000 + 60_000)

        val first = pipeline.import(firstInput)
        val second = pipeline.import(secondInput)

        val account = database.accountDao().getById(accountId)!!
        val transactions = database.transactionDao().observeTransactions().first()
        assertTrue(first is ImportResult.Imported)
        assertTrue(second is ImportResult.Duplicate)
        assertEquals(125_000, account.balance)
        assertEquals(1, transactions.size)
    }

    @Test
    fun expenseWithoutCategoryBecomesPendingCategory() = runBlocking {
        val accountId = insertAccount(balance = 100_000)

        val result = pipeline.import(
            sampleInput(
                accountId = accountId,
                type = TransactionType.EXPENSE,
                amount = 40_000,
                externalId = "expense-1",
            ),
        )

        val account = database.accountDao().getById(accountId)!!
        val transaction = database.transactionDao().observeTransactions().first().first()
        assertTrue(result is ImportResult.Imported)
        assertEquals(60_000, account.balance)
        assertEquals(TransactionStatus.PENDING_CATEGORY, transaction.status)
    }

    @Test
    fun duplicateImportPublishesDuplicateEvent() = runBlocking {
        val accountId = insertAccount(balance = 100_000)
        val input = sampleInput(accountId, externalId = "event-duplicate")

        pipeline.import(input)
        pipeline.import(input)

        val events = database.domainEventDao().observeAll().first()
        assertTrue(events.any { it.type == DomainEventType.TRANSACTION_DUPLICATED })
    }

    private suspend fun insertAccount(balance: Long): Long = database.accountDao().insert(
        AccountEntity(
            type = AccountType.BANK,
            name = "Test bank",
            balance = balance,
        ),
    )

    private fun sampleInput(
        accountId: Long,
        type: TransactionType = TransactionType.INCOME,
        amount: Long = 10_000,
        externalId: String? = "external-id",
        reference: String? = "reference-number",
        occurredAt: Long = 1_700_000_000_000,
    ): ExternalTransactionInput = ExternalTransactionInput(
        accountId = accountId,
        type = type,
        source = TransactionSource.SEPAY_API,
        amount = amount,
        externalTransactionId = externalId,
        referenceNumber = reference,
        occurredAt = occurredAt,
    )
}
