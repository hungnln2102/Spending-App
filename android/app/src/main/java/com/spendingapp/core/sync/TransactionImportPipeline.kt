package com.spendingapp.core.sync

import androidx.room.withTransaction
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.domain.BudgetChecker
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType

class TransactionImportPipeline(
    private val database: SpendingDatabase,
    private val balanceService: BalanceService,
    private val budgetChecker: BudgetChecker? = null,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun import(input: ExternalTransactionInput): ImportResult {
        require(input.amount > 0) { "Amount must be greater than zero" }
        input.externalTransactionId?.let { externalId ->
            val duplicate = database.transactionDao().findByExternalId(externalId, input.source.name)
            if (duplicate != null) {
                eventPublisher.publish(DomainEventType.TRANSACTION_DUPLICATED, "transaction", duplicate.id, actionId = externalId)
                return ImportResult.Duplicate(duplicate.id)
            }
        }
        input.referenceNumber?.takeIf { it.isNotBlank() }?.let { referenceNumber ->
            val duplicate = database.transactionDao().findPotentialDuplicate(
                accountId = input.accountId,
                source = input.source.name,
                referenceNumber = referenceNumber,
                amount = input.amount,
                fromOccurredAt = input.occurredAt - DUPLICATE_TIME_WINDOW_MILLIS,
                toOccurredAt = input.occurredAt + DUPLICATE_TIME_WINDOW_MILLIS,
            )
            if (duplicate != null) {
                eventPublisher.publish(DomainEventType.TRANSACTION_DUPLICATED, "transaction", duplicate.id, actionId = referenceNumber)
                return ImportResult.Duplicate(duplicate.id)
            }
        }

        return database.withTransaction {
            val status = if (input.type == TransactionType.EXPENSE && input.categoryId == null) {
                TransactionStatus.PENDING_CATEGORY
            } else {
                TransactionStatus.CATEGORIZED
            }
            val transaction = TransactionEntity(
                accountId = input.accountId,
                categoryId = input.categoryId,
                linkedGoalId = input.linkedGoalId,
                type = input.type,
                status = status,
                source = input.source,
                amount = input.amount,
                description = input.description,
                externalTransactionId = input.externalTransactionId,
                referenceNumber = input.referenceNumber,
                occurredAt = input.occurredAt,
            )
            val transactionId = database.transactionDao().insert(transaction)
            when (input.type) {
                TransactionType.INCOME -> balanceService.increaseBalance(input.accountId, input.amount, transactionId, "transaction_import")
                TransactionType.EXPENSE -> balanceService.decreaseBalance(input.accountId, input.amount, transactionId, "transaction_import")
                TransactionType.ADJUSTMENT,
                TransactionType.TRANSFER -> Unit
            }
            val importedTransaction = transaction.copy(id = transactionId)
            eventPublisher.publish(
                if (input.source == TransactionSource.MANUAL) DomainEventType.TRANSACTION_CREATED else DomainEventType.TRANSACTION_IMPORTED,
                "transaction",
                transactionId,
                actionId = input.externalTransactionId ?: input.referenceNumber,
            )
            val budgetCheckResult = budgetChecker?.checkAfterTransaction(importedTransaction) ?: BudgetCheckResult.NoBudget
            when (budgetCheckResult) {
                is BudgetCheckResult.WarningTriggered -> eventPublisher.publish(DomainEventType.BUDGET_WARNING_TRIGGERED, "budget", budgetCheckResult.budget.id)
                is BudgetCheckResult.Exceeded -> eventPublisher.publish(DomainEventType.BUDGET_EXCEEDED, "budget", budgetCheckResult.budget.id)
                else -> Unit
            }
            ImportResult.Imported(transactionId, budgetCheckResult)
        }
    }

    private companion object {
        const val DUPLICATE_TIME_WINDOW_MILLIS = 5 * 60 * 1000L
    }
}

data class ExternalTransactionInput(
    val accountId: Long,
    val type: TransactionType,
    val source: TransactionSource,
    val amount: Long,
    val categoryId: Long? = null,
    val linkedGoalId: Long? = null,
    val description: String? = null,
    val externalTransactionId: String? = null,
    val referenceNumber: String? = null,
    val occurredAt: Long,
)

sealed interface ImportResult {
    data class Imported(
        val transactionId: Long,
        val budgetCheckResult: BudgetCheckResult = BudgetCheckResult.NoBudget,
    ) : ImportResult

    data class Duplicate(val existingTransactionId: Long) : ImportResult
}
