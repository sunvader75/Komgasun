package com.sunvader.komgasun.ui.reader.epub

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.abs

/** Read-only, deny-by-default archive transport; no native JavaScript interface is registered. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
open class EpubWebView(context: Context) : WebView(context) {
    var onMove: (Int) -> Unit = {}
    var onMeasured: (Long, Int, Int) -> Unit = { _, _, _ -> }
    var onImageDiagnostics: (String) -> Unit = {}
    private var recovery = EpubImageRecovery(Long.MIN_VALUE)
    private val imageWorker = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(256), java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
    )
    private var imagePending = false
    private val imageResults = mutableMapOf<Int, String>()
    private val imageSources = mutableMapOf<Int, String>()
    private val transports = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var book: EpubBook? = null
    private var chapterPath = ""
    @Volatile private var token = Long.MIN_VALUE
    private var disposed = false
    private var installed = false
    private val documents = EpubDocumentLifecycle()
    @Volatile private var shellUrl = ""
    private var requestedFraction = 0.0
    private var font = 18
    private var dark = true
    private var startX = 0f
    private var startY = 0f
    private var evaluation = 0L
    private var callbackPending = false
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            measurePage()
            inspectImages()
            postDelayed(this, 350)
        }
    }

    init {
        settings.apply {
            javaScriptEnabled = true // App evaluateJavascript only; publication scripts blocked by CSP + inert import.
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            databaseEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true
            defaultTextEncodingName = "UTF-8"
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            @Deprecated("Legacy WebView callback")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                resource(request.url, request.method, request.isForMainFrame)
            override fun onPageFinished(view: WebView?, url: String?) {
                if (disposed) return
                if (url != shellUrl) {
                    if (url?.startsWith("chrome-error:") == true || url?.startsWith("about:") == true) documentError()
                    return
                }
                val chapter = book?.chapters?.firstOrNull { it.resourcePath == chapterPath } ?: return
                val ticket = documents.reserve(url) ?: return
                val captured = token
                val expected = shellUrl
                val rasterUrls = book?.publication?.mediaTypes.orEmpty()
                    .filterValues { it in setOf("image/png", "image/jpeg", "image/gif", "image/webp") }
                    .keys.map { path -> EpubHtml.ORIGIN + "/" + path.split('/').joinToString("/") { Uri.encode(it) } }
                // The location/base check and install run atomically in the same JS evaluation.
                evaluateJavascript(EpubHtml.install(JSONObject.quote(chapter.htmlContent), JSONArray(rasterUrls).toString(), JSONObject.quote(expected), JSONObject.quote(expected))) {
                    if (disposed || token != captured || !documents.isCurrent(ticket)) return@evaluateJavascript
                    if (documents.complete(ticket, it == "true")) {
                        installed = true
                        measurePage()
                    } else documentError()
                }
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && request.url.toString() == shellUrl) documentError()
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                installed = false
                evaluation++
                callbackPending = false
                imagePending = false
                if (url == shellUrl) documents.begin(shellUrl) else documentError()
            }
        }
        post(poll)
    }

    private fun documentError() {
        documents.begin(shellUrl)
        installed = false
        evaluation++
        callbackPending = false
        imagePending = false
        onImageDiagnostics("DOCUMENTO_NO_FIABLE: no se pudo abrir el documento local. Cierra y vuelve a abrir el capítulo; si persiste, comparte este diagnóstico.")
    }

    private fun chapterUrl() = EpubHtml.ORIGIN + "/" + chapterPath.split('/').joinToString("/") { Uri.encode(it) } + "?generation=$token"

    /** URLs must be exactly canonical, not just share a host suffix or decoded prefix. */
    open fun resource(uri: Uri, method: String = "GET", mainFrame: Boolean = false): WebResourceResponse {
        val captured = token
        fun record(value: String) { if (captured == token && transports.size < 256) transports[uri.toString()] = value }
        fun denied(reason: String = "POLICY") : WebResourceResponse {
            record("HTTP403 reason=$reason mime=unknown bytes=0")
            return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        if (disposed || method != "GET") return denied("REQUEST")
        if (mainFrame) {
            // Only the exact app-initiated current shell is served; no publication HTML or network fallback.
            if (uri.toString() != shellUrl || shellUrl.isEmpty()) return denied("MAIN_FRAME")
            return WebResourceResponse("text/html", "UTF-8", 200, "OK",
                mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
                ByteArrayInputStream(EpubHtml.shell.toByteArray(Charsets.UTF_8)))
        }
        if (uri.scheme != "https" || uri.encodedAuthority != "epub.invalid") return denied("ORIGIN")
        if (uri.query != null || uri.fragment != null) return denied("QUERY_OR_FRAGMENT")
        val publication = book?.publication ?: return denied()
        return try {
            val path = publication.canonicalPath(uri.toString())
            val type = publication.mediaTypes.getValue(path)
            // HTML, SVG, JavaScript and generic binary are never served as subresources.
            if (type !in setOf("text/css", "image/png", "image/jpeg", "image/gif", "image/webp", "font/woff", "font/woff2", "font/ttf", "application/vnd.ms-opentype", "application/font-woff")) return denied("MIME")
            val data = publication.readCanonicalResource(uri.toString())
            record("HTTP200 reason=NONE mime=$type bytes=${data.bytes.size}")
            WebResourceResponse(type, if (type == "text/css") "UTF-8" else null,
                200, "OK", mapOf("X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store"), ByteArrayInputStream(data.bytes))
        } catch (e: EpubResourceFailure) { denied(e.category.name) }
        catch (_: Exception) { denied("UNEXPECTED_CLASS") }
    }

    fun render(state: EpubReaderUiState) {
        if (disposed) return
        val chapter = state.epubBook?.chapters?.getOrNull(state.currentChapterIndex) ?: return
        requestedFraction = state.proportion.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        font = state.fontSizeSp.coerceIn(12, 32)
        dark = state.isDarkTheme
        if (token != state.chapterGeneration || book !== state.epubBook) {
            token = state.chapterGeneration
            recovery = EpubImageRecovery(token)
            imageWorker.queue.clear()
            imagePending = false
            imageResults.clear()
            imageSources.clear()
            transports.clear()
            onImageDiagnostics("")
            evaluation++
            callbackPending = false
            installed = false
            book = state.epubBook
            chapterPath = chapter.resourcePath ?: return
            stopLoading()
            shellUrl = chapterUrl()
            documents.begin(shellUrl)
            loadUrl(shellUrl)
        } else measurePage()
    }

    private fun measurePage() {
        if (!installed || disposed || width == 0 || height == 0 || callbackPending) return
        callbackPending = true
        val captured = token
        val serial = ++evaluation
        val fraction = requestedFraction
        val capturedFont = font
        val capturedDark = dark
        evaluateJavascript("window.__epub.apply($fraction,$font,$dark)") { result ->
            if (disposed || token != captured || serial != evaluation) return@evaluateJavascript
            callbackPending = false
            try {
                val values = JSONArray(result)
                if (values.length() != 2) return@evaluateJavascript
                val page = values.getDouble(0)
                val count = values.getDouble(1)
                if (!page.isFinite() || !count.isFinite() || page % 1.0 != 0.0 || count % 1.0 != 0.0 ||
                    count !in 1.0..100000.0 || page < 0 || page >= count) return@evaluateJavascript
                // A newer state must not be overwritten by an older async layout callback.
                if (fraction != requestedFraction || capturedFont != font || capturedDark != dark) { measurePage(); return@evaluateJavascript }
                onMeasured(captured, page.toInt(), count.toInt())
            } catch (_: Exception) { /* Ignore malformed/stale callbacks, never persist them. */ }
        }
    }

    private fun inspectImages() {
        if (!installed || disposed || imagePending) return
        imagePending = true
        val captured = token
        val publication = book?.publication
        val policy = recovery
        evaluateJavascript("window.__epub.images()") { raw ->
            if (disposed || captured != token || recovery !== policy) return@evaluateJavascript
            imagePending = false
            try {
                val rows = JSONArray(raw)
                if (rows.length() > 256) return@evaluateJavascript
                val reports = StringBuilder()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONArray(i)
                    if (row.length() != 12 || row.getDouble(0) != i.toDouble()) continue
                    val dimensions = (3..6).map { row.getDouble(it) }
                    if (dimensions.any { !it.isFinite() || it % 1.0 != 0.0 }) continue
                    val complete = row.getBoolean(2)
                    val w = dimensions[0].toInt(); val h = dimensions[1].toInt()
                    val rw = dimensions[2].toInt(); val rh = dimensions[3].toInt()
                    val age = row.getDouble(7)
                    if (w !in 0..100000 || h !in 0..100000 || rw !in 0..100000 || rh !in 0..100000 || !age.isFinite()) continue
                    val failed = complete && w == 0
                    if (age < 1500 || (!failed && w > 0 && i !in imageResults)) continue
                    val source = imageSources.getOrPut(i) { row.getString(1) }
                    reports.append(EpubImageDiagnostics(i, complete, w, h, rw, rh,
                        transports[source] ?: "UNKNOWN (sin callback observado)", imageResults[i] ?: "NO_ATTEMPT", row.getString(8), row.getString(9),
                        row.getInt(10), row.getString(11)).report())
                    if (publication == null || !policy.claim(captured, i)) continue
                    imageResults[i] = "VALIDATING"
                    imageWorker.execute {
                        if (captured != token || Thread.currentThread().isInterrupted) return@execute
                        var outcome = "REJECTED"
                        var payload: String? = null
                        try {
                            val path = publication.canonicalPath(source)
                            val mime = publication.mediaTypes[path]
                            if (mime in setOf("image/png", "image/jpeg", "image/gif", "image/webp") && !Thread.currentThread().isInterrupted) {
                                val bytes = publication.readCanonicalResource(source).bytes
                                if (EpubImageRecovery.validRaster(mime!!, bytes) && policy.reserve(captured, bytes.size)) {
                                    payload = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                } else outcome = "SIGNATURE_OR_BUDGET"
                            }
                        } catch (e: EpubResourceFailure) { outcome = e.category.name }
                        catch (_: Exception) { outcome = "UNEXPECTED_CLASS" }
                        val data = payload
                        val result = outcome
                        post {
                            if (disposed || captured != token || recovery !== policy) return@post
                            if (data == null) imageResults[i] = result
                            else {
                                imageResults[i] = "ASSIGNED (decode no confirmado)"
                                evaluateJavascript("window.__epub.recover($i,${JSONObject.quote(data)})") { accepted ->
                                    if (!disposed && token == captured && recovery === policy && accepted != "true") imageResults[i] = "DOM_REJECTED"
                                }
                            }
                        }
                    }
                }
                onImageDiagnostics(reports.toString())
            } catch (_: Exception) { /* Malformed callbacks never authorize reads or diagnostics. */ }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > 48 * resources.displayMetrics.density && abs(dx) > abs(dy)) onMove(if (dx < 0) 1 else -1)
                performClick()
            }
        }
        return true // Never pass vertical dragging or publication link activation to WebView.
    }
    override fun performClick(): Boolean = super.performClick()

    fun dispose() {
        disposed = true
        imageWorker.shutdownNow()
        onImageDiagnostics = {}
        ++evaluation
        removeCallbacks(poll)
        onMove = {}
        onMeasured = { _, _, _ -> }
        stopLoading()
        webViewClient = WebViewClient()
        destroy()
    }
}
