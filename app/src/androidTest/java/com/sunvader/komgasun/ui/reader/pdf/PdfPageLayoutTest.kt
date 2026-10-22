package com.sunvader.komgasun.ui.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PdfPageLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeFitsWidthScrollsAndResetsOnPageChange() {
        val page = mutableStateOf(0)
        compose.setContent {
            Box(Modifier.requiredSize(300.dp, 150.dp)) {
                PdfPageContent(600f, 1200f, page.value, true) {
                    Box(it.testTag("page"))
                }
            }
        }
        compose.onNodeWithTag("page").assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(600.dp)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(scrollOffset() > 0f)
        compose.runOnIdle { page.value++ }
        compose.waitForIdle()
        assertEquals(0f, scrollOffset(), 0f)
    }

    @Test fun portraitRemainsViewportSized() {
        compose.setContent {
            Box(Modifier.requiredSize(150.dp, 300.dp)) {
                PdfPageContent(600f, 1200f, 0, true) { Box(it.testTag("page")) }
            }
        }
        compose.onNodeWithTag("page").assertWidthIsEqualTo(150.dp).assertHeightIsEqualTo(300.dp)
    }

    private fun scrollOffset(): Float = compose.onNode(hasScrollAction()).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
}
