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
                    for (index in 0..1) {
                        val child = sheet.getChildAt(index)
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
            compose.setContent {
                AndroidView(modifier = Modifier.requiredSize(240.dp, 360.dp).testTag("reader"),
                    factory = { context -> EpubPageTurnView(context).apply {
                        onTap = { taps++ }
                        onMeasured = { token, page, count ->
                            val s = state.value
                            if (token == s.chapterGeneration) state.value = s.copy(page = page,
                                pageCount = count, proportion = EpubPagination.proportion(page, count))
                        }
                        onMove = { direction ->
                            moves++
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
            // Idle preview measurements must not write reader progress or navigate.
            compose.mainClock.advanceTimeBy(1500)
            compose.runOnIdle { assertEquals(0, state.value.page); assertEquals(0, moves) }
            reader.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(1, taps); assertEquals(0, moves) }
            reader.performTouchInput { swipeLeft() }
            compose.waitUntil(10000) { state.value.page == 1 }
            compose.mainClock.advanceTimeBy(1000)
            reader.performTouchInput { swipeRight() }
            compose.waitUntil(10000) { state.value.page == 0 }
            compose.mainClock.advanceTimeBy(1000)
            reader.performTouchInput { click(Offset(width * .9f, height * .5f)) }
            compose.waitUntil(10000) { state.value.page == 1 }
            compose.mainClock.advanceTimeBy(1000)
            reader.performTouchInput { click(Offset(width * .1f, height * .5f)) }
            compose.waitUntil(10000) { state.value.page == 0 }
            compose.mainClock.advanceTimeBy(1000)
            val before = state.value
            val completed = moves
            reader.performTouchInput {
                swipe(center, Offset(width * .4f, center.y), durationMillis = 1000)
            }
            compose.mainClock.advanceTimeBy(1500)
            compose.runOnIdle { assertEquals(before, state.value); assertEquals(completed, moves) }
            reader.performTouchInput { swipeUp() }
            compose.runOnIdle { assertEquals(before, state.value); assertEquals(completed, moves); assertEquals(1, taps) }
            compose.runOnIdle { state.value = state.value.copy(proportion = 1.0) }
            compose.waitUntil(5000) { state.value.page == state.value.pageCount - 1 }
            reader.performTouchInput { swipeLeft() }
            compose.waitUntil(10000) { state.value.currentChapterIndex == 1 && state.value.pageCount > 1 }
            compose.runOnIdle { assertEquals(0, state.value.page); assertEquals(completed + 1, moves) }
            compose.mainClock.advanceTimeBy(1000)
            reader.performTouchInput { swipeRight() }
            compose.waitUntil(10000) { state.value.currentChapterIndex == 0 && state.value.pageCount > 1 && state.value.page == state.value.pageCount - 1 }
            compose.runOnIdle { assertEquals(completed + 2, moves); assertEquals(1, taps) }
        } finally { file.delete() }
    }
}
