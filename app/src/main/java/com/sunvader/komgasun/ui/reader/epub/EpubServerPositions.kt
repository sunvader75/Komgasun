package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.data.model.EpubLocatorDto
import com.sunvader.komgasun.data.model.EpubLocationsDto
import com.sunvader.komgasun.data.model.EpubPositionsDto
import java.net.URI
import kotlin.math.abs
import kotlin.math.roundToInt

/** Never infer Komga's stored position set from the current ZIP's byte lengths. */
class EpubServerPositions private constructor(
    private val chapters: List<List<EpubLocatorDto>>
) {
    fun locator(chapter: Int, proportion: Double): EpubLocatorDto? {
        if (!proportion.isFinite() || proportion !in 0.0..1.0) return null
        val positions = chapters.getOrNull(chapter) ?: return null
        val index = (proportion * (positions.size - 1)).roundToInt()
        val published = positions[index]
        return published.copy(locations = EpubLocationsDto(progression = published.locations!!.progression))
    }

    fun proportion(chapter: Int, progression: Double): Double? {
        if (!progression.isFinite() || progression !in 0.0..1.0) return null
        val positions = chapters.getOrNull(chapter) ?: return null
        if (positions.size == 1) return 0.0
        val index = positions.indices.minByOrNull { abs(positions[it].locations!!.progression!! - progression) } ?: return null
        return index.toDouble() / (positions.size - 1)
    }

    companion object {
        fun create(paths: List<String>, response: EpubPositionsDto): EpubServerPositions {
            require(paths.isNotEmpty() && paths.none { it.isBlank() } && paths.distinct().size == paths.size)
            require(response.total in 1..100_000 && response.total == response.positions.size)
            val known = paths.toSet()
            val grouped = response.positions.map { position ->
                val value = position.locations?.progression
                require(value != null && value.isFinite() && value in 0.0..1.0 && position.type.isNotBlank())
                // Komga may publish raw member names, including spaces and URI delimiters.
                val raw = position.href.removePrefix("/")
                val resource = if (raw in known) position.href else runCatching {
                    val uri = URI(position.href)
                    require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawQuery == null && uri.rawFragment == null)
                    uri.path
                }.getOrNull()
                val path = resource?.removePrefix("/")
                require(path in known) { "Komga positions do not match this EPUB" }
                path!! to position.copy(href = EpubProgressionSync.href(resource!!))
            }.groupBy({ it.first }, { it.second })
            val chapters = paths.map { path ->
                val positions = grouped[path]?.sortedBy { it.locations!!.progression }
                require(!positions.isNullOrEmpty()) { "Komga has no positions for this chapter" }
                require(positions.map { it.locations!!.progression }.distinct().size == positions.size)
                positions
            }
            return EpubServerPositions(chapters)
        }
    }
}
