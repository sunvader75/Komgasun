package com.sunvader.komgasun.ui.reader.comic

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageLayoutTest {
    @Test fun reductionScalesTheWholeLandscapePage() {
        assertEquals(800f, comicPageHeight(1200f, 600f, 900f, 1200f, 0.5f), 0.01f)
    }

    @Test fun landscapeFitsWidthWithoutShrinkingTallPages() {
        assertEquals(1600f, comicPageHeight(1200f, 600f, 900f, 1200f), 0.01f)
    }

    @Test fun landscapePreservesWideImageAspectRatio() {
        assertEquals(400f, comicPageHeight(1200f, 600f, 1800f, 600f), 0.01f)
    }

    @Test fun portraitRetainsFitWithinViewport() {
        assertEquals(800f, comicPageHeight(600f, 1200f, 900f, 1200f), 0.01f)
        assertEquals(1200f, comicPageHeight(600f, 1200f, 600f, 2400f), 0.01f)
    }

    @Test fun unknownDimensionsStayBounded() {
        assertEquals(600f, comicPageHeight(1200f, 600f, 0f, 0f), 0f)
        assertEquals(600f, comicPageHeight(1200f, 600f, Float.NaN, 10f), 0f)
    }
}
