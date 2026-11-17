package com.sunvader.komgasun.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPanTest {
    @Test fun reducedPageCanBeDragged() {
        assertEquals(Offset(40f, -30f), readerPanOffset(Offset.Zero, Offset(40f, -30f), 0.5f, 1000f, 600f))
    }

    @Test fun enlargedPageKeepsExistingBounds() {
        assertEquals(Offset(500f, -300f), readerPanOffset(Offset.Zero, Offset(900f, -900f), 2f, 1000f, 600f))
    }

    @Test fun normalPageStaysInPlace() {
        assertEquals(Offset.Zero, readerPanOffset(Offset.Zero, Offset(40f, -30f), 1f, 1000f, 600f))
    }

    @Test fun zoomedPanDelegatesOverflowToVerticalScroll() {
        val step = readerPanWithScroll(
            offset = Offset.Zero,
            pan = Offset(0f, -450f),
            scale = 2f,
            width = 1000f,
            height = 600f,
            canScrollVertically = true
        )
        assertEquals(Offset(0f, -300f), step.offset)
        assertEquals(75f, step.scrollDelta, 0.01f)
    }

    @Test fun zoomedPanDoesNotScrollWithoutVerticalScroll() {
        val step = readerPanWithScroll(
            offset = Offset.Zero,
            pan = Offset(0f, -450f),
            scale = 2f,
            width = 1000f,
            height = 600f,
            canScrollVertically = false
        )
        assertEquals(Offset(0f, -300f), step.offset)
        assertEquals(0f, step.scrollDelta, 0f)
    }
}
