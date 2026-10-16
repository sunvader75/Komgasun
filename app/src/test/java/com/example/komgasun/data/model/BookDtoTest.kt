package com.example.komgasun.data.model

import org.junit.Assert.assertEquals
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
}
