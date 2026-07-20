package com.spendingapp.core.repository

import androidx.room.withTransaction
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BalanceLogEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.domain.BudgetChecker
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
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

    suspend fun updateTransaction(
        transaction: TransactionEntity,
        accountId: Long = transaction.accountId,
        categoryId: Long? = transaction.categoryId,
        amount: Long = transaction.amount,
        description: String? = transaction.description,
        occurredAt: Long = transaction.occurredAt,
    ): BudgetCheckResult {
        require(amount > 0) { "Amount must be greater than zero" }
        return database.withTransaction {
            val current = requireNotNull(database.transactionDao().getById(transaction.id)) { "Transaction not found: ${transaction.id}" }
            val updated = current.copy(
                accountId = accountId,
                categoryId = categoryId,
                amount = amount,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                occurredAt = occurredAt,
                status = if (current.type == TransactionType.EXPENSE && categoryId == null) TransactionStatus.PENDING_CATEGORY else TransactionStatus.CATEGORIZED,
                updatedAt = System.currentTimeMillis(),
            )
            applyBalanceCorrection(current, updated)
            database.transactionDao().update(updated)
            val wasCategorized = current.status == TransactionStatus.PENDING_CATEGORY && updated.status == TransactionStatus.CATEGORIZED
            eventPublisher.publish(if (wasCategorized) DomainEventType.TRANSACTION_CATEGORIZED else DomainEventType.TRANSACTION_UPDATED, "transaction", current.id)
            if (wasCategorized) BudgetChecker(database).checkAfterTransaction(updated) else BudgetCheckResult.NoBudget
        }
    }

    suspend fun ignoreTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            val current = requireNotNull(database.transactionDao().getById(transaction.id)) { "Transaction not found: ${transaction.id}" }
            if (current.status == TransactionStatus.IGNORED) return@withTransaction
            val ignored = current.copy(
                status = TransactionStatus.IGNORED,
                updatedAt = System.currentTimeMillis(),
            )
            applyBalanceCorrection(current, ignored)
            database.transactionDao().update(ignored)
            eventPublisher.publish(DomainEventType.TRANSACTION_IGNORED, "transaction", current.id)
        }
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
    private suspend fun applyBalanceCorrection(current: TransactionEntity, updated: TransactionEntity) {
        val oldDelta = current.balanceDelta()
        val newDelta = updated.balanceDelta()
        if (current.accountId == updated.accountId) {
            val correction = newDelta - oldDelta
            if (correction != 0L) changeAccountBalance(updated.accountId, correction, updated.id)
            return
        }
        if (oldDelta != 0L) changeAccountBalance(current.accountId, -oldDelta, current.id)
        if (newDelta != 0L) changeAccountBalance(updated.accountId, newDelta, updated.id)
    }

    private suspend fun changeAccountBalance(accountId: Long, delta: Long, transactionId: Long) {
        val account = requireNotNull(database.accountDao().getById(accountId)) { "Account not found: $accountId" }
        val newBalance = account.balance + delta
        require(newBalance >= 0) { "Balance cannot become negative" }
        database.accountDao().update(account.copy(balance = newBalance, updatedAt = System.currentTimeMillis()))
        database.balanceLogDao().insert(
            BalanceLogEntity(
                accountId = accountId,
                transactionId = transactionId,
                beforeBalance = account.balance,
                afterBalance = newBalance,
                changedAmount = delta,
                reason = "transaction_update",
            ),
        )
    }

    private fun TransactionEntity.balanceDelta(): Long = when {
        status == TransactionStatus.IGNORED -> 0L
        type == TransactionType.INCOME -> amount
        type == TransactionType.EXPENSE -> -amount
        else -> 0L
    }
}
