package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EpubBookScrubberTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exactDestinationUpdatesDuringDragAndSeeksOnlyOnRelease() {
        var sought: Int? = null
        compose.setContent {
            MaterialTheme {
                EpubBookScrubber(EpubBookPageMap(listOf(2, 5, 1)), 1, true, false, false,
                    onSeek = { sought = it }, modifier = Modifier.width(300.dp).padding(top = 80.dp))
            }
        }
        compose.onNodeWithText("1 / 8").assertIsDisplayed()
        compose.onNodeWithTag("epub-book-slider").performTouchInput {
            down(Offset(10f, centerY))
            moveTo(Offset(width - 1f, centerY))
        }
        compose.onNodeWithTag("epub-page-indicator").assertTextEquals("8 / 8")
        compose.runOnIdle { assertNull(sought) }
        compose.onNodeWithTag("epub-book-slider").performTouchInput { up() }
        compose.runOnIdle { assertEquals(8, sought) }
    }

    @Test fun pendingAndFailedCalculationStillAllowWholeBookNavigation() {
        var sought = -1f
        val failed = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                EpubBookScrubber(null, 1, true, failed.value, false, onSeek = {},
                    onSeekFraction = { sought = it }, bookFraction = 0.25f,
                    modifier = Modifier.width(300.dp).padding(top = 80.dp))
            }
        }
        compose.onNodeWithTag("epub-page-indicator").assertTextContains("25 %", substring = true)
        compose.onNodeWithTag("epub-book-slider").performTouchInput { click(Offset(width - 1f, centerY)) }
        compose.runOnIdle { org.junit.Assert.assertTrue("Expected end-of-book destination, got $sought", sought > 0.99f); failed.value = true }
        compose.onNodeWithTag("epub-book-slider").assertIsEnabled()
        compose.onNodeWithTag("epub-page-indicator").assertTextContains("Total no disponible", substring = true)
    }

    @Test fun calculatingDoesNotShowAnEstimatedTotal() {
        compose.setContent {
            MaterialTheme { EpubBookScrubber(null, 1, true, false, false, onSeek = {}) }
        }
        compose.onNodeWithTag("epub-page-indicator").assertTextContains("Calculando páginas…", substring = true)
        compose.onNodeWithTag("epub-book-slider").assertIsEnabled()
    }
}
