package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "balance_logs",
    indices = [Index(value = ["accountId"]), Index(value = ["transactionId"])],
)
data class BalanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val transactionId: Long? = null,
    val beforeBalance: Long,
    val afterBalance: Long,
    val changedAmount: Long,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)
