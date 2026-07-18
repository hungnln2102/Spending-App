package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.sync.ExternalTransactionInput
import com.spendingapp.core.sync.ImportResult
import com.spendingapp.core.sync.TransactionImportPipeline
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val database: SpendingDatabase,
    private val importPipeline: TransactionImportPipeline,
) {
    fun observeTransactions(): Flow<List<TransactionEntity>> = database.transactionDao().observeTransactions()

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    suspend fun addManualTransaction(
        accountId: Long,
        categoryId: Long?,
        type: TransactionType,
        amount: Long,
        description: String?,
        occurredAt: Long,
    ): ImportResult = importPipeline.import(
        ExternalTransactionInput(
            accountId = accountId,
            categoryId = categoryId,
            type = type,
            source = TransactionSource.MANUAL,
            amount = amount,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            occurredAt = occurredAt,
        ),
    )
}
