package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spendingapp.core.database.entity.BalanceLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceLogDao {
    @Insert
    suspend fun insert(log: BalanceLogEntity): Long

    @Query("SELECT * FROM balance_logs WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeByAccount(accountId: Long): Flow<List<BalanceLogEntity>>
}
