package com.sunvader.komgasun

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LoginNavKey : NavKey

@Serializable
data object LibrariesNavKey : NavKey

@Serializable
data class SeriesNavKey(val libraryId: String, val libraryName: String) : NavKey

@Serializable
data class BooksNavKey(val seriesId: String, val seriesTitle: String, val libraryId: String = "") : NavKey

@Serializable
data class ComicReaderNavKey(val bookId: String, val bookTitle: String) : NavKey

@Serializable
data class PdfReaderNavKey(val bookId: String, val bookTitle: String) : NavKey

@Serializable
data class EpubReaderNavKey(val bookId: String, val bookTitle: String) : NavKey
