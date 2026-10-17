package com.sunvader.komgasun.ui.reader.epub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubReaderStorageTest {
    @Test fun identityIsScopedAndPathSafe() {
        val key = EpubReaderStorage.identity("https://one", "user", "../../book")
        assertTrue(key.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(key, EpubReaderStorage.identity("https://two", "user", "../../book"))
        assertNotEquals(key, EpubReaderStorage.identity("https://one", "other", "../../book"))
        assertNotEquals(EpubReaderStorage.identity("ab", "c", "d"), EpubReaderStorage.identity("a", "bc", "d"))
    }
    @Test fun locatorRejectsInvalidAndOutdatedChapters() {
        assertEquals(1 to 0.5, EpubReaderStorage.decodeLocator("1:0.5", 2))
        for (value in listOf(null, "", "-1:0", "2:0", "0:NaN", "0:Infinity", "0:1.1", "0:0:0")) {
            assertNull(EpubReaderStorage.decodeLocator(value, 2))
        }
    }
    @Test fun serverResetAndRereadDiscardOldLocator() {
        assertNull(EpubReaderStorage.resumeLocator("0:1.0", 2, 0, false))
        assertNull(EpubReaderStorage.resumeLocator("1:1.0", 2, 2, true))
        assertNull(EpubReaderStorage.resumeLocator("0:0.5", 2, 2, false))
        assertEquals(1 to 0.5, EpubReaderStorage.resumeLocator("1:0.5", 2, 2, false))
    }
    @Test fun invalidCacheRedownloadsOnceAndGoodCacheIsReused() = runTest {
        val file = Files.createTempFile("epub-cache-", ".epub").toFile()
        try {
            file.writeText("not a ZIP")
            var calls = 0
            val book = EpubReaderStorage.load(file) { target -> calls++; valid(target); target }
            assertEquals(1, calls)
            assertEquals(1, book.chapters.size)
            assertEquals(1, EpubReaderStorage.load(file) { error("must not download valid cache") }.chapters.size)
            assertEquals(1, calls)
        } finally { file.delete() }
    }
    @Test fun invalidFreshDownloadDoesNotLoop() = runTest {
        val file = Files.createTempFile("epub-cache-", ".epub").toFile()
        try {
            file.writeText("bad")
            var calls = 0
            try {
                EpubReaderStorage.load(file) { calls++; it.writeText("still bad"); it }
                fail("invalid fresh EPUB must fail")
            } catch (_: EpubException) { assertEquals(1, calls) }
        } finally { file.delete() }
    }
    @Test fun cancellationDoesNotRetryOrReplaceCache() = runTest {
        val file = Files.createTempFile("epub-cache-", ".epub").toFile()
        try {
            file.writeText("bad")
            var calls = 0
            try {
                EpubReaderStorage.load(file) { calls++; throw CancellationException("closed") }
                fail("must propagate cancellation")
            } catch (_: CancellationException) {
                assertEquals(1, calls)
                assertEquals("bad", file.readText())
            }
        } finally { file.delete() }
    }
    private fun valid(file: java.io.File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            mapOf(
                "META-INF/container.xml" to "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.html' media-type='text/html'/></manifest><spine><itemref idref='a'/></spine></package>",
                "a.html" to "<p>Readable</p>"
            ).forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }
}
