package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `dark mode is off before anything is chosen`() {
        assertFalse(SettingsStore(context).isDarkMode)
    }

    @Test
    fun `dark mode survives a new store instance`() {
        SettingsStore(context).isDarkMode = true
        assertTrue(SettingsStore(context).isDarkMode)
    }
}
