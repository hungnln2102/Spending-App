package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spendingapp.core.database.entity.DomainEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainEventDao {
    @Query("SELECT * FROM domain_events ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DomainEventEntity>>

    @Query("SELECT * FROM domain_events WHERE dispatchedAt IS NULL ORDER BY createdAt ASC")
    suspend fun pendingDispatch(): List<DomainEventEntity>

    @Insert
    suspend fun insert(event: DomainEventEntity): Long

    @Query("UPDATE domain_events SET dispatchedAt = :dispatchedAt WHERE id = :eventId")
    suspend fun markDispatched(eventId: Long, dispatchedAt: Long = System.currentTimeMillis())

    @Query("UPDATE domain_events SET dispatchAttempts = dispatchAttempts + 1, lastDispatchError = :error WHERE id = :eventId")
    suspend fun markDispatchFailed(eventId: Long, error: String)
}
