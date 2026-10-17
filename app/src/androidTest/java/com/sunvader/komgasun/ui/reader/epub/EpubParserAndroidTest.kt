package com.sunvader.komgasun.ui.reader.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubParserAndroidTest {
    @Test fun ordinaryPublicationOpensWithAndroidXmlProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("epub-provider-", ".epub", context.cacheDir)
        val html = "<!doctype html><title>Readable</title><p>Hola&nbsp;mundo"
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                val entries = mapOf(
                    "META-INF/container.xml" to """
                        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles><rootfile full-path="book.opf"/></rootfiles>
                        </container>
                    """.trimIndent(),
                    "book.opf" to """
                        <package xmlns="http://www.idpf.org/2007/opf">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Book</dc:title></metadata>
                          <manifest><item id="c" href="chapter.html" media-type="text/html"/></manifest>
                          <spine><itemref idref="c"/></spine>
                        </package>
                    """.trimIndent(),
                    "chapter.html" to html
                )
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            val book = EpubParser.parse(file)
            assertEquals("Book", book.title)
            val chapter = book.chapters.single()
            assertEquals("Readable", chapter.title)
            assertEquals("chapter.html", chapter.resourcePath)
            assertEquals(html, chapter.htmlContent)
        } finally {
            file.delete()
        }
    }
}
