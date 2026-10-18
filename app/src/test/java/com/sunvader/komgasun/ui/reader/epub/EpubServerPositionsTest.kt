package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class EpubServerPositionsTest {
    private val path = "OEBPS/Text/capitulo01.xhtml"
    private fun positions(href: String = path) = EpubPositionsDto(3, listOf(0.0, 0.33333334, 0.6666667).mapIndexed { i, p ->
        EpubLocatorDto(href, "application/xhtml+xml", EpubLocationsDto(p, (i + 1) / 3.0, i + 1))
    })

    @Test fun endOfChapterUsesExactPublishedValueNotAReconstructedByteCount() {
        // The supplied chapter has 12477 bytes, but only the server knows its stored analysis.
        val guessed = EpubProgressionSync.discreteProgression(1.0, 12477)
        assertTrue(guessed > positions().positions.last().locations!!.progression!!)
        val server = EpubServerPositions.create(listOf(path), positions())
        assertEquals(0.0, server.locator(0, 0.0)!!.locations!!.progression!!, 0.0)
        assertEquals(0.33333334, server.locator(0, 0.5)!!.locations!!.progression!!, 0.0)
        assertEquals(0.6666667, server.locator(0, 1.0)!!.locations!!.progression!!, 0.0)
        assertEquals(1.0, server.proportion(0, 0.6666667)!!, 0.0)
        assertNull(server.locator(0, Double.NaN))
        assertNull(server.locator(1, 0.5))
    }

    @Test fun canonicalResourcesRoundTripThroughKomgaUrlDecoderIncludingLiteralPlus() {
        val member = "OEBPS/Text/a+b é#?.xhtml"
        for (href in listOf(member, EpubProgressionSync.href(member), "/$member", EpubProgressionSync.href("/$member"))) {
            val server = EpubServerPositions.create(listOf(member), positions(href))
            val locator = server.locator(0, 1.0)!!
            val expected = if (href.startsWith("/")) "/$member" else member
            assertEquals(expected, URLDecoder.decode(locator.href, "UTF-8"))
            assertNull(java.net.URI(locator.href).fragment)
            assertNull(java.net.URI(locator.href).query)
        }
    }

    @Test fun resumeUsesPublishedPositionsRatherThanLocalBytesAndKeepsTimestampPreference() {
        val server = EpubServerPositions.create(listOf(path), positions())
        val remote = EpubProgressionDto("2026-10-17T22:00:00.123Z", EpubDeviceDto("test", "Test"),
            EpubLocatorDto(path, "application/xhtml+xml", EpubLocationsDto(0.6666667)))
        assertEquals(0 to 1.0, EpubProgressionSync.resume(listOf(path), null, remote, listOf(12477L), server))
        assertEquals(0 to 0.25, EpubProgressionSync.resume(listOf(path), "0:0.25:2026-10-17T22:01:00.123Z", remote, listOf(12477L), server))
    }

    @Test fun malformedOrMismatchedSetsNeverProduceGuessedLocators() {
        for (bad in listOf(
            positions().copy(total = 4),
            positions().copy(total = 0, positions = emptyList()),
            positions("other.xhtml"),
            positions().copy(positions = positions().positions.map { it.copy(locations = EpubLocationsDto(Double.NaN)) })
        )) assertThrows(IllegalArgumentException::class.java) { EpubServerPositions.create(listOf(path), bad) }
        val single = EpubServerPositions.create(listOf(path), EpubPositionsDto(1, listOf(positions().positions.first())))
        assertEquals(0.0, single.locator(0, 1.0)!!.locations!!.progression!!, 0.0)
        assertEquals(0.0, single.proportion(0, 0.0)!!, 0.0)
    }
}
