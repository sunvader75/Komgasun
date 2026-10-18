package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.data.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpubProgressionSyncTest {
    private val paths = listOf("OEBPS/a b.xhtml", "OEBPS/two.xhtml")
    private fun remote(href: String = "OEBPS/a%20b.xhtml", fraction: Double = 0.35, modified: String = "2026-10-17T22:00:00.123Z") =
        EpubProgressionDto(modified, EpubDeviceDto("other", "Other"), EpubLocatorDto(href, "application/xhtml+xml", EpubLocationsDto(fraction)))

    @Test fun encodedHrefPreservesExactMemberWithoutBecomingAnExternalUri() {
        for (path in listOf("OEBPS/a b%20é#?.xhtml", "a:b.xhtml")) {
            val href = EpubProgressionSync.href(path)
            val uri = java.net.URI(href)
            assertFalse(uri.isAbsolute)
            assertNull(uri.fragment)
            assertNull(uri.query)
            assertEquals(path, uri.path)
            assertEquals(0 to 0.35, EpubProgressionSync.resume(listOf(path), null, remote(href)))
        }
    }
    @Test fun remoteResourceFractionWinsOverLegacyLocalPosition() {
        assertEquals(0 to 0.35, EpubProgressionSync.resume(paths, "1:0.8", remote()))
    }
    @Test fun localFallbackDoesNotDependOnServerPageOrCompletion() {
        assertEquals(1 to 0.8, EpubProgressionSync.resume(paths, "1:0.8", null))
        assertEquals(0 to 0.0, EpubProgressionSync.resume(paths, "999:NaN", null))
    }
    @Test fun invalidRemoteFallsBackWithoutGuessingChapter() {
        for (r in listOf(remote("missing.xhtml"), remote(fraction = Double.NaN), remote(fraction = -0.1), remote(fraction = 1.1), remote("https://outside/a"))) {
            assertEquals(1 to 0.8, EpubProgressionSync.resume(paths, "1:0.8", r))
        }
    }
    @Test fun boundariesAndFragmentsResolveToExactResource() {
        assertEquals(0 to 0.0, EpubProgressionSync.resume(paths, null, remote("OEBPS/a%20b.xhtml#start", 0.0)))
        assertEquals(1 to 1.0, EpubProgressionSync.resume(paths, null, remote("OEBPS/two.xhtml", 1.0)))
    }
    @Test fun timestampedLocalOnlyWinsWhenStrictlyNewer() {
        val newer = "2026-10-17T22:01:00.123Z"
        val older = "2026-10-17T21:59:00.123Z"
        assertEquals(1 to 0.8, EpubProgressionSync.resume(paths, "1:0.8:$newer", remote()))
        assertEquals(0 to 0.35, EpubProgressionSync.resume(paths, "1:0.8:$older", remote()))
        assertEquals(0 to 0.35, EpubProgressionSync.resume(paths, "1:0.8:2026-10-17T22:00:00.123Z", remote()))
        assertEquals(1 to 0.8, EpubProgressionSync.resume(paths, "1:0.8:$newer", remote(modified = "invalid")))
    }
    @Test fun queueCoalescesAndSerializesWhileRequestIsInFlight() = runTest {
        val sent = mutableListOf<Double?>()
        val release = CompletableDeferred<Unit>()
        var confirmations = 0
        val queue = EpubProgressionQueue(this, send = {
            sent += it.locator.locations?.progression
            if (sent.size == 1) release.await()
            Result.success(Unit)
        }, onResult = { confirmations++ })
        queue.offer(remote(fraction = 0.1))
        queue.offer(remote(fraction = 0.2))
        advanceTimeBy(351)
        runCurrent()
        assertEquals(listOf(0.2), sent)
        queue.offer(remote(fraction = 0.3))
        queue.offer(remote(fraction = 0.4))
        runCurrent()
        assertEquals(listOf(0.2), sent)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(0.2, 0.4), sent)
        assertEquals(2, confirmations)
        queue.close()
    }
    @Test fun rejectionStopsQueueWithoutRetryOrFalseConfirmation() = runTest {
        var calls = 0
        val outcomes = mutableListOf<Boolean>()
        val queue = EpubProgressionQueue(this, send = { calls++; Result.failure(java.io.IOException("HTTP 400: Response")) }, onResult = { outcomes += it.isSuccess })
        queue.offer(remote())
        advanceUntilIdle()
        queue.offer(remote(fraction = 0.9))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf(false), outcomes)
        queue.close()
    }
    @Test fun closeCancelsPendingWritesAndStaleCallbacks() = runTest {
        var calls = 0
        var results = 0
        val release = CompletableDeferred<Unit>()
        val queue = EpubProgressionQueue(this, send = { calls++; release.await(); Result.success(Unit) }, onResult = { results++ })
        queue.offer(remote())
        advanceTimeBy(351)
        runCurrent()
        assertEquals(1, calls)
        queue.offer(remote(fraction = 0.9))
        queue.close()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(0, results)
    }
    @Test fun utcTimestampSupportsOffsetsAndRejectsGarbage() {
        assertEquals(EpubProgressionSync.epoch("2026-10-17T22:00:00.123Z"), EpubProgressionSync.epoch("2026-10-18T00:00:00.123+02:00"))
        assertNull(EpubProgressionSync.epoch("not a timestamp"))
        assertNull(EpubProgressionSync.epoch("2026-02-30T00:00:00Z"))
    }
    @Test fun discreteProgressionMatchesKomgaPositionAlgorithm() {
        // Single position (< 1024 bytes): always 0.0, never 1.0 (avoids Komga's after == null error)
        assertEquals(0.0, EpubProgressionSync.discreteProgression(0.0, 500), 0.0001)
        assertEquals(0.0, EpubProgressionSync.discreteProgression(0.5, 500), 0.0001)
        assertEquals(0.0, EpubProgressionSync.discreteProgression(1.0, 500), 0.0001)

        // 5 positions (5000 bytes -> ceil(5000/1024) = 5): discrete positions are 0.0, 0.2, 0.4, 0.6, 0.8
        assertEquals(0.0, EpubProgressionSync.discreteProgression(0.0, 5000), 0.0001)
        assertEquals(0.2, EpubProgressionSync.discreteProgression(0.25, 5000), 0.0001)
        assertEquals(0.4, EpubProgressionSync.discreteProgression(0.5, 5000), 0.0001)
        assertEquals(0.6, EpubProgressionSync.discreteProgression(0.75, 5000), 0.0001)
        assertEquals(0.8, EpubProgressionSync.discreteProgression(1.0, 5000), 0.0001)

        // Out of bound / NaN inputs clamp safely
        assertEquals(0.0, EpubProgressionSync.discreteProgression(Double.NaN, 5000), 0.0001)
        assertEquals(0.0, EpubProgressionSync.discreteProgression(-0.5, 5000), 0.0001)
        assertEquals(0.8, EpubProgressionSync.discreteProgression(1.5, 5000), 0.0001)
    }
    @Test fun continuousProportionRestoresReadingProportionFromDiscrete() {
        assertEquals(0.0, EpubProgressionSync.continuousProportion(0.0, 500), 0.0001)
        assertEquals(0.0, EpubProgressionSync.continuousProportion(0.0, 5000), 0.0001)
        assertEquals(0.5, EpubProgressionSync.continuousProportion(0.4, 5000), 0.0001)
        assertEquals(1.0, EpubProgressionSync.continuousProportion(0.8, 5000), 0.0001)
    }
    @Test fun remotePositionToleratesLeadingSlashesAndExpandsDiscreteProgression() {
        val sizes = listOf(5000L, 1000L)
        // With size, 0.8 on chapter 0 (5000 bytes) maps back to 1.0 proportion
        assertEquals(0 to 1.0, EpubProgressionSync.remotePosition(paths, remote("OEBPS/a%20b.xhtml", 0.8), sizes))
        // Leading slash on locator href is tolerated
        assertEquals(0 to 1.0, EpubProgressionSync.remotePosition(paths, remote("/OEBPS/a%20b.xhtml", 0.8), sizes))
        // Leading slash on paths list is tolerated
        val slashPaths = listOf("/OEBPS/a b.xhtml", "/OEBPS/two.xhtml")
        assertEquals(0 to 1.0, EpubProgressionSync.remotePosition(slashPaths, remote("OEBPS/a%20b.xhtml", 0.8), sizes))
    }
}
