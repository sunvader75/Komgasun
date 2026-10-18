package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.data.model.EpubProgressionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Pure resume policy: server page numbers are never EPUB chapter indices. */
object EpubProgressionSync {
    private fun formatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    fun timestamp(millis: Long): String = formatter().format(Date(millis))

    fun epoch(value: String?): Long? {
        val match = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$").matchEntire(value ?: return null) ?: return null
        val normalized = match.groupValues[1] + "." + match.groupValues[2].padEnd(3, '0').take(3) + match.groupValues[3]
        return try { formatter().parse(normalized)?.time } catch (_: Exception) { null }
    }

    fun href(path: String): String = URI(null, null, "/$path", null).toASCIIString().removePrefix("/").replace(":", "%3A").replace("+", "%2B")

    /**
     * Maps continuous reading proportion [0.0..1.0] to a discrete Readium progression
     * matching Komga's position set (1 position per 1024 bytes).
     */
    fun discreteProgression(proportion: Double, resourceBytes: Long): Double {
        if (!proportion.isFinite()) return 0.0
        val count = kotlin.math.ceil(resourceBytes.coerceAtLeast(0) / 1024.0).toInt().coerceAtLeast(1)
        if (count <= 1) return 0.0
        val index = (proportion.coerceIn(0.0, 1.0) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        return index.toDouble() / count
    }

    /**
     * Restores continuous reading proportion [0.0..1.0] from a discrete Readium progression.
     */
    fun continuousProportion(progression: Double, resourceBytes: Long): Double {
        if (!progression.isFinite()) return 0.0
        val count = kotlin.math.ceil(resourceBytes.coerceAtLeast(0) / 1024.0).toInt().coerceAtLeast(1)
        if (count <= 1) return 0.0
        return (progression.coerceIn(0.0, 1.0) * count / (count - 1)).coerceIn(0.0, 1.0)
    }

    fun resume(paths: List<String>, local: String?, remote: EpubProgressionDto?, sizes: List<Long> = emptyList(), positions: EpubServerPositions? = null): Pair<Int, Double> {
        val saved = EpubReaderStorage.decodeLocator(local, paths.size)
        val position = remotePosition(paths, remote, sizes, positions)
        val remoteTime = epoch(remote?.modified)
        val localTime = epoch(local?.split(':', limit = 3)?.getOrNull(2))
        if (position != null && remoteTime != null) {
            if (saved != null && localTime != null && localTime > remoteTime) return saved
            return position
        }
        return saved ?: (0 to 0.0)
    }

    fun remotePosition(paths: List<String>, remote: EpubProgressionDto?, sizes: List<Long> = emptyList(), positions: EpubServerPositions? = null): Pair<Int, Double>? {
        val fraction = remote?.locator?.locations?.progression
        val path = try {
            val uri = URI(remote?.locator?.href ?: "")
            if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) null else uri.path?.removePrefix("/")
        } catch (_: Exception) { null }
        val index = paths.map { it.removePrefix("/") }.indexOf(path)
        if (index < 0 || fraction == null || !fraction.isFinite() || fraction !in 0.0..1.0 || epoch(remote.modified) == null) {
            return null
        }
        val size = sizes.getOrNull(index) ?: 0L
        val proportion = positions?.proportion(index, fraction)
            ?: if (size > 1024L) continuousProportion(fraction, size) else fraction
        return index to proportion
    }
}

/** One in-flight PUT, one latest pending locator, no retry/polling after any failure. */
class EpubProgressionQueue(
    scope: CoroutineScope,
    private val send: suspend (EpubProgressionDto) -> Result<Unit>,
    private val onResult: (Result<Unit>) -> Unit
) {
    private val pending = Channel<EpubProgressionDto>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (first in pending) {
            delay(350)
            var latest = first
            while (true) { latest = pending.tryReceive().getOrNull() ?: break }
            val result = send(latest)
            onResult(result)
            if (result.isFailure) { pending.close(); break }
        }
    }
    fun offer(value: EpubProgressionDto) { pending.trySend(value) }
    fun close() { pending.close(); worker.cancel() }
}
