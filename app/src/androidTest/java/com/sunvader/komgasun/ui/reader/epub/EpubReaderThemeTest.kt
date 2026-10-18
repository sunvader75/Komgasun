package com.sunvader.komgasun.ui.reader.epub

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EpubReaderThemeTest {
    @Test fun themeSurvivesReaderRecreationAndBookChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "epub_theme_test_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val application = object : Application() {
            override fun getSharedPreferences(requestedName: String, mode: Int): SharedPreferences =
                if (requestedName == "epub_reader_settings") preferences
                else context.getSharedPreferences("${name}_$requestedName", mode)
        }
        try {
            val first = EpubReaderViewModel(application, "first-book")
            assertTrue(first.uiState.value.isDarkTheme)
            first.toggleTheme()
            assertFalse(first.uiState.value.isDarkTheme)
            val reopened = EpubReaderViewModel(application, "second-book")
            assertFalse(reopened.uiState.value.isDarkTheme)
            reopened.toggleTheme()
            assertTrue(reopened.uiState.value.isDarkTheme)
            assertTrue(EpubReaderViewModel(application, "first-book").uiState.value.isDarkTheme)
        } finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("${name}_epub_locators")
            context.deleteSharedPreferences("${name}_epub_sync_device")
        }
    }
}
