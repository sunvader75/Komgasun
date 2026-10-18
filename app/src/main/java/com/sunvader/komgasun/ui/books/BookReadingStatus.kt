package com.sunvader.komgasun.ui.books

import com.sunvader.komgasun.data.model.ReadProgressDto
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.BookType
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.detectBookType
import com.sunvader.komgasun.data.model.matchesFilter

/** Presentation only: availability never replaces the reading state. */
data class BookReadingStatus(
    val readBadge: String? = null,
    val detail: String? = null,
    val fraction: Float? = null,
    val unavailable: Boolean = false
)

fun BookDto.readingStatus(): BookReadingStatus {
    val unavailable = matchesFilter(ItemFilter.UNAVAILABLE)
    val progress = readProgress
    if (progress?.completed == true) return BookReadingStatus(readBadge = "✓ Leído", unavailable = unavailable)
    // EPUB uses the reader's measured map, or the server's explicit totalProgression.
    // A server position number must never be divided by a physical-page total.
    if (detectBookType() == BookType.EPUB) {
        val summary = epubProgress?.takeIf { it.isValid }
        if (summary != null) {
            val percent = (summary.fraction * 100 + 0.000001).toInt().coerceIn(0, 99)
            return BookReadingStatus(detail = "En lectura · $percent %", fraction = percent / 100f, unavailable = unavailable)
        }
        return if (progress != null && progress.page > 0) BookReadingStatus(detail = "En lectura", unavailable = unavailable)
            else BookReadingStatus(unavailable = unavailable)
    }
    if (progress == null || progress.page <= 0) return BookReadingStatus(unavailable = unavailable)
    if (media.pagesCount <= 0) {
        return BookReadingStatus(detail = "En lectura · Pág. ${progress.page}", unavailable = unavailable)
    }
    // Only completed can communicate completion, even for stale/out-of-range positions.
    val percent = (progress.page.toLong() * 100 / media.pagesCount).coerceIn(0, 99).toInt()
    return BookReadingStatus(detail = "En lectura · $percent %", fraction = percent / 100f, unavailable = unavailable)
}

/** Explicit unread overrides must not leave an old local percentage attached to the UI copy. */
fun BookDto.withReadingProgress(progress: ReadProgressDto): BookDto = copy(
    readProgress = progress,
    epubProgress = if (!progress.completed && progress.page <= 0) null else epubProgress
)

/** EPUB positions from Komga are not display pages; use only the last reader-measured total. */
fun BookDto.coverPageCount(): Int? = if (detectBookType() == BookType.EPUB)
    epubProgress?.takeIf { it.isValid }?.totalPages
else media.pagesCount.takeIf { it > 0 }
