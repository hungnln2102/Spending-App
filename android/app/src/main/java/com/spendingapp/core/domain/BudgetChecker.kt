package com.spendingapp.core.domain

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.repository.BudgetRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class BudgetChecker(
    private val database: SpendingDatabase,
) {
    suspend fun checkAfterTransaction(transaction: TransactionEntity): BudgetCheckResult {
        val categoryId = transaction.categoryId ?: return BudgetCheckResult.NoBudget
        if (transaction.type != TransactionType.EXPENSE) return BudgetCheckResult.NoBudget

        val month = YearMonth.from(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault())).toString()
        val budget = database.budgetDao().getByCategoryAndMonth(categoryId, month) ?: return BudgetCheckResult.NoBudget
        if (!budget.notificationEnabled) return BudgetCheckResult.NotificationDisabled(budget.id)

        val monthRange = BudgetRepository.monthRangeMillis(month)
        val spentAmount = database.transactionDao().sumExpenseForCategoryBetween(categoryId, monthRange.first, monthRange.second)
        val level = when {
            spentAmount >= budget.limitAmount -> BudgetWarningLevel.EXCEEDED
            spentAmount * 100 >= budget.limitAmount * budget.warningThresholdPercent -> BudgetWarningLevel.WARNING
            else -> return BudgetCheckResult.UnderThreshold(budget.id, spentAmount)
        }

        if (budget.lastWarningLevel == level.name && (budget.lastWarningSpentAmount ?: 0L) >= spentAmount) {
            return BudgetCheckResult.AlreadySent(budget.id, level, spentAmount)
        }

        database.budgetDao().markWarningSent(
            budgetId = budget.id,
            level = level.name,
            spentAmount = spentAmount,
            warnedAt = System.currentTimeMillis(),
        )

        return when (level) {
            BudgetWarningLevel.WARNING -> BudgetCheckResult.WarningTriggered(budget, spentAmount)
            BudgetWarningLevel.EXCEEDED -> BudgetCheckResult.Exceeded(budget, spentAmount)
        }
    }
}

enum class BudgetWarningLevel { WARNING, EXCEEDED }

sealed interface BudgetCheckResult {
    data object NoBudget : BudgetCheckResult
    data class NotificationDisabled(val budgetId: Long) : BudgetCheckResult
    data class UnderThreshold(val budgetId: Long, val spentAmount: Long) : BudgetCheckResult
    data class AlreadySent(val budgetId: Long, val level: BudgetWarningLevel, val spentAmount: Long) : BudgetCheckResult
    data class WarningTriggered(val budget: BudgetEntity, val spentAmount: Long) : BudgetCheckResult
    data class Exceeded(val budget: BudgetEntity, val spentAmount: Long) : BudgetCheckResult
}
