package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Models Android's unsupported SAX feature switches without changing the actual DOM parser. */
class RestrictedXmlFactory : javax.xml.parsers.DocumentBuilderFactory() {
    companion object { lateinit var delegateProvider: String }
    private val delegate = javax.xml.parsers.DocumentBuilderFactory.newInstance(delegateProvider, javaClass.classLoader)
    override fun newDocumentBuilder(): javax.xml.parsers.DocumentBuilder {
        delegate.isNamespaceAware = isNamespaceAware
        delegate.isExpandEntityReferences = isExpandEntityReferences
        return delegate.newDocumentBuilder()
    }
    override fun setFeature(name: String, value: Boolean) {
        throw javax.xml.parsers.ParserConfigurationException("Unsupported feature: $name")
    }
    override fun getFeature(name: String): Boolean = throw javax.xml.parsers.ParserConfigurationException(name)
    override fun setAttribute(name: String, value: Any?) = delegate.setAttribute(name, value)
    override fun getAttribute(name: String): Any = delegate.getAttribute(name)
}

class EpubParserTest {
    private fun fixture(opfPath: String = "book.opf", href: String = "chapter.xhtml",
                        member: String = "chapter.xhtml", prefix: String = "", utf16: Boolean = false,
                        chapter: String = "<html><head><title>Capítulo á</title></head><body><img src='cover.png'/></body></html>",
                        extra: Map<String, ByteArray> = emptyMap(), spineCount: Int = 1, mediaType: String = "application/xhtml+xml", block: (java.io.File) -> Unit) {
        val file = Files.createTempFile("epub-fixture", ".epub").toFile()
        val p = if (prefix.isEmpty()) "" else "$prefix:"
        fun xml(text: String): ByteArray = if (utf16) ("<?xml version='1.0' encoding='UTF-16'?>" + text).toByteArray(Charsets.UTF_16) else text.toByteArray()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                val entries = linkedMapOf(
                    "META-INF/container.xml" to xml("<${p}container xmlns${if (prefix.isEmpty()) "" else ":$prefix"}='urn:oasis:names:tc:opendocument:xmlns:container'><${p}rootfiles><${p}rootfile full-path='$opfPath'/></${p}rootfiles></${p}container>"),
                    opfPath to xml("<${p}package xmlns${if (prefix.isEmpty()) "" else ":$prefix"}='http://www.idpf.org/2007/opf' xmlns:dc='http://purl.org/dc/elements/1.1/'><${p}metadata><dc:title>Libro á</dc:title><dc:creator>Autor</dc:creator></${p}metadata><${p}manifest><${p}item id='c' href='$href' media-type='$mediaType'/></${p}manifest><${p}spine>${"<${p}itemref idref='c'/>".repeat(spineCount)}</${p}spine></${p}package>"),
                    member to xml(chapter)
                )
                entries.putAll(extra)
                entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            }
            block(file)
        } finally { file.delete() }
    }

    @Test fun publicationOpensWhenXmlProviderRejectsEntityFeatures() {
        val key = "javax.xml.parsers.DocumentBuilderFactory"
        val previous = System.getProperty(key)
        RestrictedXmlFactory.delegateProvider = javax.xml.parsers.DocumentBuilderFactory.newInstance().javaClass.name
        try {
            System.setProperty(key, RestrictedXmlFactory::class.java.name)
            fixture { file ->
                val book = EpubParser.parse(file)
                assertEquals("Libro á", book.title)
                assertEquals("Capítulo á", book.chapters.single().title)
                assertTrue(book.chapters.single().resourceBytes > 0L)
            }
            for (path in listOf("META-INF/container.xml", "book.opf")) {
                fixture(extra = mapOf(path to "<!DOCTYPE package SYSTEM 'https://invalid.example/dtd'><package/>".toByteArray())) {
                    assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("DTD"))
                }
            }
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    @Test fun simpleAndNamespacedPublications() {
        for (prefix in listOf("", "opf")) fixture(prefix = prefix) { file ->
            val book = EpubParser.parse(file)
            assertEquals("Libro á", book.title)
            assertEquals("Autor", book.author)
            assertEquals(1, book.chapters.size)
            assertEquals("Capítulo á", book.chapters.single().title)
            assertTrue(book.chapters.single().htmlContent.contains("<img"))
        }
    }

    @Test fun realFailingEpubCanBeParsed() {
        val file = java.io.File("../El_agente_de_las_estrellas.epub").takeIf { it.exists() }
            ?: java.io.File("El_agente_de_las_estrellas.epub").takeIf { it.exists() }
            ?: return
        val book = EpubParser.parse(file)
        assertEquals(31, book.chapters.size)
        val paths = book.chapters.map { it.resourcePath.orEmpty() }
        var startPos = 1
        val simulatedPositions = book.chapters.flatMap { ch ->
            val positionCount = maxOf(1, kotlin.math.ceil(ch.resourceBytes / 1024.0).toInt())
            (0 until positionCount).map { p ->
                val progression = (p.toFloat() / positionCount).toDouble()
                com.sunvader.komgasun.data.model.EpubLocatorDto(
                    href = ch.resourcePath!!,
                    type = "application/xhtml+xml",
                    locations = com.sunvader.komgasun.data.model.EpubLocationsDto(progression = progression, position = startPos++)
                )
            }
        }
        val dto = com.sunvader.komgasun.data.model.EpubPositionsDto(simulatedPositions.size, simulatedPositions)
        println("Simulated ${simulatedPositions.size} positions for 31 chapters")
        val serverPositions = EpubServerPositions.create(paths, dto)
        assertNotNull(serverPositions)

        // Now test Komga's exact validation from BookLifecycle.kt on both discreteProgression and serverPositions
        val mediaFiles = paths.toSet()
        val extensionPositions = simulatedPositions

        for ((idx, ch) in book.chapters.withIndex()) {
            for (p in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                // 1. Test discreteProgression (pre-cf56d38 logic)
                val discreteVal = EpubProgressionSync.discreteProgression(p, ch.resourceBytes).toFloat()
                val href = ch.resourcePath!!
                assertTrue("href in mediaFiles", href in mediaFiles)
                val matching = extensionPositions.filter { it.href == href }
                assertTrue("matching not empty", matching.isNotEmpty())
                val matchedDiscrete = matching.firstOrNull { it.locations!!.progression!!.toFloat() == discreteVal }
                    ?: run {
                        val before = matching.filter { it.locations!!.progression!!.toFloat() < discreteVal }.maxByOrNull { it.locations!!.position!! }
                        val after = matching.filter { it.locations!!.progression!!.toFloat() > discreteVal }.minByOrNull { it.locations!!.position!! }
                        if (before == null || after == null || before.locations!!.position!! > after.locations!!.position!!) {
                            println("DISCRETE REJECTED: ch=$idx ($href, bytes=${ch.resourceBytes}) prop=$p discreteVal=$discreteVal before=$before after=$after matching=${matching.map { it.locations!!.progression }}")
                            null
                        } else before
                    }

                // 2. Test serverPositions locator
                val locator = serverPositions.locator(idx, p)!!
                val locVal = locator.locations!!.progression!!.toFloat()
                val matchedServer = matching.firstOrNull { it.locations!!.progression!!.toFloat() == locVal }
                    ?: run {
                        val before = matching.filter { it.locations!!.progression!!.toFloat() < locVal }.maxByOrNull { it.locations!!.position!! }
                        val after = matching.filter { it.locations!!.progression!!.toFloat() > locVal }.minByOrNull { it.locations!!.position!! }
                        if (before == null || after == null || before.locations!!.position!! > after.locations!!.position!!) {
                            println("SERVER POS REJECTED: ch=$idx ($href) prop=$p locVal=$locVal before=$before after=$after")
                            null
                        } else before
                    }
                assertNotNull("Server position must always be accepted by Komga", matchedServer)
            }
        }
    }

    @Test fun canonicalReferencesChooseExactMember() {
        fixture("OPS/package/book.opf", "../Text/caf%C3%A9%20uno.xhtml#start", "OPS/Text/café uno.xhtml",
            extra = mapOf("wrong/café uno.xhtml" to "wrong".toByteArray())) { file ->
            assertEquals("Capítulo á", EpubParser.parse(file).chapters.single().title)
        }
    }

    @Test fun epub2DoctypeAndNamedEntitiesRemainRaw() {
        val html = "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.1//EN' 'http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd'><html><head><title>Uno&nbsp;dos</title></head><body>Texto&nbsp;con espacio</body></html>"
        fixture(chapter = html) {
            val chapter = EpubParser.parse(it).chapters.single()
            assertEquals(html, chapter.htmlContent)
            assertEquals("Uno&nbsp;dos", chapter.title)
        }
    }

    @Test fun htmlCompatibleChapterDoesNotRequireXml() {
        val html = "<!doctype html><HTML><HEAD><TITLE>Capítulo <b>HTML</b></TITLE></HEAD><BODY><br><p>Uno&nbsp;dos<p>Tres</BODY>"
        fixture(chapter = html, mediaType = "text/html") {
            val chapter = EpubParser.parse(it).chapters.single()
            assertEquals(html, chapter.htmlContent)
            assertEquals("Capítulo HTML", chapter.title)
        }
    }

    @Test fun chapterEntitiesAreNeverResolved() {
        val html = "<!DOCTYPE html [<!ENTITY x SYSTEM 'file:///not-readable'>]><html><title>&x;</title><body>&x;</body></html>"
        fixture(chapter = html) {
            val chapter = EpubParser.parse(it).chapters.single()
            assertEquals(html, chapter.htmlContent)
            assertEquals("&x;", chapter.title)
        }
    }

    @Test fun declaredUtf16() { fixture(utf16 = true) { assertEquals("Libro á", EpubParser.parse(it).title); assertEquals("Capítulo á", EpubParser.parse(it).chapters.single().title) } }

    @Test fun missingSpineMemberFailsRatherThanSkippingOrGuessing() {
        fixture(href = "missing/chapter.xhtml") { file ->
            val error = runCatching { EpubParser.parse(file) }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message.orEmpty().contains("missing/chapter.xhtml"))
        }
    }

    @Test fun rootEscapeFails() { fixture(href = "../chapter.xhtml") { assertNotNull(runCatching { EpubParser.parse(it) }.exceptionOrNull()) } }

    @Test fun resourcesAreReadOnDemandWithoutBasenameFallback() {
        fixture("OPS/book.opf", "Text/chapter.xhtml", "OPS/Text/chapter.xhtml",
            extra = mapOf("OPS/images/portada á.png" to byteArrayOf(1, 2, 3))) { file ->
            val book = EpubParser.parse(file)
            val publication = book.publication!!
            assertEquals("OPS/Text/chapter.xhtml", book.chapters.single().resourcePath)
            val image = publication.readResource(book.chapters.single().resourcePath!!, "../images/portada%20%C3%A1.png#x")
            assertEquals("OPS/images/portada á.png", image.path)
            assertEquals("application/octet-stream", image.mediaType)
            assertArrayEquals(byteArrayOf(1, 2, 3), image.bytes)
            assertTrue(runCatching { publication.readResource("OPS/book.opf", "missing.png") }.exceptionOrNull()!!.message!!.contains("missing.png"))
            assertNotNull(runCatching { publication.readResource("OPS/book.opf", "../../outside") }.exceptionOrNull())
        }
    }

    @Test fun dtdAndEncryptedPublicationsAreRejected() {
        for (path in listOf("META-INF/container.xml", "book.opf")) {
            fixture(extra = mapOf(path to "<!DOCTYPE package [<!ENTITY x SYSTEM 'file:///not-readable'>]><package>&x;</package>".toByteArray())) {
                assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("DTD"))
            }
        }
        fixture(extra = mapOf("META-INF/encryption.xml" to "encrypted".toByteArray())) {
            assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("DRM"))
        }
    }

    @Test fun emptyAndMissingSpineAreActionable() {
        fixture(spineCount = 0) { assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("spine")) }
        fixture(extra = mapOf("book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><spine><itemref idref='absent'/></spine></package>".toByteArray())) {
            assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("absent"))
        }
    }

    @Test fun entryAndTotalBudgetsAreEnforced() {
        fixture(chapter = "<html>" + "a".repeat(EpubPublication.MAX_ENTRY_BYTES) + "</html>") {
            assertTrue(runCatching { EpubParser.parse(it) }.exceptionOrNull()!!.message!!.contains("grande"))
        }
    }

    @Test fun totalBudgetIsEnforcedAcrossSpine() {
        fixture(chapter = "<html>" + "a".repeat(1024 * 1024) + "</html>", spineCount = 33) {
            assertNotNull(runCatching { EpubParser.parse(it) }.exceptionOrNull())
        }
    }

    @Test fun malformedZipIsActionable() {
        val file = Files.createTempFile("bad-epub", ".epub").toFile()
        try { file.writeText("not a zip"); assertNotNull(runCatching { EpubParser.parse(file) }.exceptionOrNull()) } finally { file.delete() }
    }
}
