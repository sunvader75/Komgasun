package com.sunvader.komgasun.ui.reader

import com.sunvader.komgasun.ui.reader.pdf.PdfPageCache
import org.junit.Assert.*
import org.junit.Test

class PdfPageCacheTest {
    @Test fun boundsBytesAndRejectsOversizedPages() {
        val cache = PdfPageCache<String>(10) { it.length.toLong() }
        cache.put(0, "123456")
        cache.put(1, "abcdef")
        assertNull(cache[0])
        assertEquals("abcdef", cache[1])
        cache.put(2, "12345678901")
        assertNull(cache[2])
        assertEquals("abcdef", cache[1])
    }
    @Test fun retainsOnlyNearbyPagesAndClears() {
        val cache = PdfPageCache<String>(100) { it.length.toLong() }
        (0..9).forEach { cache.put(it, "$it") }
        cache.retain(readerPageWindow(5, 10))
        assertNull(cache[0])
        readerPageWindow(5, 10).forEach { assertEquals("$it", cache[it]) }
        cache.clear()
        assertNull(cache[5])
    }
}
