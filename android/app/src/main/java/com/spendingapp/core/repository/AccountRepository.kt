package com.spendingapp.core.repository

import androidx.room.withTransaction
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BalanceLogEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.model.AccountType
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val database: SpendingDatabase,
    private val balanceService: BalanceService,
) {
    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    suspend fun createAccount(
        name: String,
        type: AccountType,
        initialBalance: Long,
        provider: String? = null,
    ): Long {
        require(name.isNotBlank()) { "Tên nguồn tiền không được trống" }
        require(initialBalance >= 0) { "Số dư ban đầu không được âm" }
        return database.withTransaction {
            val accountId = database.accountDao().insert(
                AccountEntity(
                    type = type,
                    name = name.trim(),
                    balance = initialBalance,
                    provider = provider?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
            if (initialBalance > 0) {
                database.balanceLogDao().insert(
                    BalanceLogEntity(
                        accountId = accountId,
                        beforeBalance = 0,
                        afterBalance = initialBalance,
                        changedAmount = initialBalance,
                        reason = "initial_balance",
                    ),
                )
            }
            accountId
        }
    }

    suspend fun updateAccountBalance(accountId: Long, newBalance: Long, reason: String) {
        require(newBalance >= 0) { "Số dư mới không được âm" }
        require(reason.isNotBlank()) { "Vui lòng nhập lý do cập nhật số dư" }
        balanceService.adjustBalance(
            accountId = accountId,
            newBalance = newBalance,
            transactionId = null,
            reason = reason.trim(),
        )
    }
}
