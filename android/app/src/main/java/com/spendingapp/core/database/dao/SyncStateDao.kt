package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendingapp.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_states ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_states WHERE source = :source AND accountId = :accountId LIMIT 1")
    suspend fun getBySourceAndAccount(source: String, accountId: Long?): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(syncState: SyncStateEntity): Long
}

