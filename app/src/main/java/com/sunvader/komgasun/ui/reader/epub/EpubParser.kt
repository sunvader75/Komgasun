package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val htmlContent: String,
    val resourcePath: String? = null,
    val resourceBytes: Long = 0L
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
    val publication: EpubPublication? = null
)

object EpubParser {
    private const val OPF = "http://www.idpf.org/2007/opf"
    private const val CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container"
    private const val DC = "http://purl.org/dc/elements/1.1/"

    fun parse(epubFile: File): EpubBook = EpubPublication.withArchive(epubFile) { zip ->
        var remaining = EpubPublication.MAX_PARSE_BYTES
        fun read(path: String): ByteArray {
            if (remaining <= 0) throw EpubException("EPUB supera el límite total de análisis de 32 MiB")
            return EpubPublication.readEntry(zip, path, minOf(remaining, EpubPublication.MAX_ENTRY_BYTES)).also { remaining -= it.size }
        }
        if (zip.getEntry("META-INF/encryption.xml") != null) {
            throw EpubException("EPUB cifrado/DRM u ofuscación no compatible (META-INF/encryption.xml)")
        }
        val container = xml(read("META-INF/container.xml"))
        val root = container.elements(CONTAINER, "rootfile").firstOrNull()
            ?: throw EpubException("EPUB sin rootfile en META-INF/container.xml")
        val packagePath = EpubPath.resolve("", root.getAttribute("full-path"))
        val opf = xml(read(packagePath))
        val manifest = linkedMapOf<String, Pair<String, String>>()
        val hrefs = mutableMapOf<String, String>()
        for (item in opf.elements(OPF, "item")) {
            val id = item.getAttribute("id")
            if (id.isBlank() || manifest.containsKey(id)) throw EpubException("Identificador de manifest EPUB vacío o duplicado: $id")
            val href = item.getAttribute("href")
            if (href.isBlank()) throw EpubException("Referencia manifest EPUB vacía: $id")
            hrefs[id] = href
            manifest[id] = EpubPath.resolve(packagePath, href) to item.getAttribute("media-type")
        }
        val spine = opf.elements(OPF, "itemref")
        if (spine.isEmpty()) throw EpubException("EPUB sin capítulos en spine")
        val chapters = spine.mapIndexed { index, item ->
            val id = item.getAttribute("idref")
            val (path, mediaType) = manifest[id] ?: throw EpubException("Spine EPUB referencia un identificador ausente: $id")
            if (mediaType != "application/xhtml+xml" && mediaType != "text/html") throw EpubException("Tipo de capítulo EPUB no compatible: $mediaType ($path)")
            val raw = read(path)
            val html = decodeXml(raw)
            val title = extractChapterTitle(html) ?: "Capítulo ${index + 1}"
            EpubChapter(id, title, hrefs.getValue(id), html, path, raw.size.toLong())
        }
        EpubBook(
            opf.elements(DC, "title").firstOrNull()?.textContent?.trim().orEmpty().ifBlank { epubFile.nameWithoutExtension },
            opf.elements(DC, "creator").firstOrNull()?.textContent?.trim().orEmpty(),
            chapters,
            EpubPublication(epubFile, packagePath, manifest.values.associate { it.first to it.second })
        )
    }

    /** Chapters are opaque HTML, not package XML. Never parse their DTDs or resolve entities.
     * This is only a display-label heuristic, not sanitization for the eventual WebView.
     */
    private fun extractChapterTitle(html: String): String? {
        // Bound heuristic work independently of the archive entry limit. Preserve full HTML below.
        val preview = html.take(64 * 1024)
        for (tag in listOf("title", "h1", "h2")) {
            val match = Regex("<$tag\\b[^>]*>(.*?)</$tag\\s*>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(preview)
            val title = match?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim()
            if (!title.isNullOrBlank()) return title
        }
        return null
    }

    private fun Document.elements(namespace: String, name: String): List<Element> {
        val nodes = getElementsByTagNameNS(namespace, name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun decodeXml(bytes: ByteArray): String {
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == '<'.code.toByte() -> Charsets.UTF_16BE
            bytes.size >= 4 && bytes[0] == '<'.code.toByte() && bytes[1] == 0.toByte() -> Charsets.UTF_16LE
            else -> {
                val header = String(bytes, 0, minOf(bytes.size, 256), Charsets.US_ASCII)
                val encoding = Regex("""^<\?xml\s+[^>]*encoding\s*=\s*["']([^"']+)["']""").find(header)?.groupValues?.get(1) ?: "UTF-8"
                require(encoding.uppercase() in setOf("UTF-8", "UTF8")) { "Codificación EPUB no compatible: $encoding" }
                Charset.forName(encoding)
            }
        }
        return charset.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    }

    private fun xml(bytes: ByteArray): Document = xml(decodeXml(bytes))

    private fun xml(text: String): Document {
        // Check after decoding so UTF-16 cannot bypass this boundary. No DTD (including internal subsets).
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            throw EpubException("DTD y entidades XML no compatibles en EPUB")
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Android's DOM factory does not support these SAX feature switches. They are
            // defense in depth where available, not the security boundary: decoded package
            // XML above rejects all DTD/entity declarations, and the resolver below fails closed.
            for (feature in listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities"
            )) {
                try {
                    setFeature(feature, false)
                } catch (_: javax.xml.parsers.ParserConfigurationException) {
                    // Unsupported by this provider; retain the precheck and rejecting resolver.
                }
            }
        }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw EpubException("Referencia XML externa bloqueada") }
            setErrorHandler(object : DefaultHandler() {
                override fun error(e: SAXParseException) { throw e }
                override fun fatalError(e: SAXParseException) { throw e }
            })
        }.parse(InputSource(StringReader(text)))
    }
}
