package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubImageRecoveryTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
    @Test fun signaturesAndActivePayloads() {
        assertTrue(EpubImageRecovery.validRaster("image/png", png))
        assertFalse(EpubImageRecovery.validRaster("image/jpeg", png))
        assertFalse(EpubImageRecovery.validRaster("image/svg+xml", "<svg/>".toByteArray()))
        assertFalse(EpubImageRecovery.validRaster("image/png", "<script/>".toByteArray()))
        assertFalse(EpubImageRecovery.validRaster("image/gif", byteArrayOf()))
        assertTrue(EpubImageRecovery.validRaster("image/jpeg", byteArrayOf(-1, -40, -1)))
        assertTrue(EpubImageRecovery.validRaster("image/gif", "GIF89a".toByteArray()))
        assertTrue(EpubImageRecovery.validRaster("image/webp", "RIFF1234WEBP".toByteArray()))
    }
    @Test fun attemptsGenerationAndBudgetAreBounded() {
        val policy = EpubImageRecovery(7)
        assertFalse(policy.claim(8, 0))
        assertTrue(policy.claim(7, 0))
        assertFalse(policy.claim(7, 0))
        assertFalse(policy.claim(7, -1))
        assertFalse(policy.claim(7, 256))
        assertFalse(policy.reserve(8, 1))
        assertFalse(policy.reserve(7, 8 * 1024 * 1024 + 1))
        assertTrue(policy.reserve(7, 8 * 1024 * 1024))
        assertTrue(policy.reserve(7, 8 * 1024 * 1024))
        assertFalse(policy.reserve(7, 1))
    }
}
