package com.spendingapp.core.domain

import androidx.room.withTransaction
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BalanceLogEntity

class BalanceService(
    private val database: SpendingDatabase,
) {
    suspend fun increaseBalance(accountId: Long, amount: Long, transactionId: Long?, reason: String) {
        require(amount > 0) { "Amount must be greater than zero" }
        changeBalance(accountId, amount, transactionId, reason)
    }

    suspend fun decreaseBalance(accountId: Long, amount: Long, transactionId: Long?, reason: String) {
        require(amount > 0) { "Amount must be greater than zero" }
        changeBalance(accountId, -amount, transactionId, reason)
    }

    suspend fun adjustBalance(accountId: Long, newBalance: Long, transactionId: Long?, reason: String) {
        require(newBalance >= 0) { "Balance cannot be negative" }
        database.withTransaction {
            val account = requireAccount(accountId)
            val changedAmount = newBalance - account.balance
            database.accountDao().update(account.copy(balance = newBalance, updatedAt = System.currentTimeMillis()))
            database.balanceLogDao().insert(
                BalanceLogEntity(
                    accountId = accountId,
                    transactionId = transactionId,
                    beforeBalance = account.balance,
                    afterBalance = newBalance,
                    changedAmount = changedAmount,
                    reason = reason,
                ),
            )
        }
    }

    private suspend fun changeBalance(accountId: Long, delta: Long, transactionId: Long?, reason: String) {
        database.withTransaction {
            val account = requireAccount(accountId)
            val newBalance = account.balance + delta
            require(newBalance >= 0) { "Balance cannot become negative" }
            database.accountDao().update(account.copy(balance = newBalance, updatedAt = System.currentTimeMillis()))
            database.balanceLogDao().insert(
                BalanceLogEntity(
                    accountId = accountId,
                    transactionId = transactionId,
                    beforeBalance = account.balance,
                    afterBalance = newBalance,
                    changedAmount = delta,
                    reason = reason,
                ),
            )
        }
    }

    private suspend fun requireAccount(accountId: Long): AccountEntity =
        requireNotNull(database.accountDao().getById(accountId)) { "Account not found: $accountId" }
}
