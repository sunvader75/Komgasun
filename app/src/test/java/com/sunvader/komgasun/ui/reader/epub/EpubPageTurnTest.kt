package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubPageTurnTest {
    @Test fun shortDragReturnsButDistanceOrForwardVelocityCompletes() {
        assertFalse(EpubPageTurn.complete(0.2f, 0f))
        assertTrue(EpubPageTurn.complete(0.34f, 0f))
        assertTrue(EpubPageTurn.complete(0.1f, 1.5f))
        assertFalse(EpubPageTurn.complete(0.1f, -2f))
    }

    @Test fun previewUsesAdjacentPageOrChapterWithoutChangingSource() {
        val source = EpubReaderUiState(currentChapterIndex = 1, page = 2, pageCount = 4)
        assertEquals(1.0, EpubPageTurn.destination(source, 3, 1)?.proportion)
        assertEquals(1.0 / 3, EpubPageTurn.destination(source, 3, -1)?.proportion)
        val next = EpubPageTurn.destination(source.copy(page = 3), 3, 1)!!
        assertEquals(2, next.currentChapterIndex)
        assertEquals(0.0, next.proportion, 0.0)
        val previous = EpubPageTurn.destination(source.copy(page = 0), 3, -1)!!
        assertEquals(0, previous.currentChapterIndex)
        assertEquals(1.0, previous.proportion, 0.0)
        assertEquals(2, source.page)
        assertNull(EpubPageTurn.destination(source.copy(currentChapterIndex = 0, page = 0), 3, -1))
        assertNull(EpubPageTurn.destination(source.copy(currentChapterIndex = 2, page = 3), 3, 1))
    }

    @Test fun existingTapZonesAndCenterControlsRemain() {
        assertEquals(-1, EpubPageTurn.tapDirection(0.1f))
        assertEquals(0, EpubPageTurn.tapDirection(0.5f))
        assertEquals(1, EpubPageTurn.tapDirection(0.9f))
    }
}
