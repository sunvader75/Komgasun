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

    @Test
    fun testMultiFilter_matchesFilters() {
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

        // Empty filter set matches all
        assertTrue(unreadBook.matchesFilters(emptySet()))
        assertTrue(inProgressBook.matchesFilters(emptySet()))
        assertTrue(readBook.matchesFilters(emptySet()))
        assertTrue(unavailableBook.matchesFilters(emptySet()))

        // Set with ALL matches all
        assertTrue(unreadBook.matchesFilters(setOf(ItemFilter.ALL)))
        assertTrue(inProgressBook.matchesFilters(setOf(ItemFilter.ALL)))

        // Multi-select: UNREAD + IN_PROGRESS
        val unreadAndInProgress = setOf(ItemFilter.UNREAD, ItemFilter.IN_PROGRESS)
        assertTrue(unreadBook.matchesFilters(unreadAndInProgress))
        assertTrue(inProgressBook.matchesFilters(unreadAndInProgress))
        assertFalse(readBook.matchesFilters(unreadAndInProgress))
        assertFalse(unavailableBook.matchesFilters(unreadAndInProgress))

        // Multi-select: READ + UNAVAILABLE
        val readAndUnavailable = setOf(ItemFilter.READ, ItemFilter.UNAVAILABLE)
        assertFalse(unreadBook.matchesFilters(readAndUnavailable))
        assertFalse(inProgressBook.matchesFilters(readAndUnavailable))
        assertTrue(readBook.matchesFilters(readAndUnavailable))
        assertTrue(unavailableBook.matchesFilters(readAndUnavailable))
    }

    @Test
    fun testSeriesSortOrder_fromName() {
        assertEquals(SeriesSortOrder.ALPHABETICAL, SeriesSortOrder.fromName("ALPHABETICAL"))
        assertEquals(SeriesSortOrder.ALPHABETICAL, SeriesSortOrder.fromName("alphabetical"))
        assertEquals(SeriesSortOrder.RANDOM, SeriesSortOrder.fromName("RANDOM"))
        assertEquals(SeriesSortOrder.RANDOM, SeriesSortOrder.fromName("random"))
        assertEquals(SeriesSortOrder.ALPHABETICAL, SeriesSortOrder.fromName(null))
        assertEquals(SeriesSortOrder.ALPHABETICAL, SeriesSortOrder.fromName("UNKNOWN"))
    }

    @Test
    fun testSeriesSort_alphabetical() {
        val seriesBatman = SeriesDto(id = "1", libraryId = "lib", name = "Batman", metadata = SeriesMetadataDto(title = "Batman"))
        val seriesSuperman = SeriesDto(id = "2", libraryId = "lib", name = "Superman", metadata = SeriesMetadataDto(title = "superman"))
        val seriesAquaman = SeriesDto(id = "3", libraryId = "lib", name = "Aquaman", metadata = SeriesMetadataDto(title = ""))
        val seriesZatanna = SeriesDto(id = "4", libraryId = "lib", name = "Zatanna")

        val list = listOf(seriesSuperman, seriesZatanna, seriesBatman, seriesAquaman)
        val sorted = list.sortedByOrder(SeriesSortOrder.ALPHABETICAL)

        assertEquals(listOf("Aquaman", "Batman", "Superman", "Zatanna"), sorted.map { it.name })
    }

    @Test
    fun testSeriesSort_random() {
        val seriesList = (1..20).map {
            SeriesDto(id = "$it", libraryId = "lib", name = "Series $it")
        }

        val shuffled1 = seriesList.sortedByOrder(SeriesSortOrder.RANDOM, randomSeed = 42L)
        val shuffled2 = seriesList.sortedByOrder(SeriesSortOrder.RANDOM, randomSeed = 42L)
        val shuffled3 = seriesList.sortedByOrder(SeriesSortOrder.RANDOM, randomSeed = 999L)

        // Same seed produces same order
        assertEquals(shuffled1.map { it.id }, shuffled2.map { it.id })
        // Different seed produces different order
        assertFalse(shuffled1.map { it.id } == shuffled3.map { it.id })
    }

    @Test
    fun testBookSort_numbering() {
        val book1 = BookDto(id = "1", seriesId = "s", name = "Issue #1", metadata = BookMetadataDto(number = "1"))
        val book1_5 = BookDto(id = "1.5", seriesId = "s", name = "Special #1.5", metadata = BookMetadataDto(number = "1.5"))
        val book2 = BookDto(id = "2", seriesId = "s", name = "Issue #02", metadata = BookMetadataDto(number = "02"))
        val book10 = BookDto(id = "10", seriesId = "s", name = "Issue #10", number = 10)
        val bookAnnual = BookDto(id = "A", seriesId = "s", name = "Annual A", number = 0)
        val bookZAnnual = BookDto(id = "Z", seriesId = "s", name = "Annual Z", number = 0)

        val unsorted = listOf(book10, bookAnnual, book2, book1_5, bookZAnnual, book1)
        val sorted = unsorted.sortedByNumbering()

        assertEquals(listOf("1", "1.5", "2", "10", "A", "Z"), sorted.map { it.id })
    }
}
