package com.sunvader.komgasun.ui.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `chooseBackgroundColor returns median color ignoring dark glyph outliers`() {
        val creamPaper = 0xFFFAF6EE.toInt()
        val darkInk = 0xFF101010.toInt()
        val samples = intArrayOf(
            creamPaper, creamPaper, darkInk, creamPaper,
            creamPaper, darkInk, creamPaper, creamPaper, creamPaper
        )
        assertEquals(creamPaper, chooseBackgroundColor(samples))
    }

    @Test
    fun `chooseContrastingTextColor picks dark ink on light paper and light ink on dark background`() {
        assertEquals(0xFF1C1C1C.toInt(), chooseContrastingTextColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFF2F2F2.toInt(), chooseContrastingTextColor(0xFF1E1E1E.toInt()))
    }

    @Test
    fun `findBestFontSize binary-searches largest font size that fits box`() {
        val size = findBestFontSize(
            boxHeight = 120,
            lineCount = 4
        ) { candidate -> candidate <= 19.5f }

        assertTrue("Expected font size near 19.5f but got $size", size in 19.0f..19.5f)
    }
}
