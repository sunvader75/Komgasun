package com.sunvader.komgasun.ui.books

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.sunvader.komgasun.data.model.*
import org.junit.Rule
import org.junit.Test

class BookReadingStatusTest {
    @get:Rule val compose = createComposeRule()

    private fun show(book: BookDto) {
        compose.setContent {
            MaterialTheme {
                BookGridItem(book, "", {}, {}, showActions = false)
            }
        }
    }
    private fun progressCount(count: Int) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(count)
    }
    private fun book(page: Int = 0, completed: Boolean = false, name: String = "book.cbz", total: Int = 100, deleted: Boolean = false) =
        BookDto("1", "s", name = name, media = BookMediaDto(pagesCount = total), readProgress = ReadProgressDto(page, completed), deleted = deleted)

    @Test fun unreadPreservesFormatAndPageCountWithoutReadingIndicators() {
        show(book())
        compose.onNodeWithText("CBZ").assertIsDisplayed()
        compose.onNodeWithText("100 p.").assertIsDisplayed()
        compose.onNodeWithText("✓ Leído").assertDoesNotExist()
        progressCount(0)
    }
    @Test fun readingDisplaysTextAndDeterminateBar() {
        show(book(page = 35))
        compose.onNodeWithText("En lectura · 35 %").assertIsDisplayed()
        progressCount(1)
    }
    @Test fun completedAndUnavailableCoexistWithoutProgress() {
        show(book(completed = true, deleted = true))
        compose.onNodeWithText("✓ Leído").assertIsDisplayed()
        compose.onNodeWithText("No disponible").assertIsDisplayed()
        progressCount(0)
    }
    @Test fun epubHasGenericReadingStatusWithoutInventedChapter() {
        show(book(page = 35, name = "book.epub"))
        compose.onNodeWithText("En lectura").assertIsDisplayed()
        compose.onNodeWithText("100 p.").assertDoesNotExist()
        progressCount(0)
    }
    @Test fun epubReaderSummaryShowsBarPercentageAndReaderPageTotal() {
        show(book(page = 7, name = "book.epub", total = 999)
            .copy(epubProgress = EpubCoverProgress(0.35, 240)))
        compose.onNodeWithText("En lectura · 35 %").assertIsDisplayed()
        compose.onNodeWithText("240 p.").assertIsDisplayed()
        compose.onNodeWithText("999 p.").assertDoesNotExist()
        progressCount(1)
    }
    @Test fun epubRemoteFractionDoesNotInventPhysicalPageCount() {
        show(book(page = 7, name = "book.epub", total = 999)
            .copy(epubProgress = EpubCoverProgress(0.35)))
        compose.onNodeWithText("En lectura · 35 %").assertIsDisplayed()
        compose.onNodeWithText("999 p.").assertDoesNotExist()
        progressCount(1)
    }
    @Test fun pdfRetainsItsServerPageCountAndReadingPercentage() {
        show(book(page = 35, name = "book.pdf"))
        compose.onNodeWithText("En lectura · 35 %").assertIsDisplayed()
        compose.onNodeWithText("100 p.").assertIsDisplayed()
        progressCount(1)
    }
    @Test fun unknownTotalHasPositionWithoutPercentageBar() {
        show(book(page = 35, total = 0))
        compose.onNodeWithText("En lectura · Pág. 35").assertIsDisplayed()
        progressCount(0)
    }
}
