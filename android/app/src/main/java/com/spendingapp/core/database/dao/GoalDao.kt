package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendingapp.core.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query(
        """
        SELECT * FROM goals
        ORDER BY
            CASE status
                WHEN 'ACTIVE' THEN 0
                WHEN 'PAUSED' THEN 1
                WHEN 'COMPLETED' THEN 2
                ELSE 3
            END,
            CASE priority
                WHEN 'HIGH' THEN 0
                WHEN 'MEDIUM' THEN 1
                ELSE 2
            END,
            targetDate IS NULL,
            targetDate,
            updatedAt DESC
        """,
    )
    fun observeGoals(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)
}
