package com.sunvader.komgasun.ui.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageTranslatorTest {
    @Test
    fun `normalizeOcrBlock collapses line wraps into spaces`() {
        val raw = "First line of\n  a paragraph\r\nthat continues here."
        assertEquals("First line of a paragraph that continues here.", normalizeOcrBlock(raw))
    }

    @Test
    fun `normalizeOcrBlock trims surrounding whitespace`() {
        assertEquals("Hello world", normalizeOcrBlock("  \n Hello world \r\n "))
    }
}
