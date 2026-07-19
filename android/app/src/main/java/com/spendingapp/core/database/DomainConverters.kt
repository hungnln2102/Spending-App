package com.spendingapp.core.database

import androidx.room.TypeConverter
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import com.spendingapp.core.model.SyncStatus
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType

class DomainConverters {
    @TypeConverter fun accountTypeToString(value: AccountType): String = value.name
    @TypeConverter fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter fun transactionStatusToString(value: TransactionStatus): String = value.name
    @TypeConverter fun stringToTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter fun transactionSourceToString(value: TransactionSource): String = value.name
    @TypeConverter fun stringToTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter fun categoryTypeToString(value: CategoryType): String = value.name
    @TypeConverter fun stringToCategoryType(value: String): CategoryType = CategoryType.valueOf(value)

    @TypeConverter fun goalPriorityToString(value: GoalPriority): String = value.name
    @TypeConverter fun stringToGoalPriority(value: String): GoalPriority = GoalPriority.valueOf(value)

    @TypeConverter fun goalStatusToString(value: GoalStatus): String = value.name
    @TypeConverter fun stringToGoalStatus(value: String): GoalStatus = GoalStatus.valueOf(value)

    @TypeConverter fun syncStatusToString(value: SyncStatus): String = value.name
    @TypeConverter fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter fun domainEventTypeToString(value: DomainEventType): String = value.name
    @TypeConverter fun stringToDomainEventType(value: String): DomainEventType = DomainEventType.valueOf(value)
}
