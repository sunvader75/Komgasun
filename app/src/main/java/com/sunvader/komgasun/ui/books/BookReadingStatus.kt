package com.sunvader.komgasun.ui.books

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
    if (progress == null || progress.page <= 0) return BookReadingStatus(unavailable = unavailable)
    // Komga's EPUB server position is not a chapter index or a physical page.
    if (detectBookType() == BookType.EPUB) {
        return BookReadingStatus(detail = "En lectura", unavailable = unavailable)
    }
    if (media.pagesCount <= 0) {
        return BookReadingStatus(detail = "En lectura · Pág. ${progress.page}", unavailable = unavailable)
    }
    // Only completed can communicate completion, even for stale/out-of-range positions.
    val percent = (progress.page.toLong() * 100 / media.pagesCount).coerceIn(0, 99).toInt()
    return BookReadingStatus(detail = "En lectura · $percent %", fraction = percent / 100f, unavailable = unavailable)
}
