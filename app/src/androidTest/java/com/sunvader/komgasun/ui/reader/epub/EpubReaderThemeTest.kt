package com.sunvader.komgasun.ui.reader.epub

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EpubReaderThemeTest {
    @Test fun themeSurvivesReaderRecreationAndBookChanges() = withReaderPreferences { application ->
        val first = EpubReaderViewModel(application, "first-book")
        assertTrue(first.uiState.value.isDarkTheme)
        first.toggleTheme()
        assertFalse(first.uiState.value.isDarkTheme)
        val reopened = EpubReaderViewModel(application, "second-book")
        assertFalse(reopened.uiState.value.isDarkTheme)
        reopened.toggleTheme()
        assertTrue(reopened.uiState.value.isDarkTheme)
        assertTrue(EpubReaderViewModel(application, "first-book").uiState.value.isDarkTheme)
    }

    @Test fun fontSizeSurvivesReaderRecreationAndBookChanges() = withReaderPreferences { application ->
        val first = EpubReaderViewModel(application, "first-book")
        assertEquals(18, first.uiState.value.fontSizeSp)
        first.increaseFontSize()
        assertEquals(20, first.uiState.value.fontSizeSp)
        val reopened = EpubReaderViewModel(application, "first-book")
        assertEquals(20, reopened.uiState.value.fontSizeSp)
        val otherBook = EpubReaderViewModel(application, "second-book")
        assertEquals(20, otherBook.uiState.value.fontSizeSp)
        otherBook.decreaseFontSize()
        assertEquals(18, otherBook.uiState.value.fontSizeSp)
        assertEquals(18, EpubReaderViewModel(application, "first-book").uiState.value.fontSizeSp)
    }

    @Test fun fontSizeKeepsExistingStepsAndBounds() = withReaderPreferences { application ->
        val reader = EpubReaderViewModel(application, "first-book")
        repeat(10) { reader.decreaseFontSize() }
        assertEquals(12, reader.uiState.value.fontSizeSp)
        assertEquals(12, EpubReaderViewModel(application, "second-book").uiState.value.fontSizeSp)
        reader.increaseFontSize()
        assertEquals(14, reader.uiState.value.fontSizeSp)
        repeat(20) { reader.increaseFontSize() }
        assertEquals(32, reader.uiState.value.fontSizeSp)
        assertEquals(32, EpubReaderViewModel(application, "second-book").uiState.value.fontSizeSp)
        reader.decreaseFontSize()
        assertEquals(30, reader.uiState.value.fontSizeSp)
    }

    @Test fun originalFontsSurviveReaderRecreationAndBookChanges() = withReaderPreferences { application ->
        val first = EpubReaderViewModel(application, "first-book")
        assertFalse(first.uiState.value.useOriginalFonts)
        first.setUseOriginalFonts(true)
        assertTrue(first.uiState.value.useOriginalFonts)
        assertTrue(EpubReaderViewModel(application, "first-book").uiState.value.useOriginalFonts)
        val otherBook = EpubReaderViewModel(application, "second-book")
        assertTrue(otherBook.uiState.value.useOriginalFonts)
        otherBook.setUseOriginalFonts(false)
        assertFalse(otherBook.uiState.value.useOriginalFonts)
        assertFalse(EpubReaderViewModel(application, "first-book").uiState.value.useOriginalFonts)
    }

    private fun withReaderPreferences(test: (Application) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "epub_theme_test_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val application = object : Application() {
            override fun getSharedPreferences(requestedName: String, mode: Int): SharedPreferences =
                if (requestedName == "epub_reader_settings") preferences
                else context.getSharedPreferences("${name}_$requestedName", mode)
        }
        try {
            test(application)
        } finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("${name}_epub_locators")
            context.deleteSharedPreferences("${name}_epub_sync_device")
        }
    }
}
