package com.sunvader.komgasun.ui.reader.epub

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubWebViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paginatedResourcesSecurityAndReflow() {
        val file = File.createTempFile("epub-webview-", ".epub")
        try {
            val html = """<html><head><link rel="stylesheet" href="style.css"><base href="https://evil.invalid/"><meta http-equiv="refresh" content="0;url=file:///etc/passwd"></head><body onload="window.pwned=1"><script>window.pwned=1</script><script src="https://evil.invalid/test.js"></script><iframe src="file:///etc/passwd"></iframe><img src="pixel.png">${"<p>Reading text for horizontal pages with reflow.</p>".repeat(120)}</body></html>"""
            val entries = mapOf(
                "META-INF/container.xml" to "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>".toByteArray(),
                "book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.html' media-type='text/html'/><item id='b' href='b.html' media-type='text/html'/><item id='css' href='style.css' media-type='text/css'/><item id='img' href='pixel.png' media-type='image/png'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>".toByteArray(),
                "a.html" to html.toByteArray(), "b.html" to html.toByteArray(),
                "style.css" to "p { color:rgb(12,34,56) }".toByteArray(),
                "pixel.png" to android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6RkAAAAASUVORK5CYII=", 0)
            )
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            } }
            val book = EpubParser.parse(file)
            val state = mutableStateOf(EpubReaderUiState(isLoading = false, isReady = true, epubBook = book, chapterGeneration = 1))
            val wide = mutableStateOf(false)
            lateinit var web: EpubWebView
            compose.setContent {
                Box(Modifier.requiredSize(if (wide.value) 360.dp else 240.dp, if (wide.value) 240.dp else 360.dp).testTag("reader")) {
                    AndroidView(factory = { context -> EpubWebView(context).also { view ->
                        web = view
                        view.onMeasured = { token, page, count ->
                            val s = state.value
                            if (token == s.chapterGeneration) state.value = s.copy(page = page, pageCount = count, proportion = EpubPagination.proportion(page, count))
                        }
                        view.onMove = { direction ->
                            val s = state.value
                            val p = EpubPagination.move(EpubPosition(s.currentChapterIndex, s.page), s.pageCount, 2, direction)
                            state.value = if (p.chapter == s.currentChapterIndex) s.copy(page = p.page, proportion = EpubPagination.proportion(p.page, s.pageCount))
                            else s.copy(currentChapterIndex = p.chapter, pageCount = 0, proportion = if (direction < 0) 1.0 else 0.0, chapterGeneration = s.chapterGeneration + 1)
                        }
                    } }, update = { it.render(state.value) }, onRelease = { it.dispose() })
                }
            }
            compose.waitUntil(10000) { state.value.pageCount > 1 }
            compose.onNodeWithTag("reader").performTouchInput { swipeLeft() }
            compose.waitUntil(5000) { state.value.page == 1 }
            compose.onNodeWithTag("reader").performTouchInput { swipeRight() }
            compose.waitUntil(5000) { state.value.page == 0 }
            compose.runOnIdle { state.value = state.value.copy(proportion = 1.0) }
            compose.waitUntil(5000) { state.value.page == state.value.pageCount - 1 }
            compose.onNodeWithTag("reader").performTouchInput { swipeLeft() }
            compose.waitUntil(5000) { state.value.currentChapterIndex == 1 && state.value.pageCount > 1 }
            assertEquals(0, state.value.page)
            compose.onNodeWithTag("reader").performTouchInput { swipeRight() }
            compose.waitUntil(5000) { state.value.currentChapterIndex == 0 && state.value.pageCount > 1 && state.value.page == state.value.pageCount - 1 }
            compose.runOnIdle { state.value = state.value.copy(proportion = 0.5, fontSizeSp = 26); wide.value = true }
            compose.waitUntil(5000) { state.value.pageCount > 1 && kotlin.math.abs(state.value.proportion - 0.5) < 0.1 }
            compose.onNodeWithTag("reader").performTouchInput { swipeUp() }
            assertEquals("0", js(web, "window.scrollY"))
            assertEquals("null", js(web, "window.pwned || null"))
            assertEquals("0", js(web, "document.querySelectorAll('script,base,meta[http-equiv=refresh],iframe').length"))
            assertEquals("true", js(web, "document.querySelector('img').complete && document.querySelector('img').naturalWidth>0"))
            assertEquals("\"rgb(12, 34, 56)\"", js(web, "getComputedStyle(document.querySelector('p')).color"))
            compose.runOnIdle {
                assertEquals(403, web.resource(Uri.parse("https://evil.invalid/style.css")).statusCode)
                assertEquals(403, web.resource(Uri.parse("file:///etc/passwd")).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/%73tyle.css")).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/a.html")).statusCode)
                assertEquals(200, web.resource(Uri.parse("https://epub.invalid/style.css")).statusCode)
            }
        } finally { file.delete() }
    }

    private fun js(view: EpubWebView, script: String): String {
        val result = AtomicReference<String?>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.set(it) } }
        compose.waitUntil(5000) { result.get() != null }
        return result.get()!!
    }
}
