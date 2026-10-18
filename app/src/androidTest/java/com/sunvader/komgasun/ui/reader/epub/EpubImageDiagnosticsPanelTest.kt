package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class EpubImageDiagnosticsPanelTest {
    @get:Rule val compose = createComposeRule()
    @Test fun absentUntilReportThenExpandableAndCopyable() {
        val report = mutableStateOf("")
        compose.setContent { EpubImageDiagnosticsPanel(report.value) }
        compose.onNodeWithText("Diagnóstico de imágenes").assertDoesNotExist()
        compose.runOnIdle { report.value = EpubImageDiagnostics(0, true, 0, 0, 0, 0, "UNKNOWN", "REJECTED").report() }
        compose.onNodeWithText("Diagnóstico de imágenes").performClick()
        compose.onNodeWithText(report.value).assertIsDisplayed()
        compose.onNodeWithText("Copiar diagnóstico").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle { report.value = "" }
        compose.onNodeWithText("Diagnóstico de imágenes").assertDoesNotExist()
    }
}
