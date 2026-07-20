package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendingapp.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month ORDER BY categoryId")
    fun observeByMonth(month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND month = :month LIMIT 1")
    suspend fun getByCategoryAndMonth(categoryId: Long, month: String): BudgetEntity?

    @Query(
        """
        UPDATE budgets
        SET lastWarningLevel = :level,
            lastWarningSpentAmount = :spentAmount,
            lastWarningAt = :warnedAt,
            updatedAt = :warnedAt
        WHERE id = :budgetId
        """,
    )
    suspend fun markWarningSent(budgetId: Long, level: String, spentAmount: Long, warnedAt: Long)

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)
    @Delete
    suspend fun delete(budget: BudgetEntity)

}



