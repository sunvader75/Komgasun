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

    fun href(path: String): String = URI(null, null, "/$path", null).toASCIIString().removePrefix("/").replace(":", "%3A")

    fun resume(paths: List<String>, local: String?, remote: EpubProgressionDto?): Pair<Int, Double> {
        val saved = EpubReaderStorage.decodeLocator(local, paths.size)
        val position = remotePosition(paths, remote)
        val remoteTime = epoch(remote?.modified)
        val localTime = epoch(local?.split(':', limit = 3)?.getOrNull(2))
        if (position != null && remoteTime != null) {
            if (saved != null && localTime != null && localTime > remoteTime) return saved
            return position
        }
        return saved ?: (0 to 0.0)
    }

    fun remotePosition(paths: List<String>, remote: EpubProgressionDto?): Pair<Int, Double>? {
        val fraction = remote?.locator?.locations?.progression
        val path = try {
            val uri = URI(remote?.locator?.href ?: "")
            if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) null else uri.path
        } catch (_: Exception) { null }
        val index = paths.indexOf(path)
        return if (index >= 0 && fraction != null && fraction.isFinite() && fraction in 0.0..1.0 && epoch(remote?.modified) != null) index to fraction else null
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
