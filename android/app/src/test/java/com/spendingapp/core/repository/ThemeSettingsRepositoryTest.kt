package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeSettingsRepositoryTest {
    private lateinit var repository: ThemeSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ThemeSettingsRepository(context)
        repository.clearForTest()
    }

    @Test
    fun savesThemePreferenceAndExposesFlow() {
        val saved = repository.saveSettings(ThemeSettings(ThemeMode.DARK))

        assertEquals(ThemeMode.DARK, saved.mode)
        assertEquals(saved, repository.getSettings())
        assertEquals(saved, repository.observeSettings().value)
    }
}
