package com.sunvader.komgasun.ui.reader.epub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** Live GPU text, covered during layout; no software WebView snapshots or page mesh. */
class EpubPageTurnView(context: Context) : FrameLayout(context) {
    val reader = EpubWebView(context)
    private val cover = View(context)
    private var state: EpubReaderUiState? = null
    private var source: EpubReaderUiState? = null
    private var target: EpubReaderUiState? = null
    private var committing = false
    private var waiting = false
    private var visualPending = false
    private var recoveryControls = false
    private val timeout = Runnable { resetTurn(recover = true) }
    private var revision = 0L
    private var disposed = false
    private var animator: ValueAnimator? = null
    private var tracker: VelocityTracker? = null
    private var startX = 0f
    private var startY = 0f
    private var direction = 0
    private var progress = 0f
    private var ignoreGesture = false
    var onMove: (Int) -> Unit = {}
    var onTap: () -> Unit = {}
    var onMeasured: (Long, Int, Int) -> Unit = { _, _, _ -> }

    private val readingInsets = EpubPageInsets(
        (40 * resources.displayMetrics.density).roundToInt(),
        (16 * resources.displayMetrics.density).roundToInt())
    private fun viewport() = readingInsets.viewport(width, height)

    init {
        addView(reader, LayoutParams(-1, -1).apply {
            setMargins(readingInsets.horizontal, readingInsets.vertical,
                readingInsets.horizontal, readingInsets.vertical)
        })
        addView(cover, LayoutParams(-1, -1))
        cover.visibility = GONE
        reader.onMeasured = { generation, page, count ->
            // A measurement of the source must not overwrite the parent's newly requested
            // destination before Compose has delivered its render update.
            if (source == null || (committing && state?.let { !samePosition(source!!, it) } == true)) {
                onMeasured(generation, page, count)
            }
        }
        reader.onPaginationReady = { generation, count ->
            val current = state
            val destination = target
            if (waiting && !visualPending && current != null && generation == current.chapterGeneration &&
                (destination == null || (current.currentChapterIndex == destination.currentChapterIndex &&
                    current.page == EpubPagination.page(destination.proportion, count)))) {
                val ticket = revision
                visualPending = true
                reader.postVisualStateCallback(ticket, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (ticket != revision || disposed) return
                        removeCallbacks(timeout)
                        waiting = false
                        visualPending = false
                        recoveryControls = false
                        animateCover(0f) { resetTurn() }
                    }
                })
                reader.invalidate()
            }
        }
    }

    private fun samePosition(a: EpubReaderUiState, b: EpubReaderUiState) =
        a.epubBook === b.epubBook && a.chapterGeneration == b.chapterGeneration &&
            a.currentChapterIndex == b.currentChapterIndex && a.proportion == b.proportion &&
            a.fontSizeSp == b.fontSizeSp && a.isDarkTheme == b.isDarkTheme &&
            a.useOriginalFonts == b.useOriginalFonts

    fun render(value: EpubReaderUiState) {
        if (disposed) return
        val previous = state
        val changed = previous == null || !samePosition(previous, value)
        if (changed) {
            // Navigation is committed only after fade-out. External jumps/reflows are also
            // covered before render can replace the document or move its CSS columns.
            stopAnimation()
            ++revision
            beginWait()
            cover.alpha = 1f
            cover.visibility = VISIBLE
            // Only the requested destination owns this turn. A TOC/slider/configuration
            // change supersedes it, so its readiness must not wait for the obsolete target.
            if (!committing || target?.let { !samePosition(it, value) } != false) {
                source = null; target = null; committing = false
            }
        }
        state = value
        val color = if (value.isDarkTheme) Color.rgb(18, 18, 18) else Color.WHITE
        setBackgroundColor(color)
        cover.setBackgroundColor(color)
        if (source == null || (committing && source?.let { !samePosition(it, value) } == true)) {
            reader.paginationPaused = false
        }
        reader.render(value)
    }

    fun turn(step: Int) {
        if (prepare(step)) settle(true)
    }

    private fun prepare(step: Int): Boolean {
        val current = state ?: return false
        if (disposed || source != null || waiting || animator != null || step == 0 ||
            width == 0 || height == 0 || !current.isReady || !reader.isSettled) return false
        val destination = EpubPageTurn.destination(current, current.epubBook?.chapters?.size ?: 0, step)
            ?: return false
        source = current
        target = destination
        direction = step
        progress = 0f
        return true
    }

    private fun animateCover(to: Float, done: () -> Unit) {
        stopAnimation()
        cover.visibility = VISIBLE
        animator = ValueAnimator.ofFloat(cover.alpha, to).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { cover.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    done()
                }
            })
            start()
        }
    }

    private fun settle(complete: Boolean) {
        if (!complete) { resetTurn(); return }
        reader.paginationPaused = true
        cover.alpha = 0f
        animateCover(1f) {
            committing = true
            beginWait()
            // The live WebView remains behind an opaque cover until settled + visually ready.
            onMove(direction)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if ((animator != null || committing || waiting) && !recoveryControls) return true
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreGesture = source != null
        if (ignoreGesture) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
                if (source != null) progress = (-direction * dx / reader.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                if (source != null) {
                    settle(EpubPageTurn.complete(progress,
                        -direction * (tracker?.xVelocity ?: 0f) / reader.width.coerceAtLeast(1)))
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
                resetTurn(); tracker?.recycle(); tracker = null
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (state == null) return
        stopAnimation()
        ++revision
        source = null; target = null; committing = false
        beginWait()
        cover.alpha = 1f; cover.visibility = VISIBLE
    }

    private fun stopAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun beginWait() {
        waiting = true; visualPending = false; recoveryControls = false
        removeCallbacks(timeout)
        postDelayed(timeout, 8000L)
    }

    private fun resetTurn(recover: Boolean = false) {
        ++revision
        stopAnimation()
        removeCallbacks(timeout)
        source = null; target = null; committing = false
        waiting = recover; visualPending = false; progress = 0f
        recoveryControls = recover
        ignoreGesture = false
        reader.paginationPaused = false
        // Failure never uncovers unready letters. After eight seconds center taps can
        // open controls for retry/navigation; a later valid layout still fades in normally.
        cover.visibility = if (recover) VISIBLE else GONE
        cover.alpha = if (recover) 1f else 0f
    }

    fun dispose() {
        disposed = true
        resetTurn()
        tracker?.recycle(); tracker = null
        reader.dispose()
    }
}
