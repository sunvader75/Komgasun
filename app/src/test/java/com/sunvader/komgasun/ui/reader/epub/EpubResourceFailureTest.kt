package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubResourceFailureTest {
    @Test fun androidCanonicalPunctuationContract() {
        val path = "dir/a +!'()~*_-.,;:@&=é中.jpg"
        val canonical = "https://epub.invalid/dir/a%20%2B!'()~*_-.%2C%3B%3A%40%26%3D%C3%A9%E4%B8%AD.jpg"
        val pub = EpubPublication(java.io.File("unused"), "book.opf", mapOf(path to "image/jpeg"))
        assertEquals(path, pub.canonicalPath(canonical))
        for (bad in listOf(canonical.replace("%20", "+"), canonical.replace("!", "%21"), canonical.replace("*", "%2A"))) {
            try { pub.canonicalPath(bad); fail() } catch (e: EpubResourceFailure) { assertEquals(EpubResourceCategory.URI_OR_CANONICAL, e.category) }
        }
    }
    @Test fun canonicalResourceAndRemovedArchiveHaveMeasuredResults() {
        val file = Files.createTempFile("epub-resource-", ".zip").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { z ->
                z.putNextEntry(ZipEntry("imágen.jpg")); z.write(byteArrayOf(1,2,3)); z.closeEntry()
            }
            val pub = EpubPublication(file, "content.opf", mapOf("imágen.jpg" to "image/jpeg", "missing.jpg" to "image/jpeg"))
            val source = "https://epub.invalid/im%C3%A1gen.jpg"
            assertArrayEquals(byteArrayOf(1,2,3), pub.readCanonicalResource(source).bytes)
            for ((input, expected) in listOf("" to "EMPTY_SOURCE", "https://outside.invalid/a" to "INVALID_SOURCE_ORIGIN", "https://epub.invalid/no.jpg" to "NOT_MANIFEST", "https://epub.invalid/missing.jpg" to "ENTRY_MISSING", "https://epub.invalid/%ZZ" to "URI_OR_CANONICAL")) {
                try { pub.readCanonicalResource(input); fail(input) } catch (e: EpubResourceFailure) { assertEquals(expected, e.category.name) }
            }
            assertTrue(file.delete())
            try { pub.readCanonicalResource(source); fail() } catch (e: EpubResourceFailure) { assertEquals("ARCHIVE_MISSING", e.category.name) }
        } finally { file.delete() }
    }
}
