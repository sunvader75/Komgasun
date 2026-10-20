package com.sunvader.komgasun.ui.reader.epub

/** Vocabulario fijo para diagnósticos: usar la categoría, nunca mensajes de excepción ni rutas del archivo. */
enum class EpubResourceCategory {
    EMPTY_SOURCE, INVALID_SOURCE_ORIGIN, URI_OR_CANONICAL, NOT_MANIFEST,
    ARCHIVE_MISSING, ARCHIVE_UNREADABLE, ENTRY_MISSING, ENTRY_LIMIT,
    INVALID_ARCHIVE, READ_IO, UNEXPECTED_CLASS
}
class EpubResourceFailure(val category: EpubResourceCategory, message: String = category.name) : EpubException(message)
