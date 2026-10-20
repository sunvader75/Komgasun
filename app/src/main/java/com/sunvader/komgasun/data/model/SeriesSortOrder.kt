package com.sunvader.komgasun.data.model

import java.util.Random

enum class SeriesSortOrder(val label: String) {
    ALPHABETICAL("Alfabético"),
    RANDOM("Aleatorio");

    companion object {
        /** Resuelve el nombre sin distinguir mayúsculas; usa ALPHABETICAL si falta o no se reconoce. */
        fun fromName(name: String?): SeriesSortOrder {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALPHABETICAL
        }
    }
}

/**
 * Devuelve una copia ordenada por título (o nombre si está vacío), sin distinguir mayúsculas,
 * o barajada reproduciblemente con [randomSeed]; no modifica la lista original.
 */
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

/**
 * Ordena una copia por número de metadatos interpretable como Float, o por número positivo del libro.
 * Sin número usa Float.MAX_VALUE; desempata por número del libro y nombre sin distinguir mayúsculas.
 */
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
