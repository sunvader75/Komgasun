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
class EpubWebView(context: Context) : WebView(context) {
    var onMove: (Int) -> Unit = {}
    var onMeasured: (Long, Int, Int) -> Unit = { _, _, _ -> }
    private var book: EpubBook? = null
    private var chapterPath = ""
    private var token = Long.MIN_VALUE
    private var disposed = false
    private var installed = false
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
                if (disposed || installed || url != chapterUrl()) return
                val chapter = book?.chapters?.firstOrNull { it.resourcePath == chapterPath } ?: return
                val captured = token
                val rasterUrls = book?.publication?.mediaTypes.orEmpty()
                    .filterValues { it in setOf("image/png", "image/jpeg", "image/gif", "image/webp") }
                    .keys.map { path -> EpubHtml.ORIGIN + "/" + path.split('/').joinToString("/") { Uri.encode(it) } }
                evaluateJavascript(EpubHtml.install(JSONObject.quote(chapter.htmlContent), JSONArray(rasterUrls).toString())) {
                    if (!disposed && token == captured && it == "true") {
                        installed = true
                        measurePage()
                    }
                }
            }
        }
        post(poll)
    }

    private fun chapterUrl() = EpubHtml.ORIGIN + "/" + chapterPath.split('/').joinToString("/") { Uri.encode(it) } + "?generation=$token"

    /** URLs must be exactly canonical, not just share a host suffix or decoded prefix. */
    fun resource(uri: Uri, method: String = "GET", mainFrame: Boolean = false): WebResourceResponse {
        fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        if (disposed || mainFrame || method != "GET" || uri.scheme != "https" || uri.encodedAuthority != "epub.invalid" ||
            uri.query != null || uri.fragment != null) return denied()
        val publication = book?.publication ?: return denied()
        return try {
            val encoded = uri.encodedPath?.removePrefix("/") ?: return denied()
            val path = publication.resolve("", encoded)
            val canonical = "/" + path.split('/').joinToString("/") { Uri.encode(it) }
            if (uri.encodedPath != canonical || path !in publication.mediaTypes) return denied()
            val type = publication.mediaTypes.getValue(path)
            // HTML, SVG, JavaScript and generic binary are never served as subresources.
            if (type !in setOf("text/css", "image/png", "image/jpeg", "image/gif", "image/webp", "font/woff", "font/woff2", "font/ttf", "application/vnd.ms-opentype", "application/font-woff")) return denied()
            val data = publication.readResource("", encoded)
            WebResourceResponse(type, if (type == "text/css") "UTF-8" else null,
                200, "OK", mapOf("X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store"), ByteArrayInputStream(data.bytes))
        } catch (_: Exception) { denied() }
    }

    fun render(state: EpubReaderUiState) {
        if (disposed) return
        val chapter = state.epubBook?.chapters?.getOrNull(state.currentChapterIndex) ?: return
        requestedFraction = state.proportion.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        font = state.fontSizeSp.coerceIn(12, 32)
        dark = state.isDarkTheme
        if (token != state.chapterGeneration || book !== state.epubBook) {
            token = state.chapterGeneration
            evaluation++
            callbackPending = false
            installed = false
            book = state.epubBook
            chapterPath = chapter.resourcePath ?: return
            stopLoading()
            loadDataWithBaseURL(chapterUrl(), EpubHtml.shell, "text/html", "UTF-8", null)
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
        ++evaluation
        removeCallbacks(poll)
        onMove = {}
        onMeasured = { _, _, _ -> }
        stopLoading()
        webViewClient = WebViewClient()
        destroy()
    }
}
