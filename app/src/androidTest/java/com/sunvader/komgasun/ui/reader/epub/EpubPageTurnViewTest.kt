package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubPageTurnViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullPaperKeepsInsetTextAndOpaqueMarginsAcrossThemesAndResize() {
        lateinit var sheet: EpubPageTurnView
        compose.setContent {
            AndroidView(modifier = Modifier.requiredSize(240.dp, 360.dp),
                factory = { EpubPageTurnView(it).also { view -> sheet = view } },
                onRelease = { it.dispose() })
        }
        compose.runOnIdle {
            val density = sheet.resources.displayMetrics.density
            val x = (40 * density).toInt()
            val y = (16 * density).toInt()
            for (dark in listOf(false, true, false)) {
                sheet.render(EpubReaderUiState(isDarkTheme = dark))
                for (width in listOf(sheet.width, sheet.width + 100)) {
                    sheet.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(sheet.height, android.view.View.MeasureSpec.EXACTLY))
                    sheet.layout(0, 0, width, sheet.measuredHeight)
                    for (child in listOf(sheet.reader)) {
                        assertEquals(x, child.left)
                        assertEquals(y, child.top)
                        assertEquals(width - 2 * x, child.width)
                        assertEquals(sheet.height - 2 * y, child.height)
                    }
                    val bitmap = android.graphics.Bitmap.createBitmap(width, sheet.height, android.graphics.Bitmap.Config.ARGB_8888)
                    sheet.draw(android.graphics.Canvas(bitmap))
                    assertEquals(if (dark) android.graphics.Color.rgb(18, 18, 18) else android.graphics.Color.WHITE,
                        bitmap.getPixel(0, sheet.height / 2))
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun failedDestinationUnlocksControlsAfterEightSecondsWithoutRevealingText() =
        exerciseInterruptedTurn(failDocument = true)

    @Test fun externalChapterSupersedesCommittedDestinationWithoutStayingCovered() =
        exerciseInterruptedTurn(failDocument = false)

    private fun exerciseInterruptedTurn(failDocument: Boolean) {
        val file = File.createTempFile("epub-interrupted-turn-", ".epub")
        try {
            val html = "<p>Controlled destination text.</p>".repeat(80)
            val entries = mapOf(
                "META-INF/container.xml" to "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.html' media-type='text/html'/><item id='b' href='b.html' media-type='text/html'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>",
                "a.html" to html, "b.html" to html)
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            } }
            val state = mutableStateOf(EpubReaderUiState(isLoading = false, isReady = true,
                epubBook = EpubParser.parse(file), chapterGeneration = 1))
            lateinit var sheet: EpubPageTurnView
            var moves = 0
            var taps = 0
            var failedAt = 0L
            var diagnostics = ""
            compose.setContent {
                AndroidView(modifier = Modifier.requiredSize(240.dp, 360.dp),
                    factory = { context -> EpubPageTurnView(context).apply {
                        sheet = this
                        reader.onImageDiagnostics = { diagnostics = it }
                        onTap = { taps++ }
                        onMeasured = { token, page, count ->
                            val s = state.value
                            if (token == s.chapterGeneration) state.value = s.copy(page = page,
                                pageCount = count, proportion = EpubPagination.proportion(page, count))
                        }
                        onMove = {
                            moves++
                            failedAt = android.os.SystemClock.uptimeMillis()
                            if (failDocument) {
                                // Public WebView navigation deterministically reports document failure,
                                // never pagination readiness. No internal callbacks are replaced.
                                reader.loadUrl("about:blank")
                            } else {
                                // A TOC jump supersedes the requested next page in chapter zero.
                                state.value = state.value.copy(currentChapterIndex = 1,
                                    chapterGeneration = 2, page = 0, pageCount = 0, proportion = 0.0)
                            }
                        }
                    } }, update = { it.render(state.value) }, onRelease = { it.dispose() })
            }
            fun awaitUncovered() {
                compose.waitUntil(5000) {
                    var uncovered = false
                    compose.runOnIdle { uncovered = sheet.getChildAt(1).visibility == android.view.View.GONE }
                    uncovered
                }
            }
            fun centerTap() {
                compose.runOnIdle {
                    val now = android.os.SystemClock.uptimeMillis()
                    for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                        val event = android.view.MotionEvent.obtain(now, now, action,
                            sheet.width * .5f, sheet.height * .5f, 0)
                        sheet.dispatchTouchEvent(event); event.recycle()
                    }
                }
            }
            compose.waitUntil(10000) { state.value.pageCount > 1 }
            awaitUncovered()
            compose.runOnIdle { sheet.turn(1); assertEquals(0, moves) }
            compose.waitUntil(5000) { moves == 1 }
            if (failDocument) {
                compose.waitUntil(5000) { diagnostics.startsWith("DOCUMENTO_NO_FIABLE") }
                centerTap()
                compose.runOnIdle { assertEquals(0, taps) }
                compose.waitUntil(11000) { centerTap(); taps > 0 }
                compose.runOnIdle {
                    assertTrue("Recovery must be bounded by the eight-second wait",
                        android.os.SystemClock.uptimeMillis() - failedAt in 7800L..11000L)
                    assertEquals(1, moves)
                    assertEquals(0, state.value.currentChapterIndex)
                    assertEquals(0, state.value.page)
                    assertEquals(android.view.View.VISIBLE, sheet.getChildAt(1).visibility)
                    assertEquals("Failed content must stay hidden", 1f, sheet.getChildAt(1).alpha, 0f)
                    // Controls can request a retry without exposing the failed document.
                    state.value = state.value.copy(chapterGeneration = 2)
                }
                awaitUncovered()
            } else {
                compose.waitUntil(5000) { state.value.currentChapterIndex == 1 && state.value.pageCount > 1 }
                awaitUncovered()
                centerTap()
                compose.runOnIdle { assertEquals(1, taps); assertEquals(1, moves); assertEquals(0, state.value.page) }
            }
        } finally { file.delete() }
    }

    @Test fun productionInputNavigatesOnlyAfterCompletedTurns() {
        val file = File.createTempFile("epub-turn-", ".epub")
        try {
            val html = "<p>Reading text for horizontal pages with reflow.</p>".repeat(120)
            val entries = mapOf(
                "META-INF/container.xml" to "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                "book.opf" to "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.html' media-type='text/html'/><item id='b' href='b.html' media-type='text/html'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>",
                "a.html" to html, "b.html" to html
            )
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            } }
            val state = mutableStateOf(EpubReaderUiState(isLoading = false, isReady = true,
                epubBook = EpubParser.parse(file), chapterGeneration = 1))
            var moves = 0
            var taps = 0
            lateinit var sheet: EpubPageTurnView
            var requestedAt = 0L
            var completedAt = 0L
            compose.setContent {
                AndroidView(modifier = Modifier.requiredSize(240.dp, 360.dp).testTag("reader"),
                    factory = { context -> EpubPageTurnView(context).apply {
                        sheet = this
                        onTap = { taps++ }
                        onMeasured = { token, page, count ->
                            val s = state.value
                            if (token == s.chapterGeneration) state.value = s.copy(page = page,
                                pageCount = count, proportion = EpubPagination.proportion(page, count))
                        }
                        onMove = { direction ->
                            moves++
                            completedAt = android.os.SystemClock.uptimeMillis()
                            val s = state.value
                            val p = EpubPagination.move(EpubPosition(s.currentChapterIndex, s.page), s.pageCount, 2, direction)
                            state.value = if (p.chapter == s.currentChapterIndex) s.copy(page = p.page,
                                proportion = EpubPagination.proportion(p.page, s.pageCount))
                            else s.copy(currentChapterIndex = p.chapter, pageCount = 0,
                                proportion = if (direction < 0) 1.0 else 0.0,
                                chapterGeneration = s.chapterGeneration + 1)
                        }
                    } }, update = { it.render(state.value) }, onRelease = { it.dispose() })
            }
            compose.waitUntil(10000) { state.value.pageCount > 1 }
            val reader = compose.onNodeWithTag("reader")
            fun awaitTransition() {
                compose.waitUntil(10000) {
                    var finished = false
                    compose.runOnIdle { finished = sheet.getChildAt(1).visibility == android.view.View.GONE }
                    finished
                }
            }
            fun drag(from: Float, to: Float, duration: Long = 300) {
                awaitTransition()
                // Native animations and WebView use the real clock, not Compose's clock.
                compose.runOnIdle {
                    val start = android.os.SystemClock.uptimeMillis()
                    fun send(action: Int, x: Float, elapsed: Long) {
                        val event = android.view.MotionEvent.obtain(start, start + elapsed,
                            action, sheet.width * x, sheet.height * .5f, 0)
                        sheet.dispatchTouchEvent(event)
                        event.recycle()
                    }
                    send(android.view.MotionEvent.ACTION_DOWN, from, 0)
                    for (i in 1..10) send(android.view.MotionEvent.ACTION_MOVE,
                        from + (to - from) * i / 10, duration * i / 10)
                    send(android.view.MotionEvent.ACTION_UP, to, duration)
                }
            }
            // Wait for the initial readiness fade, which uses Android's animation clock.
            compose.waitUntil(10000) {
                var visible = false
                compose.runOnIdle { visible = sheet.getChildAt(1).visibility == android.view.View.GONE }
                visible
            }
            compose.runOnIdle {
                requestedAt = android.os.SystemClock.uptimeMillis()
                sheet.turn(1)
                assertEquals("A turn must not replace text synchronously", 0, moves)
                assertEquals(0, state.value.page)
            }
            compose.waitUntil(10000) { moves == 1 }
            compose.runOnIdle {
                assertTrue("Navigation must follow a nonzero fade", completedAt - requestedAt >= 100)
            }
            compose.waitUntil(10000) {
                var visible = false
                compose.runOnIdle { visible = sheet.getChildAt(1).visibility == android.view.View.GONE }
                visible
            }
            compose.runOnIdle { sheet.turn(-1) }
            compose.waitUntil(10000) { state.value.page == 0 }
            compose.waitUntil(10000) {
                var visible = false
                compose.runOnIdle { visible = sheet.getChildAt(1).visibility == android.view.View.GONE }
                visible
            }
            compose.runOnIdle { moves = 0 }
            compose.mainClock.advanceTimeBy(1500)
            compose.runOnIdle { assertEquals(0, state.value.page); assertEquals(0, moves) }
            reader.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(1, taps); assertEquals(0, moves) }
            drag(.9f, .1f)
            compose.waitUntil(10000) { state.value.page == 1 }
            drag(.1f, .9f)
            compose.waitUntil(10000) { state.value.page == 0 }
            awaitTransition()
            reader.performTouchInput { click(Offset(width * .9f, height * .5f)) }
            compose.waitUntil(10000) { state.value.page == 1 }
            awaitTransition()
            reader.performTouchInput { click(Offset(width * .1f, height * .5f)) }
            compose.waitUntil(10000) { state.value.page == 0 }
            awaitTransition()
            val before = state.value
            val completed = moves
            drag(.5f, .4f, 1000)
            compose.mainClock.advanceTimeBy(1500)
            compose.runOnIdle { assertEquals(before, state.value); assertEquals(completed, moves) }
            reader.performTouchInput { swipeUp() }
            compose.runOnIdle { assertEquals(before, state.value); assertEquals(completed, moves); assertEquals(1, taps) }
            compose.runOnIdle { state.value = state.value.copy(proportion = 1.0) }
            compose.waitUntil(5000) { state.value.page == state.value.pageCount - 1 }
            drag(.9f, .1f)
            compose.waitUntil(10000) { state.value.currentChapterIndex == 1 && state.value.pageCount > 1 }
            compose.runOnIdle { assertEquals(0, state.value.page); assertEquals(completed + 1, moves) }
            drag(.1f, .9f)
            compose.waitUntil(10000) { state.value.currentChapterIndex == 0 && state.value.pageCount > 1 && state.value.page == state.value.pageCount - 1 }
            compose.runOnIdle { assertEquals(completed + 2, moves); assertEquals(1, taps) }
        } finally { file.delete() }
    }
}
