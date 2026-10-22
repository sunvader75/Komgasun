package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubTurnPreviewsTest {
    @Test fun preparesBeforeTouchAndReusesBothDirections() {
        val requests = mutableListOf<Pair<Int, (String) -> Unit>>()
        val previews = EpubTurnPreviews<String> { step, done -> requests += step to done }
        previews.update("page-1", listOf(1, -1))
        assertEquals(listOf(1), requests.map { it.first }) // No gesture needed.
        requests[0].second("next")
        assertEquals(listOf(1, -1), requests.map { it.first })
        requests[1].second("previous")
        previews.prefer(1)
        previews.prefer(-1)
        previews.update("page-1", listOf(1, -1))
        assertEquals("next", previews[1])
        assertEquals("previous", previews[-1])
        assertEquals(2, requests.size)
    }

    @Test fun prioritizationDoesNotDuplicateInflightWorkAndFailureRejectsLateResult() {
        val requests = mutableListOf<Pair<Int, (String) -> Unit>>()
        val previews = EpubTurnPreviews<String> { step, done -> requests += step to done }
        previews.update("page", listOf(1, -1))
        previews.prefer(-1)
        previews.prefer(-1)
        assertEquals(1, requests.size)
        previews.failed()
        previews.prefer(-1)
        assertEquals(listOf(1, -1), requests.map { it.first })
        requests[0].second("late")
        assertNull(previews[1])
        requests[1].second("previous")
        assertEquals("previous", previews[-1])
        assertEquals(listOf(1, -1, 1), requests.map { it.first })
        previews.update("page", listOf(-1))
        requests[2].second("no-longer-adjacent")
        assertNull(previews[1])
    }

    @Test fun invalidationRejectsOldCallbacksAndAllowsRetryAfterTimeout() {
        val requests = mutableListOf<(String) -> Unit>()
        val previews = EpubTurnPreviews<String> { _, done -> requests += done }
        previews.update("old-layout", listOf(1))
        previews.update("new-layout", listOf(1))
        requests[0]("obsolete")
        assertNull(previews[1])
        requests[1]("current")
        assertEquals("current", previews[1])
        previews.update(null, emptyList())
        assertNull(previews[1])
        previews.update("new-layout", listOf(1))
        previews.failed()
        previews.prefer(1)
        assertEquals(4, requests.size)
    }
}
