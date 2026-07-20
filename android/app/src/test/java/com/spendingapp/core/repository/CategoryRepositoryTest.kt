package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryRepositoryTest {
    private lateinit var database: SpendingDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpendingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CategoryRepository(database, DomainEventPublisher(database))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createCategoryStoresLocalCategoryAndEvent() = runBlocking {
        val categoryId = repository.createCategory("  Cafe  ", CategoryType.EXPENSE, "☕", "#AA77FF")

        val categories = repository.observeCategories().first()
        val events = database.domainEventDao().pendingDispatch()
        assertEquals(1, categories.size)
        assertEquals(categoryId, categories.first().id)
        assertEquals("Cafe", categories.first().name)
        assertEquals(CategoryType.EXPENSE, categories.first().type)
        assertTrue(events.any { it.type == DomainEventType.CATEGORY_CREATED && it.aggregateId == categoryId })
    }

    @Test
    fun updateCategoryChangesNameIconColorAndEvent() = runBlocking {
        val categoryId = repository.createCategory("Cafe", CategoryType.EXPENSE, "☕", "#AA77FF")
        val category = database.categoryDao().getById(categoryId)!!

        repository.updateCategory(category, "Ăn sáng", "🥐", "#FFAA66")

        val updated = database.categoryDao().getById(categoryId)!!
        val events = database.domainEventDao().pendingDispatch()
        assertEquals("Ăn sáng", updated.name)
        assertEquals("🥐", updated.icon)
        assertEquals("#FFAA66", updated.color)
        assertTrue(events.any { it.type == DomainEventType.CATEGORY_UPDATED && it.aggregateId == categoryId })
    }

    @Test
    fun archiveCategoryHidesItFromActiveListAndEvent() = runBlocking {
        val categoryId = repository.createCategory("Cafe", CategoryType.EXPENSE, "☕", "#AA77FF")
        val category = database.categoryDao().getById(categoryId)!!

        repository.archiveCategory(category)

        val activeCategories = repository.observeCategories().first()
        val archived = database.categoryDao().getById(categoryId)!!
        val events = database.domainEventDao().pendingDispatch()
        assertTrue(archived.isArchived)
        assertEquals(0, activeCategories.size)
        assertTrue(events.any { it.type == DomainEventType.CATEGORY_ARCHIVED && it.aggregateId == categoryId })
    }
}
