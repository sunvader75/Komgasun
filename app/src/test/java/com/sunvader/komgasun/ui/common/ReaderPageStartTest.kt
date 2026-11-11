package com.sunvader.komgasun.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageStartTest {
    @Test fun reducedAndNormalPagesStartAtZeroOffset() {
        for (scale in listOf(0.1f, 0.5f, 0.75f, 1.0f)) {
            // Landscape tall page
            assertEquals(Offset.Zero, readerPageStartOffset(scale, 1200f, 600f, 900f, 1200f))
            // Portrait narrow page
            assertEquals(Offset.Zero, readerPageStartOffset(scale, 600f, 1200f, 900f, 1200f))
            // Square / wide page
            assertEquals(Offset.Zero, readerPageStartOffset(scale, 1200f, 600f, 1800f, 600f))
        }
    }

    @Test fun enlargedLandscapePageAlignsTopLeftOrigin() {
        // Landscape tall page at 2.5x: fills width, top starts at top of screen
        assertEquals(Offset(1500f, 750f), readerPageStartOffset(2.5f, 2000f, 1000f, 1000f, 2000f))
    }

    @Test fun enlargedPortraitPageAlignsTopLeftOrigin() {
        // Portrait narrow page at 2.5x
        assertEquals(Offset(250f, 1500f), readerPageStartOffset(2.5f, 1000f, 2000f, 600f, 2000f))
    }

    @Test fun invalidDimensionsReturnZero() {
        assertEquals(Offset.Zero, readerPageStartOffset(2f, 1000f, 600f, 0f, 1000f))
        assertEquals(Offset.Zero, readerPageStartOffset(2f, 1000f, 600f, Float.NaN, 1000f))
        assertEquals(Offset.Zero, readerPageStartOffset(2f, 1000f, 600f, 1000f, -10f))
    }
}
