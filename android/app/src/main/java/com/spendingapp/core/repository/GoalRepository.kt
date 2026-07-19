package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val database: SpendingDatabase,
    private val eventPublisher: DomainEventPublisher,
) {
    fun observeGoals(): Flow<List<GoalEntity>> = database.goalDao().observeGoals()

    suspend fun createGoal(
        name: String,
        targetAmount: Long,
        priority: GoalPriority,
        targetDate: Long? = null,
    ): Long {
        require(name.isNotBlank()) { "Ten muc tieu khong duoc trong" }
        require(targetAmount > 0) { "So tien muc tieu phai lon hon 0" }
        val goalId = database.goalDao().insert(
            GoalEntity(
                name = name.trim(),
                targetAmount = targetAmount,
                priority = priority,
                startDate = System.currentTimeMillis(),
                targetDate = targetDate,
            ),
        )
        eventPublisher.publish(DomainEventType.GOAL_CREATED, "goal", goalId)
        return goalId
    }

    suspend fun updateProgress(goal: GoalEntity, newCurrentAmount: Long): GoalProgressResult {
        require(newCurrentAmount >= 0) { "Tien do khong duoc am" }
        val status = if (newCurrentAmount >= goal.targetAmount) GoalStatus.COMPLETED else GoalStatus.ACTIVE
        val completedJustNow = goal.status != GoalStatus.COMPLETED && status == GoalStatus.COMPLETED
        database.goalDao().update(
            goal.copy(
                currentAmount = newCurrentAmount.coerceAtMost(goal.targetAmount),
                status = status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        eventPublisher.publish(DomainEventType.GOAL_PROGRESS_UPDATED, "goal", goal.id)
        if (completedJustNow) {
            eventPublisher.publish(DomainEventType.GOAL_COMPLETED, "goal", goal.id)
        }
        return GoalProgressResult(completedJustNow = completedJustNow)
    }

    suspend fun pauseGoal(goal: GoalEntity) {
        database.goalDao().update(goal.copy(status = GoalStatus.PAUSED, updatedAt = System.currentTimeMillis()))
        eventPublisher.publish(DomainEventType.GOAL_PAUSED, "goal", goal.id)
    }

    suspend fun resumeGoal(goal: GoalEntity) {
        database.goalDao().update(goal.copy(status = GoalStatus.ACTIVE, updatedAt = System.currentTimeMillis()))
        eventPublisher.publish(DomainEventType.GOAL_RESUMED, "goal", goal.id)
    }
}

data class GoalProgressResult(
    val completedJustNow: Boolean,
)
