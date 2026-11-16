package com.sunvader.komgasun.ui.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `findBestFontSize ignores inflated OCR lineCount and finds true max font size that fits boxHeight`() {
        // Even if OCR reported 35 fragmented lines in a 240px box, if 16.5px fits the StaticLayout, it must find ~16.5f.
        val size = findBestFontSize(
            boxHeight = 240,
            lineCount = 35
        ) { candidate -> candidate <= 16.5f }

        assertTrue("Expected font size near 16.5f but got $size", size in 16.3f..16.6f)
    }

    @Test
    fun `mergeSameRowLines joins horizontal fragments on the same visual row`() {
        val frag1 = OcrVisualLine(left = 100, top = 200, right = 420, bottom = 224, text = "ways—through natural")
        val frag2 = OcrVisualLine(left = 430, top = 202, right = 880, bottom = 225, text = "language, images, and videos—and")
        val nextRow = OcrVisualLine(left = 100, top = 236, right = 880, bottom = 260, text = "ity to learn, plan, and act.")

        val rows = mergeSameRowLines(listOf(frag2, frag1, nextRow))

        assertEquals(2, rows.size)
        assertEquals("ways—through natural language, images, and videos—and", rows[0].text)
        assertEquals(100, rows[0].left)
        assertEquals(880, rows[0].right)
    }

    @Test
    fun `extractParagraphsFromVisualLines separates indented paragraphs and unhyphenates words without splitting normal line gaps`() {
        // Simula las coordenadas reales de la captura: alto de letra 8px y espacio entre renglones 8px (pitch constante de 16px).
        val lines = listOf(
            OcrVisualLine(left = 168, top = 124, right = 604, bottom = 132, text = "have vulnerabilities; for instance, publicly"),
            OcrVisualLine(left = 168, top = 140, right = 432, bottom = 148, text = "known software flaws may remain unpatched."),
            OcrVisualLine(left = 194, top = 156, right = 600, bottom = 164, text = "Even without AI, my phone represents a rich enter-"),
            OcrVisualLine(left = 168, top = 172, right = 580, bottom = 180, text = "prise attack surface that keeps growing.")
        )

        val paragraphs = extractParagraphsFromVisualLines(lines, blockLeft = 168, blockRight = 604)

        assertEquals(2, paragraphs.size)
        assertEquals("have vulnerabilities; for instance, publicly known software flaws may remain unpatched.", paragraphs[0].text)
        assertFalse(paragraphs[0].indented)
        assertEquals("Even without AI, my phone represents a rich enterprise attack surface that keeps growing.", paragraphs[1].text)
        assertTrue(paragraphs[1].indented)
    }

    @Test
    fun `mergeContiguousOcrBlocks joins split blocks of same column while keeping paragraphs separate`() {
        val heading = OcrRawBlock(left = 100, top = 80, right = 600, valBottom = 140, lineCount = 1, text = "Chapter 1")
        val part1 = OcrRawBlock(left = 100, top = 200, right = 880, valBottom = 260, lineCount = 2, text = "First paragraph.")
        val part2 = OcrRawBlock(left = 102, top = 270, right = 720, valBottom = 330, lineCount = 2, text = "Second paragraph.")

        val merged = mergeContiguousOcrBlocks(listOf(heading, part1, part2), pageWidth = 1000)

        assertEquals(2, merged.size)
        assertEquals("Chapter 1", merged[0].text)
        assertEquals(2, merged[1].paragraphs.size)
        assertEquals("First paragraph.\nSecond paragraph.", merged[1].text)
        assertTrue(merged[1].paragraphs[1].indented)
        assertEquals(4, merged[1].lineCount)
        assertEquals(880, merged[1].right)
        assertEquals(330, merged[1].bottom)
    }

    @Test
    fun `expandBoxesToPageSpace widens narrow paragraph to column margin and expands into vertical gap`() {
        val wideParagraph = PdfTextBox(
            left = 100, top = 100, right = 900, bottom = 220,
            lineCount = 4, translatedText = "Primer párrafo ancho de referencia."
        )
        val narrowParagraph = PdfTextBox(
            left = 100, top = 260, right = 680, bottom = 320,
            lineCount = 2, translatedText = "Segundo párrafo traducido que necesita más espacio."
        )

        val expanded = expandBoxesToPageSpace(listOf(wideParagraph, narrowParagraph), pageWidth = 1000, pageHeight = 1400)

        assertEquals(900, expanded[1].right)
        assertEquals(680, expanded[1].eraseRight)
        assertTrue("Expected bottom to expand into free space below 320", expanded[1].bottom > 320)
        assertEquals(320, expanded[1].eraseBottom)
    }
}
