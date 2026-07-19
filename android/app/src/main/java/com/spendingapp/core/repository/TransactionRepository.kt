package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.sync.ExternalTransactionInput
import com.spendingapp.core.sync.ImportResult
import com.spendingapp.core.sync.TransactionImportPipeline
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val database: SpendingDatabase,
    private val importPipeline: TransactionImportPipeline,
    private val eventPublisher: DomainEventPublisher,
) {
    fun observeTransactions(): Flow<List<TransactionEntity>> = database.transactionDao().observeTransactions()

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    suspend fun addManualTransaction(
        accountId: Long,
        categoryId: Long?,
        type: TransactionType,
        amount: Long,
        description: String?,
        occurredAt: Long,
        linkedGoalId: Long? = null,
    ): ImportResult {
        val result = importPipeline.import(
            ExternalTransactionInput(
                accountId = accountId,
                categoryId = categoryId,
                linkedGoalId = linkedGoalId,
                type = type,
                source = TransactionSource.MANUAL,
                amount = amount,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                occurredAt = occurredAt,
            ),
        )
        if (result is ImportResult.Imported && type == TransactionType.INCOME && linkedGoalId != null) {
            val goal = database.goalDao().getById(linkedGoalId)
            if (goal != null && goal.status != com.spendingapp.core.model.GoalStatus.COMPLETED) {
                GoalRepository(database, eventPublisher).updateProgress(goal, goal.currentAmount + amount)
                eventPublisher.publish(DomainEventType.GOAL_LINKED_TO_TRANSACTION, "transaction", result.transactionId)
            }
        }
        return result
    }

    suspend fun unlinkGoal(transaction: TransactionEntity) {
        val linkedGoalId = transaction.linkedGoalId ?: return
        val goal = database.goalDao().getById(linkedGoalId)
        if (goal != null && transaction.type == TransactionType.INCOME) {
            GoalRepository(database, eventPublisher).updateProgress(goal, (goal.currentAmount - transaction.amount).coerceAtLeast(0L))
            eventPublisher.publish(DomainEventType.GOAL_UNLINKED_FROM_TRANSACTION, "transaction", transaction.id)
        }
        database.transactionDao().update(
            transaction.copy(
                linkedGoalId = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        eventPublisher.publish(DomainEventType.TRANSACTION_UPDATED, "transaction", transaction.id)
    }
}
