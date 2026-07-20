package com.spendingapp.core.repository

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.CategoryType
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val database: SpendingDatabase,
    private val eventPublisher: DomainEventPublisher,
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    suspend fun createCategory(name: String, type: CategoryType, icon: String?, color: String?): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Tên hạng mục không được trống" }
        val categoryId = database.categoryDao().insert(
            CategoryEntity(
                name = normalizedName,
                type = type,
                icon = icon?.trim()?.takeIf { it.isNotBlank() },
                color = color?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        eventPublisher.publish(DomainEventType.CATEGORY_CREATED, "category", categoryId)
        return categoryId
    }

    suspend fun updateCategory(category: CategoryEntity, name: String, icon: String?, color: String?, type: CategoryType = category.type) {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Tên hạng mục không được trống" }
        database.categoryDao().update(
            category.copy(
                name = normalizedName,
                type = type,
                icon = icon?.trim()?.takeIf { it.isNotBlank() },
                color = color?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        eventPublisher.publish(DomainEventType.CATEGORY_UPDATED, "category", category.id)
    }

    suspend fun archiveCategory(category: CategoryEntity) {
        database.categoryDao().update(
            category.copy(
                isArchived = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        eventPublisher.publish(DomainEventType.CATEGORY_ARCHIVED, "category", category.id)
    }
}
