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

    @Test fun paginatedResourcesSecurityAndReflow() = exerciseReader(false)
    @Test fun rejectedNormalRasterRecoversWithoutNetwork() = exerciseReader(true)

    private fun exerciseReader(induceFailure: Boolean) {
        val file = File.createTempFile("epub-webview-", ".epub")
        try {
            fun cover(source: String = "pixel.png", extra: String = "", attributes: String = "") =
                """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="100%" height="100%" viewBox="0 0 1264 1920" preserveAspectRatio="none" $attributes><image width="1264" height="1920" xlink:href="$source"/>$extra</svg>"""
            val covers = cover() + listOf("https://evil.invalid/a.png", "data:image/png;base64,AA==", "file:///a.png", "content://a", "../pixel.png", "%70ixel.png", "missing.png", "pixel.png?x=1", "pixel.png#x").joinToString("") { cover(it) } +
                cover(extra = "<script>window.pwned=1</script>") + cover(extra = "<foreignObject><img src='pixel.png'/></foreignObject>") +
                cover(extra = "<animate/>") + cover(attributes = "onload='window.pwned=1'") + cover(attributes = "style='display:none'")
            val html = """<html><head><link rel="stylesheet" href="style.css"><base href="https://evil.invalid/"><meta http-equiv="refresh" content="0;url=file:///etc/passwd"></head><body onload="window.pwned=1"><script>window.pwned=1</script><script src="https://evil.invalid/test.js"></script><iframe src="file:///etc/passwd"></iframe><img src="pixel.png">$covers${"<p>Reading text for horizontal pages with reflow.</p>".repeat(120)}</body></html>"""
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
                    AndroidView(factory = { context -> object : EpubWebView(context) {
                        override fun resource(uri: Uri, method: String, mainFrame: Boolean): android.webkit.WebResourceResponse {
                            if (induceFailure && uri.path == "/pixel.png") return android.webkit.WebResourceResponse(
                                "text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
                            return super.resource(uri, method, mainFrame)
                        }
                    }.also { view ->
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
            assertEquals("true", js(web, "location.origin==='https://epub.invalid' && location.href===document.baseURI && location.search==='?generation='+" + state.value.chapterGeneration + " && !location.href.startsWith('chrome-error:')"))
            // Duplicate completion must preserve the current API and DOM, including while pending.
            assertEquals("true", js(web, "(window.__savedApi=window.__epub,window.__savedBody=document.body.firstChild,true)"))
            compose.runOnIdle { repeat(8) { web.webViewClient.onPageFinished(web, web.url) } }
            assertEquals("true", js(web, "window.__savedApi===window.__epub && window.__savedBody===document.body.firstChild"))
            assertEquals("false", js(web, EpubHtml.install("\"<p>must not replace</p>\"", "[]", "null", "\"chrome-error://chromewebdata/\"")))
            assertEquals("true", js(web, "window.__savedApi===window.__epub && window.__savedBody===document.body.firstChild"))
            assertEquals("0", js(web, "window.scrollY"))
            assertEquals("null", js(web, "window.pwned || null"))
            assertEquals("0", js(web, "document.querySelectorAll('script,base,meta[http-equiv=refresh],iframe').length"))
            val decoded = AtomicReference(false)
            compose.runOnIdle {
                web.evaluateJavascript("(function wait(){if(Array.from(document.images).every(i=>i.complete&&i.naturalWidth>0))window.__decoded=true;else setTimeout(wait,50);})()") {}
            }
            compose.waitUntil(10000) {
                web.post { web.evaluateJavascript("window.__decoded===true") { decoded.set(it == "true") } }
                decoded.get()
            }
            assertEquals("true", js(web, "document.querySelector('img').complete && document.querySelector('img').naturalWidth>0"))
            assertEquals("2", js(web, "document.querySelectorAll('img').length"))
            assertEquals("true", js(web, "Array.from(document.querySelectorAll('img')).every(i=>i.complete && i.naturalWidth>0)"))
            if (induceFailure) {
                assertEquals("true", js(web, "Array.from(document.images).every(i=>i.src.startsWith('blob:https://epub.invalid/'))"))
                assertEquals("false", js(web, "window.__epub.recover(0,'data:image/svg+xml;base64,PHN2Zy8+')"))
                assertEquals("false", js(web, "window.__epub.recover(0,'data:image/png;base64,AA==')"))
            } else {
                assertEquals("true", js(web, "document.querySelectorAll('img')[0].getAttribute('src')==='https://epub.invalid/pixel.png'"))
                assertEquals("true", js(web, "document.querySelectorAll('img')[1].attributes.length===1 && document.querySelectorAll('img')[1].src==='https://epub.invalid/pixel.png'"))
            }
            // Recovery must not replace the immutable validated source with the live Blob URL.
            assertEquals("true", js(web, "window.__epub.images().every(r=>r[1]==='https://epub.invalid/pixel.png' && r[8]==='ATTR' && r[10]===1 && r[11]==='APP_CHAPTER')"))
            assertEquals("true", js(web, "(function(){document.images[0].removeAttribute('src');return window.__epub.images()[0][1]==='https://epub.invalid/pixel.png';})()"))
            assertEquals("0", js(web, "document.querySelectorAll('svg,foreignObject,animate').length"))
            assertEquals("\"rgb(12, 34, 56)\"",  js(web, "getComputedStyle(document.querySelector('p')).color"))
            compose.runOnIdle {
                assertEquals(200, web.resource(Uri.parse(web.url!!), mainFrame = true).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/a.html?generation=-1"), mainFrame = true).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://evil.invalid/style.css")).statusCode)
                assertEquals(403, web.resource(Uri.parse("file:///etc/passwd")).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/%73tyle.css")).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/a.html")).statusCode)
                assertEquals(200, web.resource(Uri.parse("https://epub.invalid/style.css")).statusCode)
                val raster = web.resource(Uri.parse("https://epub.invalid/pixel.png"))
                assertEquals(if (induceFailure) 403 else 200, raster.statusCode)
                if (!induceFailure) {
                    assertEquals("image/png", raster.mimeType)
                    assertTrue(raster.data.use { it.readBytes() }.contentEquals(entries.getValue("pixel.png")))
                }
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/missing.png")).statusCode)
                assertEquals(403, web.resource(Uri.parse("https://epub.invalid/pixel.png?x=1")).statusCode)
            }
        } finally { file.delete() }
    }

    @Test fun explicitBaseRetainsRelativeAndCategorizesEmptySources() {
        lateinit var web: EpubWebView
        compose.setContent { AndroidView(factory = { EpubWebView(it).also { v -> web = v; v.loadDataWithBaseURL(EpubHtml.ORIGIN + "/shell", EpubHtml.shell, "text/html", "UTF-8", null) } }, onRelease = { it.dispose() }) }
        compose.waitUntil(5000) { web.progress == 100 }
        val html = "<img><img src=''><img src='https://outside.invalid/a'><img src='../pixel.png'>"
        assertEquals("true", js(web, EpubHtml.install(org.json.JSONObject.quote(html), "[\"https://epub.invalid/pixel.png\"]", "\"https://epub.invalid/chapters/a.html\"")))
        assertEquals("true", js(web, "(function(){const r=window.__epub.images();return r[0][1]==='' && r[0][8]==='MISSING_ATTR' && r[1][1]==='' && r[1][8]==='EMPTY_ATTR' && r[2][1]==='' && r[3][1]==='https://epub.invalid/pixel.png' && document.images[3].getAttribute('src')===r[3][1] && r.every(x=>x[11]==='APP_CHAPTER' && x[10]===1);})()"))
    }

    @Test fun localOpenTypeFontsAreServedWithoutOpeningOtherResources() {
        val file = File.createTempFile("epub-font-", ".epub")
        try {
            val font = byteArrayOf(0x4f, 0x54, 0x54, 0x4f)
            val entries = mapOf(
                "META-INF/container.xml" to "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>".toByteArray(),
                "book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.html' media-type='text/html'/><item id='font' href='font.otf' media-type='font/otf'/><item id='legacy' href='legacy.otf' media-type='application/vnd.ms-opentype'/><item id='js' href='bad.js' media-type='text/javascript'/></manifest><spine><itemref idref='a'/></spine></package>".toByteArray(),
                "a.html" to "<p>Text</p>".toByteArray(), "font.otf" to font,
                "legacy.otf" to font, "bad.js" to "alert(1)".toByteArray()
            )
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            } }
            val book = EpubParser.parse(file)
            lateinit var web: EpubWebView
            compose.setContent { AndroidView(modifier = Modifier.requiredSize(320.dp, 480.dp), factory = { EpubWebView(it).also { v -> web = v; v.render(EpubReaderUiState(epubBook = book, chapterGeneration = 1)) } }, onRelease = { it.dispose() }) }
            compose.runOnIdle {
                for ((path, mime) in listOf("font.otf" to "font/otf", "legacy.otf" to "application/vnd.ms-opentype")) {
                    val response = web.resource(Uri.parse("https://epub.invalid/$path"))
                    assertEquals(200, response.statusCode)
                    assertEquals(mime, response.mimeType)
                    assertTrue(font.contentEquals(response.data.use { it.readBytes() }))
                }
                for (url in listOf("https://evil.invalid/font.otf", "file:///font.otf", "https://epub.invalid/font.otf?q=1", "https://epub.invalid/missing.otf", "https://epub.invalid/bad.js", "https://epub.invalid/a.html", "https://epub.invalid/../font.otf")) {
                    val response = web.resource(Uri.parse(url))
                    assertEquals(url, 403, response.statusCode)
                    assertEquals("text/plain", response.mimeType)
                    assertEquals(0, response.data.use { it.readBytes() }.size)
                }
                val post = web.resource(Uri.parse("https://epub.invalid/font.otf"), method = "POST")
                assertEquals(403, post.statusCode)
                assertEquals(0, post.data.use { it.readBytes() }.size)
            }
        } finally { file.delete() }
    }

    @Test fun originalTypographyPreservesRelativeSizeAndLeading() {
        lateinit var web: EpubWebView
        compose.setContent { AndroidView(modifier = Modifier.requiredSize(320.dp, 480.dp), factory = { EpubWebView(it).also { v -> web = v; v.loadDataWithBaseURL(EpubHtml.ORIGIN + "/shell", EpubHtml.shell, "text/html", "UTF-8", null) } }, onRelease = { it.dispose() }) }
        compose.waitUntil(5000) { var loaded = false; compose.runOnIdle { loaded = web.progress == 100 }; loaded }
        val html = "<style>.notice{font-family:monospace;font-size:0.87em;line-height:1.2}</style><p>Main text</p><p class='notice'>Notice</p>"
        assertEquals("true", js(web, EpubHtml.install(org.json.JSONObject.quote(html))))
        assertEquals("true", js(web, "(function(){window.__epub.apply(0,20,true,true);const p=document.querySelectorAll('p'),a=getComputedStyle(p[0]),b=getComputedStyle(p[1]);return a.fontFamily==='serif' && b.fontFamily==='monospace' && Math.abs(parseFloat(b.fontSize)-17.4)<0.1 && Math.abs(parseFloat(b.lineHeight)-20.88)<0.2;})()"))
        assertEquals("true", js(web, "(function(){window.__epub.apply(0,20,true,false);const p=document.querySelectorAll('p');return Array.from(p).every(e=>getComputedStyle(e).fontSize==='20px') && getComputedStyle(p[0]).fontFamily.includes('Literata');})()"))
    }

    @Test fun originalFontsCanBeEnabledAndDisabled() {
        lateinit var web: EpubWebView
        compose.setContent { AndroidView(modifier = Modifier.requiredSize(320.dp, 480.dp), factory = { EpubWebView(it).also { v -> web = v; v.loadDataWithBaseURL(EpubHtml.ORIGIN + "/shell", EpubHtml.shell, "text/html", "UTF-8", null) } }, onRelease = { it.dispose() }) }
        compose.waitUntil(5000) {
            var loaded = false
            compose.runOnIdle { loaded = web.progress == 100 }
            loaded
        }
        assertEquals("true", js(web, EpubHtml.install(org.json.JSONObject.quote("<body style='font-family: monospace'><p>Publisher text</p></body>"))))
        assertEquals("true", js(web, "(function(){window.__epub.apply(0,18,true);return getComputedStyle(document.querySelector('p')).fontFamily.includes('Literata');})()"))
        assertEquals("true", js(web, "(function(){window.__epub.apply(0,18,true,true);return getComputedStyle(document.querySelector('p')).fontFamily==='monospace';})()"))
        assertEquals("true", js(web, "(function(){window.__epub.apply(0,18,true,false);return getComputedStyle(document.querySelector('p')).fontFamily.includes('Literata');})()"))
    }

    @Test fun blankDocumentCannotReceivePublication() {
        lateinit var web: EpubWebView
        compose.setContent { AndroidView(factory = { EpubWebView(it).also { v -> web = v } }, onRelease = { it.dispose() }) }
        assertEquals("false", js(web, EpubHtml.install("\"<p>Rejected publication</p>\"", "[]", "\"https://epub.invalid/a?generation=1\"", "\"https://epub.invalid/a?generation=1\"")))
        assertEquals("true", js(web, "!Object.prototype.hasOwnProperty.call(window,'__epub') && !document.querySelector('p')"))
    }

    @Test fun canonicalEncodingMatchesAndroid() {
        val path = "dir/a +!'()~*_-.,;:@&=é中.jpg"
        val pub = EpubPublication(File("unused"), "book.opf", mapOf(path to "image/jpeg"))
        val source = EpubHtml.ORIGIN + "/" + path.split('/').joinToString("/") { Uri.encode(it) }
        assertEquals("https://epub.invalid/dir/a%20%2B!'()~*_-.%2C%3B%3A%40%26%3D%C3%A9%E4%B8%AD.jpg", source)
        assertEquals(path, pub.canonicalPath(source))
    }

    private fun js(view: EpubWebView, script: String): String {
        val result = AtomicReference<String?>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.set(it) } }
        compose.waitUntil(5000) { result.get() != null }
        return result.get()!!
    }
}
