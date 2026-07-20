package com.spendingapp.core.repository

import androidx.room.withTransaction
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BalanceLogEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.domain.BalanceService
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val database: SpendingDatabase,
    private val balanceService: BalanceService,
    private val eventPublisher: DomainEventPublisher,
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
                eventPublisher.publish(DomainEventType.ACCOUNT_BALANCE_CHANGED, "account", accountId)
            }
            eventPublisher.publish(DomainEventType.ACCOUNT_CREATED, "account", accountId)
            accountId
        }
    }

    suspend fun updateAccountBalance(accountId: Long, newBalance: Long, reason: String) {
        require(newBalance >= 0) { "Số dư mới không được âm" }
        require(reason.isNotBlank()) { "Vui lòng nhập lý do cập nhật số dư" }
        database.withTransaction {
            val account = requireNotNull(database.accountDao().getById(accountId)) { "Không tìm thấy nguồn tiền" }
            val changedAmount = newBalance - account.balance
            if (changedAmount == 0L) return@withTransaction
            val trimmedReason = reason.trim()
            val transactionId = database.transactionDao().insert(
                TransactionEntity(
                    accountId = accountId,
                    type = TransactionType.ADJUSTMENT,
                    status = TransactionStatus.ADJUSTED,
                    source = TransactionSource.MANUAL,
                    amount = kotlin.math.abs(changedAmount),
                    description = trimmedReason,
                    occurredAt = System.currentTimeMillis(),
                ),
            )
            database.accountDao().update(account.copy(balance = newBalance, updatedAt = System.currentTimeMillis()))
            database.balanceLogDao().insert(
                BalanceLogEntity(
                    accountId = accountId,
                    transactionId = transactionId,
                    beforeBalance = account.balance,
                    afterBalance = newBalance,
                    changedAmount = changedAmount,
                    reason = trimmedReason,
                ),
            )
            eventPublisher.publish(DomainEventType.TRANSACTION_CREATED, "transaction", transactionId)
            eventPublisher.publish(DomainEventType.ACCOUNT_BALANCE_CHANGED, "account", accountId)
        }
    }
}
