package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubPathTest {
    @Test fun resolvesRelativeReferencesAndFragments() {
        assertEquals("OPS/Text/café uno.xhtml", EpubPath.resolve("OPS/package/book.opf", "../Text/caf%C3%A9%20uno.xhtml#start"))
        assertEquals("book.opf", EpubPath.resolve("book.opf", "#start"))
        assertEquals("Text/a+b.xhtml", EpubPath.resolve("book.opf", "Text/a+b.xhtml"))
        assertEquals("Text/a.xhtml", EpubPath.resolve("book.opf", "Text/./nested/../a.xhtml"))
    }

    @Test fun rejectsEscapesAndNonPublicationReferences() {
        for (reference in listOf("../x", "%2e%2e/x", "/x", "%2Fx", "a%5Cb", "https://example.org/a", "//example.org/a", "file:///x", "x?q=1", "%00", "%zz")) {
            assertNotNull(reference, runCatching { EpubPath.resolve("book.opf", reference) }.exceptionOrNull())
        }
    }
}
