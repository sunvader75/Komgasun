package com.sunvader.komgasun.data.model

enum class ItemFilter(val label: String) {
    ALL("Todos"),
    UNREAD("Sin leer"),
    IN_PROGRESS("En progreso"),
    READ("Leído"),
    UNAVAILABLE("No disponible");

    companion object {
        fun fromName(name: String?): ItemFilter {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALL
        }
    }
}

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

fun LibraryDto.matchesFilter(filter: ItemFilter): Boolean {
    return when (filter) {
        ItemFilter.ALL -> true
        ItemFilter.UNAVAILABLE -> unavailable
        ItemFilter.UNREAD, ItemFilter.IN_PROGRESS, ItemFilter.READ -> !unavailable
    }
}

fun SeriesDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

fun BookDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

fun LibraryDto.matchesFilters(filters: Set<ItemFilter>): Boolean {
    val active = filters.filter { it != ItemFilter.ALL }
    if (active.isEmpty()) return true
    return active.any { this.matchesFilter(it) }
}

