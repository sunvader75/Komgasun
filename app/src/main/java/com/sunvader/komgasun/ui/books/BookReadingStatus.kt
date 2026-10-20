package com.sunvader.komgasun.ui.books

import com.sunvader.komgasun.data.model.ReadProgressDto
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.BookType
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.detectBookType
import com.sunvader.komgasun.data.model.matchesFilter

/** Estado de presentación: la disponibilidad nunca sustituye al estado de lectura. */
data class BookReadingStatus(
    val readBadge: String? = null,
    val detail: String? = null,
    val fraction: Float? = null,
    val unavailable: Boolean = false
)

/**
 * Calcula distintivo, detalle y fracción para la portada sin modificar el libro.
 * Solo completed indica finalización; los porcentajes en curso se limitan al 99 %.
 * EPUB usa progreso global válido, nunca posición del servidor dividida por páginas físicas.
 * Si no hay un total fiable, deja la fracción nula y muestra solo el estado o la página conocida.
 */
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

/** Devuelve una copia con el progreso indicado; al marcar sin leer elimina el porcentaje EPUB local obsoleto. */
fun BookDto.withReadingProgress(progress: ReadProgressDto): BookDto = copy(
    readProgress = progress,
    epubProgress = if (!progress.completed && progress.page <= 0) null else epubProgress
)

/** Devuelve el total positivo de páginas; en EPUB solo admite el último total medido válido, o `null` si falta. */
fun BookDto.coverPageCount(): Int? = if (detectBookType() == BookType.EPUB)
    epubProgress?.takeIf { it.isValid }?.totalPages
else media.pagesCount.takeIf { it > 0 }
