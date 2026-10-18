package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubBookProgressTest {
    @Test fun seeksAcrossEntireBookIncludingEndpoints() {
        assertEquals(0 to 0.0, EpubBookProgress.position(0.0, 4))
        assertEquals(2 to 0.5, EpubBookProgress.position(0.625, 4))
        assertEquals(3 to 1.0, EpubBookProgress.position(1.0, 4))
        assertEquals(0 to 0.5, EpubBookProgress.position(0.5, 1))
    }
    @Test fun progressMatchesSeekPosition() {
        assertEquals(0.625, EpubBookProgress.fraction(2, 0.5, 4), 0.00001)
        assertEquals(1.0, EpubBookProgress.fraction(3, 1.0, 4), 0.00001)
    }
}
