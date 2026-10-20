package com.sunvader.komgasun

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.sunvader.komgasun.data.model.BookType
import com.sunvader.komgasun.data.model.detectBookType
import com.sunvader.komgasun.ui.books.BooksScreen
import com.sunvader.komgasun.ui.common.LoadingView
import com.sunvader.komgasun.ui.libraries.LibrariesScreen
import com.sunvader.komgasun.ui.login.LoginScreen
import com.sunvader.komgasun.ui.reader.comic.ComicReaderScreen
import com.sunvader.komgasun.ui.reader.epub.EpubReaderScreen
import com.sunvader.komgasun.ui.reader.pdf.PdfReaderScreen
import com.sunvader.komgasun.ui.series.SeriesScreen

@Composable
fun MainNavigation() {
    val repository = remember { KomgasunApp.instance.repository }
    var isCheckingSession by remember { mutableStateOf(true) }
    var initialKey by remember { mutableStateOf<NavKey?>(null) }

    LaunchedEffect(Unit) {
        val config = repository.getActiveConfig()
        initialKey = if (config != null) LibrariesNavKey else LoginNavKey
        isCheckingSession = false
    }

    if (isCheckingSession || initialKey == null) {
        LoadingView(modifier = Modifier.fillMaxSize())
        return
    }

    AppNavigationGraph(startKey = initialKey!!)
}

@Composable
private fun AppNavigationGraph(startKey: NavKey) {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(startKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<LoginNavKey> {
                LoginScreen(
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(LibrariesNavKey)
                    }
                )
            }

            entry<LibrariesNavKey> {
                LibrariesScreen(
                    onBookClick = { book ->
                        val title = book.metadata.title.ifBlank { book.name }
                        val reader: NavKey = when (book.detectBookType()) {
                            BookType.PDF -> PdfReaderNavKey(book.id, title)
                            BookType.EPUB -> EpubReaderNavKey(book.id, title)
                            BookType.COMIC, BookType.UNKNOWN -> ComicReaderNavKey(book.id, title)
                        }
                        backStack.add(reader)
                    },
                    onLibraryClick = { libId, libName ->
                        backStack.add(SeriesNavKey(libId, libName))
                    },
                    onLogout = {
                        backStack.clear()
                        backStack.add(LoginNavKey)
                    }
                )
            }

            entry<SeriesNavKey> { key ->
                SeriesScreen(
                    libraryId = key.libraryId,
                    libraryName = key.libraryName,
                    onSeriesClick = { seriesId, seriesTitle ->
                        backStack.add(BooksNavKey(seriesId, seriesTitle, key.libraryId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<BooksNavKey> { key ->
                BooksScreen(
                    seriesId = key.seriesId,
                    seriesTitle = key.seriesTitle,
                    libraryId = key.libraryId,
                    onBookClick = { book ->
                        val title = book.metadata.title.ifBlank { book.name }
                        val readerNavKey: NavKey = when (book.detectBookType()) {
                            BookType.PDF -> PdfReaderNavKey(book.id, title)
                            BookType.EPUB -> EpubReaderNavKey(book.id, title)
                            BookType.COMIC, BookType.UNKNOWN -> ComicReaderNavKey(book.id, title)
                        }
                        backStack.add(readerNavKey)
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<ComicReaderNavKey> { key ->
                ComicReaderScreen(
                    bookId = key.bookId,
                    bookTitle = key.bookTitle,
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<PdfReaderNavKey> { key ->
                PdfReaderScreen(
                    bookId = key.bookId,
                    bookTitle = key.bookTitle,
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<EpubReaderNavKey> { key ->
                EpubReaderScreen(
                    bookId = key.bookId,
                    bookTitle = key.bookTitle,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
