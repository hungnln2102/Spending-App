package com.spendingapp.core.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackgroundSyncSettingsRepositoryTest {
    private lateinit var repository: BackgroundSyncSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = BackgroundSyncSettingsRepository(context)
        repository.clearForTest()
    }

    @Test
    fun defaultSettingsAreSafeAndNotRealtime() {
        val settings = repository.getSettings()

        assertFalse(settings.enabled)
        assertEquals(12, settings.intervalHours)
        assertFalse(settings.wifiOnly)
    }

    @Test
    fun saveSettingsNormalizesIntervalAndPersistsWifiOnly() {
        val saved = repository.saveSettings(BackgroundSyncSettings(enabled = true, intervalHours = 3, wifiOnly = true))
        val loaded = repository.getSettings()

        assertTrue(saved.enabled)
        assertEquals(6, saved.intervalHours)
        assertTrue(saved.wifiOnly)
        assertEquals(saved, loaded)
        assertEquals(saved, repository.observeSettings().value)
    }
}
