package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId", "month"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val month: String,
    val limitAmount: Long,
    val warningThresholdPercent: Int,
    val notificationEnabled: Boolean = true,
    val lastWarningLevel: String? = null,
    val lastWarningSpentAmount: Long? = null,
    val lastWarningAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)


