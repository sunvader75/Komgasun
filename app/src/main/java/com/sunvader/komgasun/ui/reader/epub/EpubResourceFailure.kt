package com.sunvader.komgasun.ui.reader.epub

/** Fixed vocabulary only: never expose exception messages or archive paths. */
enum class EpubResourceCategory {
    EMPTY_SOURCE, INVALID_SOURCE_ORIGIN, URI_OR_CANONICAL, NOT_MANIFEST,
    ARCHIVE_MISSING, ARCHIVE_UNREADABLE, ENTRY_MISSING, ENTRY_LIMIT,
    INVALID_ARCHIVE, READ_IO, UNEXPECTED_CLASS
}
class EpubResourceFailure(val category: EpubResourceCategory, message: String = category.name) : EpubException(message)
