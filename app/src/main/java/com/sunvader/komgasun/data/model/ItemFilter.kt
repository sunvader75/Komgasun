package com.sunvader.komgasun.data.model

enum class ItemFilter(val label: String) {
    ALL("Todos"),
    UNREAD("Sin leer"),
    IN_PROGRESS("En progreso"),
    READ("Leído"),
    UNAVAILABLE("No disponible");

    companion object {
        /** Resuelve el nombre sin distinguir mayúsculas; devuelve ALL si es nulo o desconocido. */
        fun fromName(name: String?): ItemFilter {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALL
        }
    }
}

/** Evalúa los contadores de la serie; los filtros de lectura excluyen series eliminadas o no disponibles. */
fun SeriesDto.matchesFilter(filter: ItemFilter): Boolean {
    val isUnavailable = deleted || unavailable
    return when (filter) {
        ItemFilter.ALL -> true
        ItemFilter.UNAVAILABLE -> isUnavailable
        ItemFilter.UNREAD -> !isUnavailable && (booksReadCount == 0 && booksInProgressCount == 0 && booksCount > 0)
        ItemFilter.IN_PROGRESS -> !isUnavailable && (booksInProgressCount > 0 || (booksReadCount > 0 && booksReadCount < booksCount))
        ItemFilter.READ -> !isUnavailable && (booksReadCount == booksCount && booksCount > 0)
    }
}

/** Evalúa página y finalización; los filtros de lectura excluyen libros eliminados o con estado UNAVAILABLE/ERROR. */
fun BookDto.matchesFilter(filter: ItemFilter): Boolean {
    val isUnavailable = deleted ||
            media.status.equals("UNAVAILABLE", ignoreCase = true) ||
            media.status.equals("ERROR", ignoreCase = true)

    return when (filter) {
        ItemFilter.ALL -> true
        ItemFilter.UNAVAILABLE -> isUnavailable
        ItemFilter.UNREAD -> !isUnavailable && (readProgress == null || (!readProgress.completed && readProgress.page == 0))
        ItemFilter.IN_PROGRESS -> !isUnavailable && (readProgress != null && !readProgress.completed && readProgress.page > 0)
        ItemFilter.READ -> !isUnavailable && (readProgress != null && readProgress.completed)
    }
}

/** Filtra la disponibilidad; al no haber contadores, cualquier filtro de lectura acepta bibliotecas disponibles. */
fun LibraryDto.matchesFilter(filter: ItemFilter): Boolean {
    return when (filter) {
        ItemFilter.ALL -> true
        ItemFilter.UNAVAILABLE -> unavailable
        ItemFilter.UNREAD, ItemFilter.IN_PROGRESS, ItemFilter.READ -> !unavailable
    }
}

/** Acepta si coincide algún filtro distinto de ALL, o siempre si no queda ninguno. */
fun SeriesDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

/** Acepta si coincide algún filtro distinto de ALL, o siempre si no queda ninguno. */
fun BookDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

/** Acepta si coincide algún filtro distinto de ALL, o siempre si no queda ninguno. */
fun LibraryDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

