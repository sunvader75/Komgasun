package com.example.komgasun.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDtoTest {

    @Test
    fun testDetectBookType_pdf() {
        val book = BookDto(
            id = "1",
            seriesId = "s1",
            name = "document.pdf",
            media = BookMediaDto(mediaType = "application/pdf")
        )
        assertEquals(BookType.PDF, book.detectBookType())
        assertEquals("PDF", book.formatBadge())
    }

    @Test
    fun testDetectBookType_epub() {
        val book = BookDto(
            id = "2",
            seriesId = "s1",
            name = "novel.epub",
            media = BookMediaDto(mediaType = "application/epub+zip")
        )
        assertEquals(BookType.EPUB, book.detectBookType())
        assertEquals("EPUB", book.formatBadge())
    }

    @Test
    fun testDetectBookType_cbz() {
        val book = BookDto(
            id = "3",
            seriesId = "s1",
            name = "comic.cbz",
            media = BookMediaDto(mediaType = "application/vnd.comicbook+zip")
        )
        assertEquals(BookType.COMIC, book.detectBookType())
        assertEquals("CBZ", book.formatBadge())
    }

    @Test
    fun testDetectBookType_cbr() {
        val book = BookDto(
            id = "4",
            seriesId = "s1",
            name = "comic.cbr",
            media = BookMediaDto(mediaType = "application/vnd.comicbook-rar")
        )
        assertEquals(BookType.COMIC, book.detectBookType())
        assertEquals("CBR", book.formatBadge())
    }

    @Test
    fun testBookFilter_unread_inProgress_read_unavailable() {
        val unreadBook = BookDto(id = "1", seriesId = "s1", name = "Book 1")
        val inProgressBook = BookDto(
            id = "2", seriesId = "s1", name = "Book 2",
            readProgress = ReadProgressDto(page = 15, completed = false)
        )
        val readBook = BookDto(
            id = "3", seriesId = "s1", name = "Book 3",
            readProgress = ReadProgressDto(page = 30, completed = true)
        )
        val unavailableBook = BookDto(
            id = "4", seriesId = "s1", name = "Book 4",
            media = BookMediaDto(status = "UNAVAILABLE")
        )

        // UNREAD filter
        assertTrue(unreadBook.matchesFilter(ItemFilter.UNREAD))
        assertFalse(inProgressBook.matchesFilter(ItemFilter.UNREAD))
        assertFalse(readBook.matchesFilter(ItemFilter.UNREAD))
        assertFalse(unavailableBook.matchesFilter(ItemFilter.UNREAD))

        // IN_PROGRESS filter
        assertFalse(unreadBook.matchesFilter(ItemFilter.IN_PROGRESS))
        assertTrue(inProgressBook.matchesFilter(ItemFilter.IN_PROGRESS))
        assertFalse(readBook.matchesFilter(ItemFilter.IN_PROGRESS))
        assertFalse(unavailableBook.matchesFilter(ItemFilter.IN_PROGRESS))

        // READ filter
        assertFalse(unreadBook.matchesFilter(ItemFilter.READ))
        assertFalse(inProgressBook.matchesFilter(ItemFilter.READ))
        assertTrue(readBook.matchesFilter(ItemFilter.READ))
        assertFalse(unavailableBook.matchesFilter(ItemFilter.READ))

        // UNAVAILABLE filter
        assertFalse(unreadBook.matchesFilter(ItemFilter.UNAVAILABLE))
        assertFalse(inProgressBook.matchesFilter(ItemFilter.UNAVAILABLE))
        assertFalse(readBook.matchesFilter(ItemFilter.UNAVAILABLE))
        assertTrue(unavailableBook.matchesFilter(ItemFilter.UNAVAILABLE))

        // ALL filter
        assertTrue(unreadBook.matchesFilter(ItemFilter.ALL))
        assertTrue(inProgressBook.matchesFilter(ItemFilter.ALL))
        assertTrue(readBook.matchesFilter(ItemFilter.ALL))
        assertTrue(unavailableBook.matchesFilter(ItemFilter.ALL))
    }

    @Test
    fun testSeriesFilter_unread_inProgress_read_unavailable() {
        val unreadSeries = SeriesDto(id = "s1", libraryId = "lib1", name = "Series 1", booksCount = 5, booksUnreadCount = 5)
        val inProgressSeries = SeriesDto(id = "s2", libraryId = "lib1", name = "Series 2", booksCount = 5, booksInProgressCount = 1, booksReadCount = 2)
        val readSeries = SeriesDto(id = "s3", libraryId = "lib1", name = "Series 3", booksCount = 5, booksReadCount = 5)
        val unavailableSeries = SeriesDto(id = "s4", libraryId = "lib1", name = "Series 4", booksCount = 5, unavailable = true)

        assertTrue(unreadSeries.matchesFilter(ItemFilter.UNREAD))
        assertTrue(inProgressSeries.matchesFilter(ItemFilter.IN_PROGRESS))
        assertTrue(readSeries.matchesFilter(ItemFilter.READ))
        assertTrue(unavailableSeries.matchesFilter(ItemFilter.UNAVAILABLE))
    }
}
