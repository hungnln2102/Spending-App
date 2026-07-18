package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendingapp.core.model.SyncStatus

@Entity(
    tableName = "sync_states",
    indices = [Index(value = ["source", "accountId"], unique = true)],
)
data class SyncStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val accountId: Long? = null,
    val lastSyncedAt: Long? = null,
    val lastCursor: String? = null,
    val lastTransactionId: String? = null,
    val lastTransactionDate: Long? = null,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
