package com.sunvader.komgasun.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderPolicyTest {
    @Test fun prioritizesCurrentThenThreeAheadThenPrevious() {
        assertEquals(listOf(5, 6, 7, 8, 4), readerPageWindow(5, 20))
    }
    @Test fun clampsWindowAtBookBoundaries() {
        assertEquals(listOf(0, 1, 2, 3), readerPageWindow(0, 4))
        assertEquals(listOf(3, 2), readerPageWindow(3, 4))
        assertEquals(emptyList<Int>(), readerPageWindow(0, 0))
    }
    @Test fun showsFailureInsteadOfIndefiniteLoading() {
        assertEquals(ReaderLoadStatus.ERROR, readerLoadStatus(false, false, "Invalid EPUB"))
        assertEquals(ReaderLoadStatus.ERROR, readerLoadStatus(true, false, "Download failed"))
    }
    @Test fun preservesLoadingAndReadyStates() {
        assertEquals(ReaderLoadStatus.LOADING, readerLoadStatus(true, false, null))
        assertEquals(ReaderLoadStatus.LOADING, readerLoadStatus(false, false, null))
        assertEquals(ReaderLoadStatus.READY, readerLoadStatus(false, true, null))
    }
}
