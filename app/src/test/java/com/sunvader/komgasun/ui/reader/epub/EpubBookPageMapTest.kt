package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubBookPageMapTest {
    @Test fun exactPagesUseMeasuredCountsNotEqualChapterSpans() {
        val map = EpubBookPageMap(listOf(2, 5, 1))
        assertEquals(8, map.total)
        assertEquals(4, map.absolutePage(1, 1))
        assertEquals(EpubPosition(0, 0), map.position(1))
        assertEquals(EpubPosition(0, 1), map.position(2))
        assertEquals(EpubPosition(1, 0), map.position(3))
        assertEquals(EpubPosition(2, 0), map.position(8))
        assertEquals(1.0, map.proportion(1, 4), 0.0)
        assertEquals(0.0, map.proportion(2, 0), 0.0)
    }

    @Test fun sliderPreviewCoversEveryPageAndEndpoints() {
        val map = EpubBookPageMap(listOf(2, 5, 1))
        assertEquals(1, map.sliderPage(0f))
        assertEquals(8, map.sliderPage(1f))
        assertEquals(5, map.sliderPage(0.5f))
        for (page in 1..8) assertEquals(page, map.sliderPage(map.sliderFraction(page)))
    }

    @Test fun singlePageBookHasNoDivisionByZero() {
        val map = EpubBookPageMap(listOf(1))
        assertEquals(1, map.sliderPage(1f))
        assertEquals(0f, map.sliderFraction(1))
        assertEquals(EpubPosition(0, 0), map.position(1))
    }
}
