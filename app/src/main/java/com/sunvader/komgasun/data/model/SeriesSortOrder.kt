package com.sunvader.komgasun.data.model

import java.util.Random

enum class SeriesSortOrder(val label: String) {
    ALPHABETICAL("Alfabético"),
    RANDOM("Aleatorio");

    companion object {
        fun fromName(name: String?): SeriesSortOrder {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALPHABETICAL
        }
    }
}

fun List<SeriesDto>.sortedByOrder(order: SeriesSortOrder, randomSeed: Long = 0L): List<SeriesDto> {
    return when (order) {
        SeriesSortOrder.ALPHABETICAL -> {
            this.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.metadata.title.ifBlank { it.name }
            })
        }
        SeriesSortOrder.RANDOM -> {
            this.shuffled(Random(randomSeed))
        }
    }
}

fun List<BookDto>.sortedByNumbering(): List<BookDto> {
    return this.sortedWith(
        compareBy<BookDto> { book ->
            book.metadata.number.toFloatOrNull()
                ?: if (book.number > 0) book.number.toFloat() else Float.MAX_VALUE
        }.thenBy { book ->
            book.number
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { book ->
            book.name
        }
    )
}
