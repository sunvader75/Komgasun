package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class EpubBookPaginatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun measuredBookMatchesVisiblePagesAndReflowsWithoutMovingTheReader() {
        val book = EpubBook("Book", "Author", listOf(8, 80).mapIndexed { index, length ->
            EpubChapter("$index", "Chapter $index", "$index.html",
                "<html><body>${"<p>Exact pagination with multiple words and lines of reading text.</p>".repeat(length)}</body></html>",
                resourcePath = "$index.html")
        })
        val state = mutableStateOf(EpubReaderUiState(isLoading = false, isReady = true,
            epubBook = book, chapterGeneration = 1))
        val wide = mutableStateOf(false)
        val reopen = mutableStateOf(0)
        val measurements = java.util.concurrent.atomic.AtomicInteger(0)
        val cacheDirectory = java.io.File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "pagination-test-${java.util.UUID.randomUUID()}"
        )
        val map = AtomicReference<EpubBookPageMap?>()
        val visible = AtomicReference<Triple<Long, Int, Int>?>()
        val failed = AtomicReference(false)
        compose.setContent {
            Box(Modifier.requiredSize(if (wide.value) 360.dp else 240.dp, if (wide.value) 240.dp else 360.dp)) {
                androidx.compose.runtime.key(reopen.value) {
                AndroidView(factory = { EpubBookPaginator(it, cacheDirectory).apply {
                    alpha = 0f
                    val measured = onPaginationReady
                    onPaginationReady = { token, count -> measurements.incrementAndGet(); measured?.invoke(token, count) }
                } }, update = { view ->
                    view.onPageMap = { result, error -> map.set(result); failed.set(error) }
                    view.paginate(book, state.value.fontSizeSp, state.value.isDarkTheme)
                }, onRelease = { it.release() }, modifier = Modifier.fillMaxSize())
                }
                AndroidView(factory = { EpubWebView(it).apply {
                    onMeasured = { token, page, count -> visible.set(Triple(token, page, count)) }
                } }, update = { it.render(state.value) }, onRelease = { it.dispose() }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.waitUntil(15_000) { map.get() != null || failed.get() }
        assertFalse(failed.get())
        val pages = map.get()!!
        compose.waitUntil(5000) { visible.get()?.first == 1L }
        assertEquals(pages.counts[0], visible.get()!!.third)
        assertEquals(0, state.value.currentChapterIndex)
        assertEquals(0.0, state.value.proportion, 0.0)

        val initialMeasurements = measurements.get()
        compose.runOnIdle { state.value = state.value.copy(isDarkTheme = false) }
        compose.waitForIdle()
        assertSame(pages, map.get())
        assertEquals(initialMeasurements, measurements.get())
        compose.runOnIdle { reopen.value++; map.set(null) }
        compose.waitUntil(5000) { map.get() != null || failed.get() }
        assertFalse(failed.get())
        assertEquals(pages.counts, map.get()!!.counts)
        assertEquals(initialMeasurements, measurements.get()) // A cache hit never renders a chapter.

        val destination = pages.counts[0] + pages.counts[1] / 2 + 1
        val position = pages.position(destination)
        compose.runOnIdle {
            state.value = state.value.copy(currentChapterIndex = position.chapter,
                proportion = pages.proportion(position.chapter, position.page), chapterGeneration = 2)
        }
        compose.waitUntil(5000) { visible.get()?.first == 2L }
        assertEquals(pages.counts[1], visible.get()!!.third)
        assertEquals(position.page, visible.get()!!.second)
        assertEquals(destination, pages.absolutePage(position.chapter, visible.get()!!.second))

        compose.runOnIdle {
            state.value = state.value.copy(fontSizeSp = 28, chapterGeneration = 3)
            wide.value = true
        }
        compose.waitUntil(15_000) { (map.get() != null && map.get()!!.counts != pages.counts) || failed.get() }
        assertFalse(failed.get())
        compose.waitUntil(5000) { visible.get()?.first == 3L }
        assertEquals(map.get()!!.counts[1], visible.get()!!.third)
        assertNotEquals(pages.counts, map.get()!!.counts)
        assertEquals(position.chapter, state.value.currentChapterIndex)
        cacheDirectory.deleteRecursively()
    }
}
