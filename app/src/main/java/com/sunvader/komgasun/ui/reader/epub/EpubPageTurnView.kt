package com.sunvader.komgasun.ui.reader.epub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import kotlin.math.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/** Experimental paper surface. Only the live reader is connected to persisted progress. */
class EpubPageTurnView(context: Context) : FrameLayout(context) {
    val reader = EpubWebView(context)
    private val preview = EpubWebView(context)
    private val paper = Paper(context)
    private var state: EpubReaderUiState? = null
    private var source: EpubReaderUiState? = null
    private var target: EpubReaderUiState? = null
    private var revision = 0L
    private var ready = false
    private var committing = false
    private var released: Boolean? = null
    private var animator: ValueAnimator? = null
    private var tracker: VelocityTracker? = null
    private var startX = 0f
    private var startY = 0f
    private var direction = 0
    private var ignoreGesture = false
    var onMove: (Int) -> Unit = {}
    var onTap: () -> Unit = {}
    var onMeasured: (Long, Int, Int) -> Unit = { _, _, _ -> }
    private val timeout = Runnable { cancelTurn() }
    private var disposed = false
    private var loadRevision = 0L
    private var previewChapter: Int? = null
    private var previewBook: EpubBook? = null
    private var previewGeneration = 0L
    private val warmTimeout: Runnable = Runnable {
        ++loadRevision
        preview.paginationPaused = true
        preview.onPaginationReady = null
        previews.failed()
        if (source != null && !ready) cancelTurn()
    }
    private val previews: EpubTurnPreviews<Bitmap> = EpubTurnPreviews { step, done ->
        val current = state!!
        val destination = EpubPageTurn.destination(current, current.epubBook!!.chapters.size, step)!!
        val ticket = ++loadRevision
        removeCallbacks(warmTimeout)
        postDelayed(warmTimeout, 8000)
        preview.paginationPaused = false
        // Only a chapter/book switch needs a fresh shell, not every page or gesture.
        if (previewChapter != destination.currentChapterIndex || previewBook !== destination.epubBook) {
            previewChapter = destination.currentChapterIndex
            previewBook = destination.epubBook
            ++previewGeneration
        }
        preview.onPaginationReady = { _, _ ->
            if (ticket == loadRevision && !disposed) {
                preview.paginationPaused = true
                preview.onPaginationReady = null
                preview.postVisualStateCallback(ticket, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (ticket != loadRevision || disposed) return
                        preview.postVisualStateCallback(ticket + 100_000_000L, object : WebView.VisualStateCallback() {
                            override fun onComplete(secondId: Long) {
                                if (ticket != loadRevision || disposed) return
                                removeCallbacks(warmTimeout)
                                done(capture(preview))
                                showPrepared()
                            }
                        })
                        preview.invalidate()
                    }
                })
                preview.invalidate()
            }
        }
        preview.render(destination.copy(chapterGeneration = previewGeneration))
    }

    private val readingInsets = EpubPageInsets(
        (40 * resources.displayMetrics.density).roundToInt(),
        (16 * resources.displayMetrics.density).roundToInt())
    private fun viewport() = readingInsets.viewport(width, height)
    private fun textLayoutParams() = LayoutParams(-1, -1).apply {
        setMargins(readingInsets.horizontal, readingInsets.vertical,
            readingInsets.horizontal, readingInsets.vertical)
    }

    init {
        addView(preview, textLayoutParams())
        addView(reader, textLayoutParams())
        addView(paper, LayoutParams(-1, -1))
        paper.visibility = GONE
        preview.paginationPaused = true
        reader.onMeasured = { generation, page, count ->
            onMeasured(generation, page, count)
            post { warm() }
            val destination = target
            if (committing && destination != null && generation == state?.chapterGeneration &&
                state?.currentChapterIndex == destination.currentChapterIndex &&
                page == EpubPagination.page(destination.proportion, count)) {
                val ticket = revision
                reader.postVisualStateCallback(ticket, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) { if (revision == ticket) cancelTurn() }
                })
            }
        }
    }

    private fun identity(s: EpubReaderUiState) = listOf(s.epubBook, s.chapterGeneration,
        s.currentChapterIndex, s.proportion, s.page, s.pageCount,
        s.fontSizeSp, s.isDarkTheme, s.useOriginalFonts, width, height,
        resources.displayMetrics.densityDpi, readingInsets, viewport())

    private fun warm() {
        if (disposed) return
        val current = state
        val valid = current != null && current.isReady && current.pageCount > 0 && reader.width > 0 && reader.height > 0 &&
            !isLayoutRequested && !reader.isLayoutRequested && !preview.isLayoutRequested && reader.isSettled
        val key = if (valid) identity(current) else null
        // Invalidate pending visual callbacks before a new cache key starts loading.
        if (warmKey != key) {
            warmKey = key
            ++loadRevision
            removeCallbacks(warmTimeout)
            preview.paginationPaused = true
            preview.onPaginationReady = null
        }
        previews.update(key, if (valid) listOf(1, -1).filter {
            EpubPageTurn.destination(current, current.epubBook?.chapters?.size ?: 0, it) != null
        } else emptyList())
    }
    private var warmKey: Any? = null

    private fun showPrepared() {
        val current = source ?: return
        if (ready || committing) return
        val image = previews[direction] ?: return
        paper.front = capture(reader)
        paper.back = image
        ready = true
        paper.direction = direction
        paper.dark = current.isDarkTheme
        paper.visibility = VISIBLE
        paper.invalidate()
        released?.let { settle(it) }
    }

    fun render(value: EpubReaderUiState) {
        if (!committing && source?.let { identity(it) != identity(value) } == true) cancelTurn()
        if (committing && state?.let {
            it.epubBook !== value.epubBook || it.fontSizeSp != value.fontSizeSp ||
                it.isDarkTheme != value.isDarkTheme || it.useOriginalFonts != value.useOriginalFonts
        } == true) cancelTurn()
        state = value
        setBackgroundColor(if (value.isDarkTheme) Color.rgb(18, 18, 18) else Color.WHITE)
        reader.render(value)
        warm()
    }

    fun turn(step: Int) {
        if (source != null || step == 0) return
        if (prepare(step)) { released = true; if (ready) settle(true) }
    }

    private fun prepare(step: Int): Boolean {
        val current = state ?: return false
        if (width == 0 || height == 0 || !current.isReady) return false
        val destination = EpubPageTurn.destination(current, current.epubBook?.chapters?.size ?: 0, step)
            ?: return false
        source = current
        target = destination
        direction = step
        ready = false
        released = null
        ++revision
        postDelayed(timeout, 8000)
        warm()
        previews.prefer(step)
        showPrepared()
        return true
    }

    private fun capture(view: View): Bitmap = createBitmap(width, height).also {
        val canvas = Canvas(it)
        canvas.drawColor(if (state?.isDarkTheme == true) Color.rgb(18, 18, 18) else Color.WHITE)
        canvas.withTranslation(view.left.toFloat(), view.top.toFloat()) {
            clipRect(0, 0, view.width, view.height)
            view.draw(this)
        }
    }

    private fun settle(complete: Boolean) {
        removeCallbacks(timeout)
        animator = ValueAnimator.ofFloat(paper.progress, if (complete) 1f else 0f).apply {
            duration = (380 * abs((if (complete) 1f else 0f) - paper.progress)).toLong().coerceAtLeast(120)
            interpolator = DecelerateInterpolator()
            addUpdateListener { paper.progress = it.animatedValue as Float; paper.invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (source == null) return
                    if (complete) {
                        committing = true
                        postDelayed(timeout, 8000)
                        onMove(direction)
                    } else cancelTurn()
                }
            })
            start()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (animator?.isRunning == true || committing) return true
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreGesture = source != null
        if (ignoreGesture) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (source != null) return true
                startX = event.x; startY = event.y
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                val dx = event.x - startX
                if (source == null && abs(dx) > slop && abs(dx) > abs(event.y - startY)) {
                    prepare(if (dx < 0) 1 else -1)
                }
                if (source != null) {
                    paper.progress = (-direction * dx / reader.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    paper.invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                if (source != null) {
                    val complete = EpubPageTurn.complete(paper.progress,
                        -direction * (tracker?.xVelocity ?: 0f) / reader.width.coerceAtLeast(1))
                    released = complete
                    if (ready) settle(complete)
                    else if (!complete) cancelTurn()
                } else if (abs(event.x - startX) < 24 * resources.displayMetrics.density &&
                    abs(event.y - startY) < 24 * resources.displayMetrics.density) {
                    val step = EpubPageTurn.tapDirection(viewport().tapFraction(event.x))
                    if (step == 0) onTap() else turn(step)
                    performClick()
                }
                tracker?.recycle(); tracker = null
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreGesture = true
                cancelTurn(); tracker?.recycle(); tracker = null
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelTurn()
        warm()
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        warm()
    }

    private fun cancelTurn() {
        ++revision
        source = null; target = null; committing = false; ready = false; released = null
        animator?.removeAllListeners(); animator?.cancel(); animator = null
        removeCallbacks(timeout)
        paper.visibility = GONE
        paper.front = null; paper.back = null; paper.progress = 0f
    }
    fun dispose() {
        disposed = true
        ++loadRevision
        removeCallbacks(warmTimeout)
        previews.update(null, emptyList())
        cancelTurn()
        tracker?.recycle(); tracker = null
        reader.dispose(); preview.dispose()
    }

    /** One continuous, textured sheet; navigation and snapshot ownership remain outside it. */
    private class Paper(context: Context) : View(context) {
        var front: Bitmap? = null
        var back: Bitmap? = null
        var progress = 0f
        var direction = 1
        var dark = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadowMatrix = Matrix()
        private val shadowShader = LinearGradient(
            0f, 0f, 1f, 0f,
            Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )

        private val geometry = EpubCurlGeometry()

        override fun onDraw(canvas: Canvas) {
            val current = front ?: return
            val destination = back ?: return
            // Exact endpoint bitmaps avoid any residual mesh/shadow on completion or rollback.
            if (progress <= 0f || progress >= 1f) {
                canvas.drawBitmap(if (progress <= 0f) current else destination, 0f, 0f, null)
                return
            }
            val sheet = if (direction > 0) current else destination
            val underneath = if (direction > 0) destination else current
            canvas.drawBitmap(underneath, 0f, 0f, null)
            geometry.update(width.toFloat(), height.toFloat(), progress, direction, dark)
            val shadowWidth = width * (.015f + .065f * geometry.lift)
            shadowMatrix.setScale(shadowWidth, 1f)
            shadowMatrix.postTranslate(geometry.edge, 0f)
            shadowShader.setLocalMatrix(shadowMatrix)
            paint.shader = shadowShader
            paint.alpha = (255 * geometry.lift * if (dark) .6f else 1f).toInt()
            canvas.drawRect(geometry.edge, 0f, geometry.edge + shadowWidth, height.toFloat(), paint)
            paint.shader = null
            paint.alpha = 255
            canvas.drawBitmapMesh(sheet, EpubCurlGeometry.COLUMNS, EpubCurlGeometry.ROWS,
                geometry.vertices, 0, geometry.colors, 0, paint)
        }
    }
}
