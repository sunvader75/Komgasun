package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.sunvader.komgasun.ui.reader.ReaderDiagnostics
import org.junit.Rule
import org.junit.Test

class ComicDiagnosticsPanelTest {
    @get:Rule val compose = createComposeRule()

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    @Test fun entirePanelWaitsBeforeAppearing() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ComicDiagnosticsPanel(ReaderDiagnostics(), 1, { null to null }, Modifier.testTag("panel"))
        }
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(1400)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(200)
        compose.onNodeWithTag("panel").assertIsDisplayed()
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
    }

    @Test fun fastLoadCancelsTimerAndNextLoadingEpisodeWaitsAgain() {
        val displayed = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ComicDiagnosticsPanel(ReaderDiagnostics(), 1, { null to null }, Modifier.testTag("panel"), displayed.value)
        }
        advance(1000)
        compose.runOnIdle { displayed.value = true }
        advance(1000)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        compose.runOnIdle { displayed.value = false }
        advance(1400)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(200)
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
    }

    @Test fun rapidPageChangesResetTimerIncludingReturnToPreviousPage() {
        val page = mutableStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ComicDiagnosticsPanel(ReaderDiagnostics(), page.value, { null to null }, Modifier.testTag("panel"))
        }
        advance(1000)
        compose.runOnIdle { page.value = 2 }
        advance(1000)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        compose.runOnIdle { page.value = 1 }
        advance(1400)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(200)
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
        compose.onNodeWithText("Diagnóstico").performClick()
        compose.runOnIdle { page.value = 3 }
        advance(16)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(1600)
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
        compose.onNodeWithText("Copiar diagnóstico").assertDoesNotExist()
    }

    @Test fun successfulDisplayRemovesExpandedCopiedPanelAndResetsItForNextLoad() {
        val displayed = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ComicDiagnosticsPanel(ReaderDiagnostics(), 1, { null to null }, Modifier.testTag("panel"), displayed.value)
        }
        advance(1600)
        compose.onNodeWithText("Diagnóstico").performClick()
        advance(16)
        compose.onNodeWithText("Copiar diagnóstico").performClick()
        advance(16)
        compose.onNodeWithText("Diagnóstico copiado").assertIsDisplayed()
        compose.runOnIdle { displayed.value = true }
        advance(16)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        compose.runOnIdle { displayed.value = false }
        advance(16)
        compose.onNodeWithTag("panel").assertDoesNotExist()
        advance(1600)
        compose.onNodeWithText("Diagnóstico").performClick()
        advance(16)
        compose.onNodeWithText("Copiar diagnóstico").assertIsDisplayed()
        compose.onNodeWithText("Diagnóstico copiado").assertDoesNotExist()
    }

    @Test fun eventsDoNotRestartDelayAndExpandCopyRefreshAndCollapseStillWork() {
        val diagnostics = ReaderDiagnostics()
        diagnostics.start(1, ReaderDiagnostics.Kind.VISIBLE)
        compose.mainClock.autoAdvance = false
        compose.setContent { ComicDiagnosticsPanel(diagnostics, 1, { 10L to 100L }) }
        advance(1000)
        compose.runOnIdle { diagnostics.start(2, ReaderDiagnostics.Kind.PRELOAD) }
        advance(600)
        compose.onNodeWithText("Diagnóstico").performClick()
        advance(16)
        compose.onNodeWithText("Actualizar tiempos y memoria").performClick()
        advance(16)
        compose.onNodeWithText("Copiar diagnóstico").assertIsDisplayed().performClick()
        advance(16)
        compose.onNodeWithText("Diagnóstico copiado").assertIsDisplayed()
        compose.runOnIdle { diagnostics.start(3, ReaderDiagnostics.Kind.PRELOAD) }
        advance(16)
        compose.onNodeWithText("Diagnóstico copiado").assertIsDisplayed()
        compose.onNodeWithText("Ocultar diagnóstico").performClick()
        advance(16)
        compose.onNodeWithText("Copiar diagnóstico").assertDoesNotExist()
        compose.onNodeWithText("Diagnóstico").assertIsDisplayed()
    }
}
