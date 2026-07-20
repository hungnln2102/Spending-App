package com.spendingapp.core.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.SyncStateEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoSyncCoordinatorTest {
    private lateinit var context: Context
    private lateinit var database: SpendingDatabase
    private var token: String? = null
    private lateinit var fakeClient: FakeSePayClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        token = null
        fakeClient = FakeSePayClient()
    }

    @After
    fun tearDown() {
        token = null
        database.close()
    }

    @Test
    fun skipsWhenTokenMissing() = runBlocking {
        val coordinator = coordinator(networkAvailable = true)

        val decision = coordinator.syncIfEligible()

        assertEquals(AutoSyncDecision.SKIPPED_NO_TOKEN, decision)
        assertEquals(0, fakeClient.callCount)
    }

    @Test
    fun skipsWhenNetworkUnavailable() = runBlocking {
        token = "token"
        val coordinator = coordinator(networkAvailable = false)

        val decision = coordinator.syncIfEligible()

        assertEquals(AutoSyncDecision.SKIPPED_NO_NETWORK, decision)
        assertEquals(0, fakeClient.callCount)
    }

    @Test
    fun skipsWhenLastSyncIsTooRecent() = runBlocking {
        token = "token"
        val accountId = insertBankAccount()
        database.syncStateDao().upsert(
            SyncStateEntity(
                source = AutoSyncCoordinator.SEP_PAY_SOURCE,
                accountId = accountId,
                lastSyncedAt = 10_000L,
                status = SyncStatus.SUCCESS,
            ),
        )
        val coordinator = coordinator(networkAvailable = true, now = 11_000L, minimumInterval = 5_000L)

        val decision = coordinator.syncIfEligible()

        assertEquals(AutoSyncDecision.SKIPPED_TOO_SOON, decision)
        assertEquals(0, fakeClient.callCount)
    }

    @Test
    fun startsSyncWhenEligible() = runBlocking {
        token = "token"
        insertBankAccount()
        val coordinator = coordinator(networkAvailable = true)

        val decision = coordinator.syncIfEligible()

        assertEquals(AutoSyncDecision.STARTED, decision)
        assertEquals(1, fakeClient.callCount)
    }

    private fun coordinator(networkAvailable: Boolean, now: Long = 20_000L, minimumInterval: Long = 5_000L): AutoSyncCoordinator {
        val eventPublisher = DomainEventPublisher(database)
        return AutoSyncCoordinator(
            context = context,
            database = database,
            tokenProvider = { token },
            sePaySyncService = SePaySyncService(
                database = database,
                apiClient = fakeClient,
                importPipeline = TransactionImportPipeline(database, BalanceService(database), eventPublisher = eventPublisher),
                eventPublisher = eventPublisher,
            ),
            minimumIntervalMillis = minimumInterval,
            nowProvider = { now },
            networkChecker = { networkAvailable },
        )
    }

    private suspend fun insertBankAccount(): Long = database.accountDao().insert(
        AccountEntity(
            name = "Bank",
            type = AccountType.BANK,
            balance = 100_000,
        ),
    )

    private class FakeSePayClient : SePayTransactionsClient {
        var callCount = 0

        override fun fetchTransactions(
            token: String,
            fromDate: LocalDate?,
            toDate: LocalDate?,
            accountNumber: String?,
        ): List<SePayTransactionDto> {
            callCount++
            return emptyList()
        }
    }
}
