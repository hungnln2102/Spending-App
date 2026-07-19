package com.spendingapp.core.sync

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.SyncStateEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.SyncStatus
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionType
import java.time.LocalDate

class SePaySyncService(
    private val database: SpendingDatabase,
    private val apiClient: SePayTransactionsClient,
    private val importPipeline: TransactionImportPipeline,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun sync(token: String, daysBack: Long = 30): SePaySyncResult {
        val bankAccount = database.accountDao().getFirstBankAccount()
            ?: throw SePaySyncException("Bạn cần tạo nguồn tiền Ngân hàng trước khi đồng bộ SePay")
        val source = "sepay_api"
        database.syncStateDao().upsert(
            SyncStateEntity(
                source = source,
                accountId = bankAccount.id,
                status = SyncStatus.RUNNING,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        eventPublisher.publish(DomainEventType.SYNC_STARTED, "sync", bankAccount.id, actionId = source)

        return try {
            val transactions = apiClient.fetchTransactions(
                token = token,
                fromDate = LocalDate.now().minusDays(daysBack),
                toDate = LocalDate.now(),
                accountNumber = bankAccount.externalAccountId,
            )
            var imported = 0
            var duplicated = 0
            transactions.forEach { transaction ->
                val result = importPipeline.import(
                    ExternalTransactionInput(
                        accountId = bankAccount.id,
                        type = if (transaction.type == SePayTransactionType.OUT) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.SEPAY_API,
                        amount = transaction.amount,
                        categoryId = null,
                        description = transaction.description,
                        externalTransactionId = transaction.externalId,
                        referenceNumber = transaction.referenceNumber,
                        occurredAt = transaction.transactionTimeMillis,
                    ),
                )
                when (result) {
                    is ImportResult.Imported -> imported++
                    is ImportResult.Duplicate -> duplicated++
                }
            }
            database.syncStateDao().upsert(
                SyncStateEntity(
                    source = source,
                    accountId = bankAccount.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastTransactionDate = transactions.maxOfOrNull { it.transactionTimeMillis },
                    status = SyncStatus.SUCCESS,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            eventPublisher.publish(
                DomainEventType.SYNC_COMPLETED,
                "sync",
                bankAccount.id,
                actionId = source,
                payloadJson = """{"imported":$imported,"duplicated":$duplicated,"totalFetched":${transactions.size}}""",
            )
            SePaySyncResult(imported = imported, duplicated = duplicated, totalFetched = transactions.size)
        } catch (error: Throwable) {
            database.syncStateDao().upsert(
                SyncStateEntity(
                    source = source,
                    accountId = bankAccount.id,
                    status = SyncStatus.FAILED,
                    lastError = error.message,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            eventPublisher.publish(
                DomainEventType.SYNC_FAILED,
                "sync",
                bankAccount.id,
                actionId = source,
                payloadJson = error.message?.take(160),
            )
            throw error
        }
    }
}

data class SePaySyncResult(
    val imported: Int,
    val duplicated: Int,
    val totalFetched: Int,
)
