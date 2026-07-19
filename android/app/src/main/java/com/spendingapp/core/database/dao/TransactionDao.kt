package com.spendingapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE status != 'IGNORED' ORDER BY occurredAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY occurredAt DESC, id DESC")
    fun observeByStatus(status: TransactionStatus): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE externalTransactionId = :externalId AND source = :source LIMIT 1")
    suspend fun findByExternalId(externalId: String, source: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
            AND source = :source
            AND referenceNumber = :referenceNumber
            AND amount = :amount
            AND occurredAt BETWEEN :fromOccurredAt AND :toOccurredAt
        LIMIT 1
        """,
    )
    suspend fun findPotentialDuplicate(
        accountId: Long,
        source: String,
        referenceNumber: String,
        amount: Long,
        fromOccurredAt: Long,
        toOccurredAt: Long,
    ): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND status != 'IGNORED' AND occurredAt BETWEEN :from AND :to")
    fun observeIncomeBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE' AND status != 'IGNORED' AND occurredAt BETWEEN :from AND :to")
    fun observeExpenseBetween(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE categoryId = :categoryId
            AND type = 'EXPENSE'
            AND status != 'IGNORED'
            AND occurredAt BETWEEN :from AND :to
        """,
    )
    suspend fun sumExpenseForCategoryBetween(categoryId: Long, from: Long, to: Long): Long
}


