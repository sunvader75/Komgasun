package com.sunvader.komgasun.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMinimumScaleTest {
    @Test fun tallLandscapePageCanFitEntirely() {
        val viewportHeight = 300f
        val pageHeight = 1600f
        val minimum = readerMinimumScale(0.5f, viewportHeight / pageHeight)
        assertEquals(0.1875f, minimum, 0f)
        assertEquals(viewportHeight, pageHeight * minimum, 0.001f)
    }

    @Test fun ordinaryPagesKeepPreviousReductionLimit() {
        assertEquals(0.5f, readerMinimumScale(0.5f, 1f), 0f)
        assertEquals(0.5f, readerMinimumScale(0.5f, 0.75f), 0f)
    }
}
