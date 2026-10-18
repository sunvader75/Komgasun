package com.sunvader.komgasun.ui.books

import com.sunvader.komgasun.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BookReadingStatusTest {
    private fun book(page: Int = 0, completed: Boolean = false, total: Int = 100, name: String = "book.cbz", status: String = "", deleted: Boolean = false) =
        BookDto(id = "1", seriesId = "s", name = name, media = BookMediaDto(pagesCount = total, status = status), readProgress = ReadProgressDto(page, completed), deleted = deleted)

    @Test fun unreadHasNoReadingIndicators() {
        for (b in listOf(book(), book(page = -1), book().copy(readProgress = null))) {
            val s = b.readingStatus()
            assertNull(s.readBadge)
            assertNull(s.detail)
            assertNull(s.fraction)
            assertFalse(s.unavailable)
        }
    }

    @Test fun completedWinsEvenAtPageZeroAndHasNoProgress() {
        for (page in listOf(0, 35, 100)) {
            val s = book(page, completed = true).readingStatus()
            assertEquals("✓ Leído", s.readBadge)
            assertNull(s.detail)
            assertNull(s.fraction)
        }
    }

    @Test fun trustworthyPageProgress() {
        for (name in listOf("book.cbz", "book.pdf")) {
            val s = book(page = 35, name = name).readingStatus()
            assertNull(s.readBadge)
            assertEquals("En lectura · 35 %", s.detail)
            assertEquals(0.35f, s.fraction!!, 0.0001f)
        }
    }

    @Test fun invalidTotalsAndOutOfRangePagesNeverInventCompletion() {
        for (total in listOf(0, -1)) {
            val s = book(page = 35, total = total).readingStatus()
            assertEquals("En lectura · Pág. 35", s.detail)
            assertNull(s.fraction)
        }
        for (page in listOf(100, 101, Int.MAX_VALUE)) {
            val s = book(page = page).readingStatus()
            assertEquals("En lectura · 99 %", s.detail)
            assertEquals(0.99f, s.fraction!!, 0.0001f)
            assertNull(s.readBadge)
        }
    }

    @Test fun epubChapterNeverUsesMediaPageDenominator() {
        for (total in listOf(0, 10, 100)) {
            val s = book(page = 35, total = total, name = "book.epub").readingStatus()
            assertEquals("En lectura", s.detail)
            assertNull(s.fraction)
            assertNull(s.readBadge)
        }
    }

    @Test fun availabilityIsIndependentAndRetainsAllPriorUnavailableRules() {
        for (b in listOf(book(deleted = true), book(status = "unavailable"), book(status = "eRrOr"))) {
            for (progress in listOf(ReadProgressDto(), ReadProgressDto(35), ReadProgressDto(0, true))) {
                val s = b.copy(readProgress = progress).readingStatus()
                assertTrue(s.unavailable)
                assertEquals(b.copy(readProgress = progress, deleted = false, media = b.media.copy(status = "READY")).readingStatus().copy(unavailable = true), s)
            }
        }
    }
}
