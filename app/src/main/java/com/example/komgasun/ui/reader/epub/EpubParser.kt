package com.example.komgasun.ui.reader.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val htmlContent: String
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

object EpubParser {

    fun parse(epubFile: File): EpubBook {
        ZipFile(epubFile).use { zip ->
            val opfPath = findOpfPath(zip) ?: "OEBPS/content.opf"
            val opfBaseDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

            val opfEntry = zip.getEntry(opfPath) ?: error("No se encontró el archivo OPF en $opfPath")
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }

            val (title, author, manifest, spine) = parseOpf(opfXml)

            val chapters = mutableListOf<EpubChapter>()
            for ((index, idRef) in spine.withIndex()) {
                val relHref = manifest[idRef] ?: continue
                val fullHref = opfBaseDir + relHref
                val chapterEntry = zip.getEntry(fullHref)
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(relHref.substringAfterLast('/')) }
                    ?: continue

                val rawHtml = zip.getInputStream(chapterEntry).bufferedReader().use { it.readText() }
                val chapterTitle = extractChapterTitle(rawHtml) ?: "Capítulo ${index + 1}"

                chapters.add(
                    EpubChapter(
                        id = idRef,
                        title = chapterTitle,
                        href = relHref,
                        htmlContent = rawHtml
                    )
                )
            }

            return EpubBook(
                title = title.ifBlank { epubFile.nameWithoutExtension },
                author = author,
                chapters = chapters
            )
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val xml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) {
                    return fullPath
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private data class OpfData(
        val title: String,
        val author: String,
        val manifest: Map<String, String>,
        val spine: List<String>
    )

    private fun parseOpf(xml: String): OpfData {
        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.lowercase()
                when {
                    tagName.endsWith("title") -> {
                        title = parser.nextText().trim()
                    }
                    tagName.endsWith("creator") -> {
                        author = parser.nextText().trim()
                    }
                    tagName == "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            manifest[id] = href
                        }
                    }
                    tagName == "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (!idref.isNullOrBlank()) {
                            spine.add(idref)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpfData(title, author, manifest, spine)
    }

    private fun extractChapterTitle(html: String): String? {
        val titleRegex = "<title[^>]*>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
        val h1Regex = "<h1[^>]*>(.*?)</h1>".toRegex(RegexOption.IGNORE_CASE)
        val h2Regex = "<h2[^>]*>(.*?)</h2>".toRegex(RegexOption.IGNORE_CASE)

        val match = titleRegex.find(html) ?: h1Regex.find(html) ?: h2Regex.find(html)
        return match?.groupValues?.get(1)?.replace(Regex("<.*?>"), "")?.trim()?.takeIf { it.isNotBlank() }
    }
}
