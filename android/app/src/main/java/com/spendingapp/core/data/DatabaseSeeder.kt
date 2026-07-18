package com.spendingapp.core.data

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType

class DatabaseSeeder(
    private val database: SpendingDatabase,
) {
    suspend fun seedIfNeeded() {
        if (database.categoryDao().count() > 0) return
        database.categoryDao().insertAll(defaultCategories())
        database.accountDao().insert(
            AccountEntity(
                type = AccountType.CASH,
                name = "Tiền mặt",
                balance = 0L,
            ),
        )
    }

    private fun defaultCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(name = "Ăn uống", icon = "restaurant", color = "#F97316", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Di chuyển", icon = "directions_car", color = "#0EA5E9", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Nhà ở", icon = "home", color = "#8B5CF6", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Hóa đơn", icon = "receipt", color = "#14B8A6", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Mua sắm", icon = "shopping_bag", color = "#EC4899", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Sức khỏe", icon = "health_and_safety", color = "#22C55E", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Giải trí", icon = "movie", color = "#EAB308", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Giáo dục", icon = "school", color = "#6366F1", type = CategoryType.EXPENSE, isDefault = true),
        CategoryEntity(name = "Thu nhập", icon = "payments", color = "#16A34A", type = CategoryType.INCOME, isDefault = true),
        CategoryEntity(name = "Khác", icon = "category", color = "#64748B", type = CategoryType.BOTH, isDefault = true),
    )
}
