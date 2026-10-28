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
}
