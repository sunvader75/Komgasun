package com.sunvader.komgasun.ui.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageLayoutTest {
    @Test fun reductionScalesTheWholeLandscapePage() {
        assertEquals(800f, pdfPageHeight(1200f, 600f, 900f, 1200f, 0.5f), 0.01f)
    }

    @Test fun landscapeFitsTallPageToWidth() {
        assertEquals(1600f, pdfPageHeight(1200f, 600f, 900f, 1200f), 0.01f)
    }
    @Test fun landscapePreservesWidePageAspectRatio() {
        assertEquals(400f, pdfPageHeight(1200f, 600f, 1800f, 600f), 0.01f)
    }
    @Test fun portraitAndSquareKeepFit() {
        assertEquals(800f, pdfPageHeight(600f, 1200f, 900f, 1200f), 0.01f)
        assertEquals(1200f, pdfPageHeight(600f, 1200f, 600f, 2400f), 0.01f)
        assertEquals(600f, pdfPageHeight(600f, 600f, 600f, 2400f), 0.01f)
    }
    @Test fun invalidIntrinsicDimensionsStayViewportBounded() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(600f, pdfPageHeight(1200f, 600f, invalid, 100f), 0f)
            assertEquals(600f, pdfPageHeight(1200f, 600f, 100f, invalid), 0f)
        }
    }
}
