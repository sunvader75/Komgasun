package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubPageInsetsTest {
    @Test fun sheetRetainsPreviousTextViewportAndCaptureOrigin() {
        val area = EpubPageInsets(80, 32).viewport(800, 1200)
        assertEquals(80, area.left)
        assertEquals(32, area.top)
        assertEquals(640, area.width)
        assertEquals(1136, area.height)
        assertEquals(720, area.left + area.width)
        assertEquals(1168, area.top + area.height)
    }

    @Test fun tapsUseTextCoordinatesAndOuterEdgesStillNavigate() {
        val area = EpubPageInsets(80, 32).viewport(800, 1200)
        for (fraction in listOf(0f, .2f, .5f, .8f, 1f)) {
            assertEquals(EpubPageTurn.tapDirection(fraction),
                EpubPageTurn.tapDirection(area.tapFraction(80 + 640 * fraction)))
        }
        assertEquals(-1, EpubPageTurn.tapDirection(area.tapFraction(0f)))
        assertEquals(1, EpubPageTurn.tapDirection(area.tapFraction(800f)))
    }

    @Test fun resizingChangesGeometryWithoutNegativeViewport() {
        val insets = EpubPageInsets(80, 32)
        assertNotEquals(insets.viewport(800, 1200), insets.viewport(1200, 800))
        val tiny = insets.viewport(60, 20)
        assertEquals(0, tiny.width)
        assertEquals(0, tiny.height)
        assertTrue(tiny.tapFraction(30f).isFinite())
    }
}
