package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.GoalStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

class ReportingRepository(
    private val database: SpendingDatabase,
) {
    fun observeDashboardSummary(): Flow<DashboardSummary> {
        val month = BudgetRepository.currentMonth()
        val previousMonth = YearMonth.parse(month).minusMonths(1).toString()
        return combine(
            database.accountDao().observeTotalBalance(),
            database.transactionDao().observeTransactions(),
            database.goalDao().observeGoals(),
            database.categoryDao().observeActiveCategories(),
            database.budgetDao().observeByMonth(month),
        ) { totalBalance: Long,
            transactions: List<TransactionEntity>,
            goals: List<GoalEntity>,
            categories: List<CategoryEntity>,
            budgets: List<BudgetEntity> ->
            val monthRange = BudgetRepository.monthRangeMillis(month)
            val previousMonthRange = BudgetRepository.monthRangeMillis(previousMonth)
            val monthlyTransactions = transactions.filter { it.occurredAt in monthRange.first..monthRange.second }
            val previousMonthTransactions = transactions.filter { it.occurredAt in previousMonthRange.first..previousMonthRange.second }
            val income = monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val previousIncome = previousMonthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenseTransactions = monthlyTransactions.filter { it.type == TransactionType.EXPENSE }
            val previousExpenseTransactions = previousMonthTransactions.filter { it.type == TransactionType.EXPENSE }
            val expense = expenseTransactions.sumOf { it.amount }
            val previousExpense = previousExpenseTransactions.sumOf { it.amount }
            val categorySpending = buildCategorySpending(expenseTransactions, categories)
            val categoryMovements = buildCategoryMovements(expenseTransactions, previousExpenseTransactions, categories)
            val budgetStatuses = buildBudgetStatuses(budgets, categories, expenseTransactions)
            val monthlyBars = buildMonthlyBars(transactions, month)
            val balanceTrend = buildBalanceTrend(totalBalance, monthlyBars)

            DashboardSummary(
                totalBalance = totalBalance,
                transactionCount = transactions.size,
                incomeAmount = income,
                expenseAmount = expense,
                monthComparison = MonthComparisonSummary(
                    currentIncomeAmount = income,
                    previousIncomeAmount = previousIncome,
                    currentExpenseAmount = expense,
                    previousExpenseAmount = previousExpense,
                    incomeDeltaAmount = income - previousIncome,
                    expenseDeltaAmount = expense - previousExpense,
                    biggestIncreaseCategory = categoryMovements.filter { it.deltaAmount > 0 }.maxByOrNull { it.deltaAmount },
                    biggestDecreaseCategory = categoryMovements.filter { it.deltaAmount < 0 }.minByOrNull { it.deltaAmount },
                ),
                categorySpending = categorySpending,
                budgetStatuses = budgetStatuses,
                monthlyBars = monthlyBars,
                balanceTrend = balanceTrend,
                pendingCategoryCount = transactions.count { it.categoryId == null },
                pendingCategoryTransactions = transactions.filter { it.categoryId == null },
                featuredGoal = goals.firstOrNull { it.status == GoalStatus.ACTIVE },
            )
        }
    }

    private fun buildCategorySpending(
        expenseTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<CategorySpendingSummary> = expenseTransactions
        .filter { it.categoryId != null }
        .groupBy { it.categoryId!! }
        .map { (categoryId, categoryTransactions) ->
            CategorySpendingSummary(
                categoryId = categoryId,
                categoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "Chưa rõ",
                amount = categoryTransactions.sumOf { it.amount },
            )
        }
        .sortedByDescending { it.amount }
        .take(3)

    private fun buildCategoryMovements(
        currentExpenseTransactions: List<TransactionEntity>,
        previousExpenseTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<CategoryMovementSummary> {
        val currentByCategory = currentExpenseTransactions.sumByCategoryId()
        val previousByCategory = previousExpenseTransactions.sumByCategoryId()
        return (currentByCategory.keys + previousByCategory.keys).distinct().map { categoryId ->
            val currentAmount = currentByCategory[categoryId] ?: 0L
            val previousAmount = previousByCategory[categoryId] ?: 0L
            CategoryMovementSummary(
                categoryId = categoryId,
                categoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "Hạng mục #$categoryId",
                currentAmount = currentAmount,
                previousAmount = previousAmount,
                deltaAmount = currentAmount - previousAmount,
            )
        }
    }

    private fun buildBudgetStatuses(
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        expenseTransactions: List<TransactionEntity>,
    ): List<BudgetStatusSummary> = budgets.map { budget ->
        val spentAmount = expenseTransactions.filter { it.categoryId == budget.categoryId }.sumOf { it.amount }
        val percentUsed = if (budget.limitAmount <= 0) 0f else spentAmount.toFloat() / budget.limitAmount.toFloat()
        BudgetStatusSummary(
            budgetId = budget.id,
            categoryName = categories.firstOrNull { it.id == budget.categoryId }?.name ?: "Hạng mục #${budget.categoryId}",
            spentAmount = spentAmount,
            limitAmount = budget.limitAmount,
            percentUsed = percentUsed,
            status = when {
                percentUsed >= 1f -> BudgetStatus.EXCEEDED
                percentUsed * 100 >= budget.warningThresholdPercent -> BudgetStatus.WARNING
                else -> BudgetStatus.OK
            },
        )
    }.sortedByDescending { it.percentUsed }


    private fun buildMonthlyBars(transactions: List<TransactionEntity>, currentMonth: String): List<MonthlyBarSummary> {
        val current = YearMonth.parse(currentMonth)
        return (2 downTo 0).map { offset ->
            val month = current.minusMonths(offset.toLong())
            val range = BudgetRepository.monthRangeMillis(month.toString())
            val monthTransactions = transactions.filter { it.occurredAt in range.first..range.second }
            MonthlyBarSummary(
                monthLabel = "T${month.monthValue}",
                incomeAmount = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expenseAmount = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            )
        }
    }

    private fun buildBalanceTrend(totalBalance: Long, monthlyBars: List<MonthlyBarSummary>): List<BalanceTrendPoint> {
        var runningBalance = totalBalance - monthlyBars.sumOf { it.netAmount }
        return monthlyBars.map { item ->
            runningBalance += item.netAmount
            BalanceTrendPoint(
                label = item.monthLabel,
                balanceAmount = runningBalance,
            )
        }
    }
    private fun List<TransactionEntity>.sumByCategoryId(): Map<Long, Long> = filter { it.categoryId != null }
        .groupBy { it.categoryId!! }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
}

data class DashboardSummary(
    val totalBalance: Long,
    val transactionCount: Int,
    val incomeAmount: Long,
    val expenseAmount: Long,
    val monthComparison: MonthComparisonSummary,
    val categorySpending: List<CategorySpendingSummary>,
    val budgetStatuses: List<BudgetStatusSummary>,
    val monthlyBars: List<MonthlyBarSummary>,
    val balanceTrend: List<BalanceTrendPoint>,
    val pendingCategoryCount: Int,
    val pendingCategoryTransactions: List<TransactionEntity>,
    val featuredGoal: GoalEntity?,
)

data class MonthComparisonSummary(
    val currentIncomeAmount: Long,
    val previousIncomeAmount: Long,
    val currentExpenseAmount: Long,
    val previousExpenseAmount: Long,
    val incomeDeltaAmount: Long,
    val expenseDeltaAmount: Long,
    val biggestIncreaseCategory: CategoryMovementSummary?,
    val biggestDecreaseCategory: CategoryMovementSummary?,
)

data class CategoryMovementSummary(
    val categoryId: Long,
    val categoryName: String,
    val currentAmount: Long,
    val previousAmount: Long,
    val deltaAmount: Long,
)

data class CategorySpendingSummary(
    val categoryId: Long,
    val categoryName: String,
    val amount: Long,
)

data class MonthlyBarSummary(
    val monthLabel: String,
    val incomeAmount: Long,
    val expenseAmount: Long,
) {
    val netAmount: Long get() = incomeAmount - expenseAmount
}

data class BalanceTrendPoint(
    val label: String,
    val balanceAmount: Long,
)
data class BudgetStatusSummary(
    val budgetId: Long,
    val categoryName: String,
    val spentAmount: Long,
    val limitAmount: Long,
    val percentUsed: Float,
    val status: BudgetStatus,
)

enum class BudgetStatus { OK, WARNING, EXCEEDED }



