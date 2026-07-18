package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long = 0,
    val priority: GoalPriority = GoalPriority.MEDIUM,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val startDate: Long? = null,
    val targetDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
