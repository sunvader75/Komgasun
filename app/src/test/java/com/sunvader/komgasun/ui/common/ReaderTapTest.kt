package com.sunvader.komgasun.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapTest {
    @Test fun scaledPagesNavigateOnlyAtEdges() {
        for (scale in listOf(0.5f, 0.9f, 1.1f, 3f)) {
            assertEquals(-1, readerTapDirection(scale, 0f))
            assertEquals(-1, readerTapDirection(scale, 0.149f))
            assertEquals(0, readerTapDirection(scale, 0.15f))
            assertEquals(0, readerTapDirection(scale, 0.5f))
            assertEquals(0, readerTapDirection(scale, 0.85f))
            assertEquals(1, readerTapDirection(scale, 0.851f))
            assertEquals(1, readerTapDirection(scale, 1f))
        }
    }

    @Test fun normalSizeKeepsControlsAtEveryPosition() {
        for (x in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) {
            assertEquals(0, readerTapDirection(1f, x))
        }
    }
}
