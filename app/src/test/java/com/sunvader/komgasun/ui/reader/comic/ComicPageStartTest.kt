package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageStartTest {
    @Test fun enlargedPageStartsAtTopLeftIncludingFitMargins() {
        // Portrait: a narrow page is centered inside the viewport before zoom.
        assertEquals(Offset(250f, 1500f), comicPageStartOffset(2.5f, 1000f, 2000f, 600f, 2000f))
        // Landscape: the page fills the width and its scroll container starts at the top.
        assertEquals(Offset(1500f, 750f), comicPageStartOffset(2.5f, 2000f, 1000f, 1000f, 2000f))
        // A short page has a vertical fit margin in portrait.
        assertEquals(Offset(750f, -375f), comicPageStartOffset(2.5f, 1000f, 2000f, 2000f, 1000f))
    }
}
