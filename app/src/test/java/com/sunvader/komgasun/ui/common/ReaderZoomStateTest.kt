package com.sunvader.komgasun.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ReaderZoomStateTest {
    @Test fun sharedStateRetainsScaleAndPositionAcrossPages() {
        for (scale in listOf(0.5f, 2.5f)) {
            val reader = ReaderZoomState()
            reader.scale.floatValue = scale
            reader.offset.value = if (scale > 1f) Offset(120f, -80f) else Offset.Zero
            val nextPage = reader
            assertEquals(scale, nextPage.scale.floatValue, 0f)
            assertEquals(reader.offset.value, nextPage.offset.value)
            assertTrue(nextPage.isScaled)
            nextPage.scale.floatValue = 1f
            nextPage.offset.value = Offset.Zero
            assertFalse(reader.isScaled)
        }
    }

    @Test fun newReaderStartsAtNormalSizeWithoutDisplacement() {
        val reader = ReaderZoomState()
        assertEquals(1f, reader.scale.floatValue, 0f)
        assertEquals(Offset.Zero, reader.offset.value)
        assertFalse(reader.isScaled)
    }
}
