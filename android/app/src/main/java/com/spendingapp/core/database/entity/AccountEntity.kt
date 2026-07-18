package com.spendingapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendingapp.core.model.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AccountType,
    val name: String,
    val balance: Long,
    val currency: String = "VND",
    val provider: String? = null,
    val externalAccountId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
