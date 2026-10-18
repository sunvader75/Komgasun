package com.sunvader.komgasun.ui.reader.epub

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.*
import java.io.File

/** One attached renderer; derived counts are reused across reader openings and color themes. */
internal class EpubBookPaginator(
    context: Context,
    cacheDirectory: File = File(context.filesDir, "epub_pagination")
) : EpubWebView(context) {
    var onPageMap: (EpubBookPageMap?, Boolean) -> Unit = { _, _ -> }
    var onProgress: (Int, Int) -> Unit = { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = EpubPaginationCache(cacheDirectory)
    private var cacheJob: Job? = null
    private var source: EpubBook? = null
    private var fingerprint: String? = null
    private var cacheKey: String? = null
    private var fontSize = 18
    private var generation = 0L
    private var chapter = 0
    private val counts = mutableListOf<Int>()
    private var closed = false
    private val timeout = Runnable {
        paginationPaused = true
        onPageMap(null, true)
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
        onPaginationReady = { token, count ->
            if (!closed && !paginationPaused && token == generation) {
                removeCallbacks(timeout)
                counts.add(count)
                chapter++
                onProgress(chapter, source?.chapters?.size ?: 0)
                if (chapter == source?.chapters?.size) {
                    paginationPaused = true
                    val pages = EpubBookPageMap(counts)
                    val key = cacheKey
                    val revision = generation
                    cacheJob = scope.launch {
                        if (key != null) withContext(Dispatchers.IO) {
                            runCatching { cache.save(key, pages) }
                                .onFailure { android.util.Log.w("EpubPagination", "Could not save derived page counts", it) }
                        }
                        if (!closed && revision == generation) onPageMap(pages, false)
                    }
                } else measureChapter()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun paginate(book: EpubBook, font: Int, dark: Boolean) {
        // Color does not change pagination. Measure in a fixed theme, including mid-calculation.
        if (closed || (source === book && fontSize == font)) return
        if (source !== book) fingerprint = null
        source = book
        fontSize = font
        restart()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!closed && (w != oldw || h != oldh)) restart()
    }

    private fun restart() {
        removeCallbacks(timeout)
        cacheJob?.cancel()
        val revision = ++generation
        counts.clear()
        chapter = 0
        cacheKey = null
        paginationPaused = true
        onPageMap(null, false)
        val book = source ?: return
        onProgress(0, book.chapters.size)
        if (width <= 0 || height <= 0 || book.chapters.isEmpty()) return
        val viewportWidth = width
        val viewportHeight = height
        val layoutFont = fontSize
        val knownFingerprint = fingerprint
        val metrics = resources.displayMetrics
        val engine = if (android.os.Build.VERSION.SDK_INT >= 26)
            WebView.getCurrentWebViewPackage()?.versionName ?: settings.userAgentString
        else settings.userAgentString
        val renderer = "${metrics.densityDpi}:${metrics.scaledDensity}:${android.os.Build.VERSION.SDK_INT}:$engine:" +
            EpubPaginationCache.hash(EpubHtml.shell + EpubHtml.install("\"\""))
        cacheJob = scope.launch {
            try {
                val (identity, key, cached) = withContext(Dispatchers.IO) {
                    val identity = knownFingerprint ?: book.publication?.paginationFingerprint()
                        ?: EpubPaginationCache.hash(book.chapters.joinToString("\u0000") { "${it.resourcePath}\u0000${it.htmlContent}" })
                    val key = EpubPaginationCache.key(identity, viewportWidth, viewportHeight, layoutFont, renderer)
                    Triple(identity, key, cache.load(key, book.chapters.size))
                }
                if (closed || revision != generation) return@launch
                fingerprint = identity
                cacheKey = key
                if (cached != null) {
                    onProgress(book.chapters.size, book.chapters.size)
                    onPageMap(cached, false)
                } else measureChapter()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && revision == generation) onPageMap(null, true)
            }
        }
    }

    private fun measureChapter() {
        val book = source ?: return
        paginationPaused = false
        render(EpubReaderUiState(isLoading = false, isReady = true, epubBook = book,
            currentChapterIndex = chapter, chapterGeneration = ++generation,
            fontSizeSp = fontSize, isDarkTheme = true))
        postDelayed(timeout, 20_000)
    }

    fun release() {
        closed = true
        cacheJob?.cancel()
        scope.cancel()
        removeCallbacks(timeout)
        onPageMap = { _, _ -> }
        onProgress = { _, _ -> }
        dispose()
    }
}
