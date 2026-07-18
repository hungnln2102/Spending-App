package com.spendingapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendingapp.core.database.dao.AccountDao
import com.spendingapp.core.database.dao.BalanceLogDao
import com.spendingapp.core.database.dao.BudgetDao
import com.spendingapp.core.database.dao.CategoryDao
import com.spendingapp.core.database.dao.GoalDao
import com.spendingapp.core.database.dao.SyncStateDao
import com.spendingapp.core.database.dao.TransactionDao
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.BalanceLogEntity
import com.spendingapp.core.database.entity.BudgetEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.database.entity.SyncStateEntity
import com.spendingapp.core.database.entity.TransactionEntity

@TypeConverters(DomainConverters::class)
@Database(
    entities = [
        AccountEntity::class,
        BalanceLogEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        GoalEntity::class,
        SyncStateEntity::class,
        TransactionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SpendingDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun balanceLogDao(): BalanceLogDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun transactionDao(): TransactionDao
}



