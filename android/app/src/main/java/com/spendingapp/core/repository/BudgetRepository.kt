package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId

class BudgetRepository(
    private val database: SpendingDatabase,
    private val eventPublisher: DomainEventPublisher,
) {
    fun observeBudgetOverview(month: String = currentMonth()): Flow<List<BudgetOverviewItem>> = combine(
        database.budgetDao().observeByMonth(month),
        database.categoryDao().observeActiveCategories(),
        database.transactionDao().observeTransactions(),
    ) { budgets, categories, transactions ->
        val monthRange = monthRangeMillis(month)
        budgets.map { budget ->
            val category = categories.firstOrNull { it.id == budget.categoryId }
            val spent = transactions
                .filter { it.categoryId == budget.categoryId && it.type.name == "EXPENSE" && it.occurredAt in monthRange.first..monthRange.second }
                .sumOf { it.amount }
            BudgetOverviewItem(
                budget = budget,
                category = category,
                spentAmount = spent,
                percentUsed = if (budget.limitAmount <= 0) 0f else spent.toFloat() / budget.limitAmount.toFloat(),
            )
        }
    }

    fun observeExpenseCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    suspend fun saveBudget(categoryId: Long, month: String, limitAmount: Long, warningThresholdPercent: Int) {
        require(limitAmount > 0) { "Hạn mức phải lớn hơn 0" }
        require(warningThresholdPercent in 1..100) { "Ngưỡng cảnh báo phải từ 1 đến 100" }
        val existing = database.budgetDao().getByCategoryAndMonth(categoryId, month)
        if (existing == null) {
            val budgetId = database.budgetDao().insert(
                BudgetEntity(
                    categoryId = categoryId,
                    month = month,
                    limitAmount = limitAmount,
                    warningThresholdPercent = warningThresholdPercent,
                ),
            )
            eventPublisher.publish(DomainEventType.BUDGET_CREATED, "budget", budgetId)
        } else {
            database.budgetDao().update(
                existing.copy(
                    limitAmount = limitAmount,
                    warningThresholdPercent = warningThresholdPercent,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            eventPublisher.publish(DomainEventType.BUDGET_UPDATED, "budget", existing.id)
        }
    }

    companion object {
        fun currentMonth(): String = YearMonth.now().toString()

        fun monthRangeMillis(month: String): Pair<Long, Long> {
            val yearMonth = YearMonth.parse(month)
            val start = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            return start to end
        }
    }
}

data class BudgetOverviewItem(
    val budget: BudgetEntity,
    val category: CategoryEntity?,
    val spentAmount: Long,
    val percentUsed: Float,
)


