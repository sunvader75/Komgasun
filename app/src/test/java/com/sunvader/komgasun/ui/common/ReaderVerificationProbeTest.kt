package com.sunvader.komgasun.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderVerificationProbeTest {

    private fun isLandscapeReduction(w: Float, h: Float, scale: Float) = w > h && scale < 1f
    private fun allowsOneFingerPan(w: Float, h: Float, scale: Float) =
        isReaderScaled(scale) && !isLandscapeReduction(w, h, scale)
    private fun isScrollEnabled(scale: Float, multiTouch: Boolean) =
        scale <= 1f && !multiTouch

    @Test
    fun probeLandscapeReductionUnblocksScrollAndDisablesZoomablePan() {
        val w = 1920f
        val h = 1080f
        val reducedScale = 0.7f

        assertTrue(isLandscapeReduction(w, h, reducedScale))
        assertFalse(allowsOneFingerPan(w, h, reducedScale))
        assertTrue(isScrollEnabled(reducedScale, multiTouch = false))
        assertFalse(isScrollEnabled(reducedScale, multiTouch = true))
        assertEquals(Offset.Zero, readerPageStartOffset(reducedScale, w, h, 800f, 1200f))
    }

    @Test
    fun probePortraitReductionAllowsPan() {
        val w = 1080f
        val h = 1920f
        val reducedScale = 0.7f

        assertFalse(isLandscapeReduction(w, h, reducedScale))
        assertTrue(allowsOneFingerPan(w, h, reducedScale))
    }

    @Test
    fun probeZoomedInAllowsPanAndDisablesVerticalScroll() {
        val w = 1920f
        val h = 1080f
        val enlargedScale = 2.5f

        assertFalse(isLandscapeReduction(w, h, enlargedScale))
        assertTrue(allowsOneFingerPan(w, h, enlargedScale))
        assertFalse(isScrollEnabled(enlargedScale, multiTouch = false))

        val pw = 1080f
        val ph = 1920f
        assertFalse(isLandscapeReduction(pw, ph, enlargedScale))
        assertTrue(allowsOneFingerPan(pw, ph, enlargedScale))
        assertFalse(isScrollEnabled(enlargedScale, multiTouch = false))
    }

    @Test
    fun probeNormalScaleLeavesOneFingerToPagerAndEnablesScroll() {
        val w = 1920f
        val h = 1080f
        val normalScale = 1.0f

        assertFalse(isLandscapeReduction(w, h, normalScale))
        assertFalse(allowsOneFingerPan(w, h, normalScale))
        assertTrue(isScrollEnabled(normalScale, multiTouch = false))
    }

    @Test
    fun probePageStartOffsetCalculationForEnlargedAndReduced() {
        assertEquals(Offset.Zero, readerPageStartOffset(0.5f, 1920f, 1080f, 800f, 1200f))
        assertEquals(Offset.Zero, readerPageStartOffset(1.0f, 1920f, 1080f, 800f, 1200f))

        // Max zoom 5.0f landscape
        val offsetLandscape = readerPageStartOffset(5.0f, 1920f, 1080f, 800f, 1200f)
        assertEquals(3840f, offsetLandscape.x, 0.01f)
        assertEquals(2160f, offsetLandscape.y, 0.01f)

        // Max zoom 5.0f portrait
        val offsetPortrait = readerPageStartOffset(5.0f, 1080f, 1920f, 800f, 1200f)
        assertEquals(2160f, offsetPortrait.x, 0.01f)
        assertEquals(3090f, offsetPortrait.y, 0.01f)
    }

    @Test
    fun probeScalePersistsWhileOffsetResetsOnPageChange() {
        val zoom = ReaderZoomState()
        zoom.scale.floatValue = 0.7f
        zoom.offset.value = Offset(10f, 20f)

        if (zoom.scale.floatValue <= 1f) {
            zoom.offset.value = Offset.Zero
        }
        assertEquals(0.7f, zoom.scale.floatValue, 0.001f)
        assertEquals(Offset.Zero, zoom.offset.value)

        zoom.scale.floatValue = 2.5f
        zoom.offset.value = Offset(500f, 200f)

        val newPageOffset = readerPageStartOffset(zoom.scale.floatValue, 2000f, 1000f, 1000f, 2000f)
        zoom.offset.value = newPageOffset

        assertEquals(2.5f, zoom.scale.floatValue, 0.001f)
        assertEquals(Offset(1500f, 750f), zoom.offset.value)
    }
}
