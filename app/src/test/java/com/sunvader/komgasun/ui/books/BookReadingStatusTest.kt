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

    @Test fun epubUsesReaderProgressAndMeasuredTotalInsteadOfServerPositions() {
        val b = book(page = 35, total = 999, name = "book.epub")
            .copy(epubProgress = EpubCoverProgress(0.35, 240))
        assertEquals("En lectura · 35 %", b.readingStatus().detail)
        assertEquals(0.35f, b.readingStatus().fraction!!, 0.0001f)
        assertEquals(240, b.coverPageCount())
        assertNull(b.copy(epubProgress = null).coverPageCount())
        assertEquals(999, book(total = 999, name = "book.pdf").coverPageCount())
        assertEquals(999, book(total = 999, name = "book.cbz").coverPageCount())
    }

    @Test fun epubProgressDoesNotInventCompletionAndPreservesReadState() {
        val epub = book(page = 1, name = "book.epub").copy(epubProgress = EpubCoverProgress(1.0, 240))
        assertEquals("En lectura · 99 %", epub.readingStatus().detail)
        assertNull(epub.readingStatus().readBadge)
        val completed = epub.copy(readProgress = ReadProgressDto(0, true)).readingStatus()
        assertEquals("✓ Leído", completed.readBadge)
        assertNull(completed.fraction)
        assertNull(completed.detail)
        val localOnly = epub.copy(readProgress = null, epubProgress = EpubCoverProgress(0.25, null))
        assertEquals("En lectura · 25 %", localOnly.readingStatus().detail)
        assertNull(localOnly.coverPageCount())
        for (fraction in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertNull(epub.copy(epubProgress = EpubCoverProgress(fraction)).readingStatus().fraction)
        }
    }

    @Test fun markingUnreadClearsLocalEpubSummaryWithoutChangingNormalProgressUpdates() {
        val epub = book(page = 35, name = "book.epub").copy(epubProgress = EpubCoverProgress(0.35, 240))
        val reset = epub.withReadingProgress(ReadProgressDto(0, false))
        assertNull(reset.epubProgress)
        assertNull(reset.readingStatus().detail)
        assertNull(reset.readingStatus().fraction)
        assertEquals(epub.epubProgress, epub.withReadingProgress(ReadProgressDto(36)).epubProgress)
        assertEquals("✓ Leído", epub.withReadingProgress(ReadProgressDto(0, true)).readingStatus().readBadge)
    }

    @Test fun remoteEpubUsesOnlyExplicitWholeBookProgress() {
        fun remote(locations: EpubLocationsDto) = EpubProgressionDto("2026-10-18T00:00:00Z", EpubDeviceDto("device", "Reader"),
            EpubLocatorDto("chapter.xhtml", "application/xhtml+xml", locations))
        assertEquals(EpubCoverProgress(0.35), EpubCoverProgress.fromRemote(remote(EpubLocationsDto(totalProgression = 0.35))))
        assertNull(EpubCoverProgress.fromRemote(remote(EpubLocationsDto(progression = 0.35, position = 35))))
        for (invalid in listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(EpubCoverProgress.fromRemote(remote(EpubLocationsDto(totalProgression = invalid))))
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
