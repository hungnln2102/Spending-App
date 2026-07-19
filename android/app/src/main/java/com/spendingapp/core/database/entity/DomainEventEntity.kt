package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendingapp.core.event.DomainEventType

@Entity(
    tableName = "domain_events",
    indices = [
        Index(value = ["type"]),
        Index(value = ["feature"]),
        Index(value = ["aggregateType", "aggregateId"]),
        Index(value = ["dispatchedAt"]),
    ],
)
data class DomainEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DomainEventType,
    val feature: String,
    val aggregateType: String,
    val aggregateId: Long? = null,
    val actionId: String? = null,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dispatchedAt: Long? = null,
    val dispatchAttempts: Int = 0,
    val lastDispatchError: String? = null,
)
