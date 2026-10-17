package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubPaginationTest {
    @Test fun pagesAndChapterBoundaries() {
        assertEquals(EpubPosition(0, 1), EpubPagination.move(EpubPosition(0, 0), 3, 2, 1))
        assertEquals(EpubPosition(1, 0), EpubPagination.move(EpubPosition(0, 2), 3, 2, 1))
        assertEquals(EpubPosition(0, Int.MAX_VALUE), EpubPagination.move(EpubPosition(1, 0), 3, 2, -1))
        assertEquals(EpubPosition(0, 0), EpubPagination.move(EpubPosition(0, 0), 3, 2, -1))
    }
    @Test fun completionRequiresMeasuredLastPage() {
        assertFalse(EpubPagination.completed(1, 2, 0, 3))
        assertFalse(EpubPagination.completed(1, 2, 0, 0))
        assertTrue(EpubPagination.completed(1, 2, 2, 3))
        assertFalse(EpubPagination.completed(0, 2, 2, 3))
    }
    @Test fun approximateLocatorReflows() {
        assertEquals(0.5, EpubPagination.proportion(2, 5), 0.0001)
        assertEquals(5, EpubPagination.page(0.5, 11))
        assertEquals(0, EpubPagination.page(Double.NaN, 11))
        assertEquals(10, EpubPagination.page(1.0, 11))
    }
}
