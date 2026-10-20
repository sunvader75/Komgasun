package com.sunvader.komgasun.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String,
    val seriesTitle: String = "",
    val name: String,
    val number: Int = 0,
    val media: BookMediaDto = BookMediaDto(),
    val metadata: BookMetadataDto = BookMetadataDto(),
    val readProgress: ReadProgressDto? = null,
    val deleted: Boolean = false,
    val sizeBytes: Long = 0,
    @kotlinx.serialization.Transient val epubProgress: EpubCoverProgress? = null
)

@Serializable
data class ReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String? = null
)

@Serializable
data class BookMediaDto(
    val status: String = "",
    val mediaType: String = "",
    val pagesCount: Int = 0,
    val comment: String = ""
)

@Serializable
data class BookMetadataDto(
    val title: String = "",
    val number: String = "",
    val releaseDate: String? = null,
    val summary: String = ""
)

enum class BookType {
    COMIC,
    PDF,
    EPUB,
    UNKNOWN
}

/** Deduce el lector por MIME o extensión, priorizando PDF y EPUB; usa COMIC si no reconoce el formato. */
fun BookDto.detectBookType(): BookType {
    val type = media.mediaType.lowercase()
    val nameLower = name.lowercase()
    return when {
        type.contains("pdf") || nameLower.endsWith(".pdf") -> BookType.PDF
        type.contains("epub") || nameLower.endsWith(".epub") -> BookType.EPUB
        type.contains("comic") || type.contains("zip") || type.contains("rar") ||
            nameLower.endsWith(".cbz") || nameLower.endsWith(".cbr") || nameLower.endsWith(".zip") -> BookType.COMIC
        else -> BookType.COMIC
    }
}

/** Devuelve la etiqueta de formato por MIME o extensión, o LIBRO si no identifica PDF, EPUB, RAR o ZIP. */
fun BookDto.formatBadge(): String {
    val type = media.mediaType.lowercase()
    val nameLower = name.lowercase()
    return when {
        type.contains("pdf") || nameLower.endsWith(".pdf") -> "PDF"
        type.contains("epub") || nameLower.endsWith(".epub") -> "EPUB"
        type.contains("rar") || nameLower.endsWith(".cbr") -> "CBR"
        type.contains("zip") || nameLower.endsWith(".cbz") -> "CBZ"
        else -> "LIBRO"
    }
}
