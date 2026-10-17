package com.sunvader.komgasun.ui.reader.comic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ComicPageLoaderTest {
    @Test fun backwardNavigationStartsCurrentBeforeOlderLoadsFinish() = runTest {
        val started = mutableListOf<Int>()
        val gates = (0..19).associateWith { CompletableDeferred<Any>() }
        val cancelled = mutableListOf<Int>()
        var active = 0
        var peakActive = 0
        val loader = ComicPageLoader(this, 20) { page ->
            started += page
            active++
            peakActive = maxOf(peakActive, active)
            try { gates.getValue(page).await() } finally {
                active--
                if (!gates.getValue(page).isCompleted) cancelled += page
            }
        }
        try {
            loader.moveTo(10); runCurrent()
            assertEquals(listOf(10, 11), started)
            loader.moveTo(9); runCurrent()
            assertTrue(9 in started)
            assertEquals(listOf(11), cancelled)
            assertEquals(2, peakActive)
            assertEquals(1, started.count { it == 10 })
            gates.getValue(9).complete(Any()); runCurrent()
            assertTrue(loader.pages.value.size <= 5)
        } finally {
            loader.close(); runCurrent()
        }
        val before = started.toList()
        loader.moveTo(8); loader.retry(9); runCurrent()
        assertEquals(before, started)
        assertTrue(loader.pages.value.isEmpty())
        assertEquals(0, active)
    }

    @Test fun promotionSharesWorkAndObsoleteWorkIsCancelled() = runTest {
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val gates = (0..9).associateWith { CompletableDeferred<String>() }
        val loader = ComicPageLoader(this, 10) { page ->
            started += page
            try { gates.getValue(page).await() } finally {
                if (!gates.getValue(page).isCompleted) cancelled += page
            }
        }
        loader.moveTo(0); runCurrent()
        assertEquals(listOf(0, 1), started)
        loader.moveTo(1); runCurrent()
        assertEquals(listOf(0, 1), started)
        assertTrue(cancelled.isEmpty())
        gates.getValue(1).complete("shared"); runCurrent()
        assertSame(gates.getValue(1).await(), loader.pages.value[1]?.value)
        assertEquals(1, started.count { it == 1 })
        loader.moveTo(8); runCurrent()
        assertTrue(cancelled.contains(0))
        assertEquals(listOf(8, 9), started.takeLast(2))
        assertTrue(loader.pages.value.keys.all { it in listOf(8, 9, 7) })
        loader.close(); runCurrent()
        assertTrue(cancelled.containsAll(listOf(8, 9)))
    }

    @Test fun completedCurrentLeavesOnlyOneAnticipatoryRequestAndReturningDoesNotCancelIt() = runTest {
        val started = mutableListOf<Int>()
        val gates = (0..5).associateWith { CompletableDeferred<String>() }
        val loader = ComicPageLoader(this, 6) { page ->
            started += page
            gates.getValue(page).await()
        }
        loader.moveTo(1); runCurrent()
        gates.getValue(1).complete("one"); runCurrent()
        assertEquals(listOf(1, 2), started)
        loader.moveTo(0); runCurrent()
        assertEquals(listOf(1, 2, 0), started)
        assertFalse(gates.getValue(2).isCancelled)
        loader.close(); runCurrent()
    }

    @Test fun failureCanRetryAndCurrentStartsBeforePrefetch() = runTest {
        val started = mutableListOf<Int>()
        var fail = true
        val loader = ComicPageLoader(this, 5) { page ->
            started += page
            if (page == 2 && fail) error("fake")
            "page$page"
        }
        loader.moveTo(2); runCurrent()
        assertEquals(2, started.first())
        assertNotNull(loader.pages.value[2]?.error)
        fail = false
        loader.retry(2); runCurrent()
        assertEquals("page2", loader.pages.value[2]?.value)
        loader.close()
        loader.moveTo(0); runCurrent()
        assertTrue(loader.pages.value.isEmpty())
    }
}
