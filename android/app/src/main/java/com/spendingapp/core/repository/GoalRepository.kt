package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val database: SpendingDatabase,
) {
    fun observeGoals(): Flow<List<GoalEntity>> = database.goalDao().observeGoals()

    suspend fun createGoal(
        name: String,
        targetAmount: Long,
        priority: GoalPriority,
        targetDate: Long? = null,
    ): Long {
        require(name.isNotBlank()) { "Tên mục tiêu không được trống" }
        require(targetAmount > 0) { "Số tiền mục tiêu phải lớn hơn 0" }
        return database.goalDao().insert(
            GoalEntity(
                name = name.trim(),
                targetAmount = targetAmount,
                priority = priority,
                startDate = System.currentTimeMillis(),
                targetDate = targetDate,
            ),
        )
    }

    suspend fun updateProgress(goal: GoalEntity, newCurrentAmount: Long) {
        require(newCurrentAmount >= 0) { "Tiến độ không được âm" }
        val status = if (newCurrentAmount >= goal.targetAmount) GoalStatus.COMPLETED else GoalStatus.ACTIVE
        database.goalDao().update(
            goal.copy(
                currentAmount = newCurrentAmount.coerceAtMost(goal.targetAmount),
                status = status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun pauseGoal(goal: GoalEntity) {
        database.goalDao().update(goal.copy(status = GoalStatus.PAUSED, updatedAt = System.currentTimeMillis()))
    }

    suspend fun resumeGoal(goal: GoalEntity) {
        database.goalDao().update(goal.copy(status = GoalStatus.ACTIVE, updatedAt = System.currentTimeMillis()))
    }
}
