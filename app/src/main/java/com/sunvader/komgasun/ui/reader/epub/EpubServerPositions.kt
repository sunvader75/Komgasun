package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.data.model.EpubLocatorDto
import com.sunvader.komgasun.data.model.EpubLocationsDto
import com.sunvader.komgasun.data.model.EpubPositionsDto
import java.net.URI
import kotlin.math.abs
import kotlin.math.roundToInt

/** No deduce las posiciones guardadas por Komga de los tamaños del ZIP actual. */
class EpubServerPositions private constructor(
    private val chapters: List<List<EpubLocatorDto>>
) {
    /** Elige por índice la posición publicada más cercana y conserva solo progression en locations; devuelve null si no es utilizable. */
    fun locator(chapter: Int, proportion: Double): EpubLocatorDto? {
        if (!proportion.isFinite() || proportion !in 0.0..1.0) return null
        val positions = chapters.getOrNull(chapter) ?: return null
        if (positions.isEmpty()) return null
        val index = (proportion * (positions.size - 1)).roundToInt().coerceIn(0, positions.size - 1)
        val published = positions[index]
        return published.copy(locations = EpubLocationsDto(progression = published.locations!!.progression))
    }

    /** Convierte la progresión a la fracción del índice publicado más cercano; capítulo sin posiciones da null. */
    fun proportion(chapter: Int, progression: Double): Double? {
        if (!progression.isFinite() || progression !in 0.0..1.0) return null
        val positions = chapters.getOrNull(chapter) ?: return null
        if (positions.size == 1) return 0.0
        val index = positions.indices.minByOrNull { abs(positions[it].locations!!.progression!! - progression) } ?: return null
        return index.toDouble() / (positions.size - 1)
    }

    companion object {
        /**
         * Valida total (1..100 000), progresiones y tipos; asocia href por ruta literal, decodificada
         * o sufijo compatible con los capítulos. Descarta rutas no asociadas, ordena y quita duplicados
         * por progresión. Exige al menos un capítulo con posiciones, no cobertura completa del libro.
         * Lanza IllegalArgumentException si la respuesta no cumple estos requisitos.
         */
        fun create(paths: List<String>, response: EpubPositionsDto): EpubServerPositions {
            require(paths.isNotEmpty() && paths.none { it.isBlank() })
            require(response.total in 1..100_000 && response.total == response.positions.size)
            val knownPaths = paths.map { it.removePrefix("/") }
            val knownSet = knownPaths.toSet()
            val grouped = response.positions.mapNotNull { position ->
                val value = position.locations?.progression
                require(value != null && value.isFinite() && value in 0.0..1.0 && position.type.isNotBlank())
                val raw = position.href.removePrefix("/")
                val decoded = runCatching {
                    val uri = URI(position.href)
                    if (!uri.isAbsolute && uri.rawAuthority == null && uri.rawQuery == null && uri.rawFragment == null) {
                        uri.path?.removePrefix("/")
                    } else null
                }.getOrNull()
                val candidate = raw.takeIf { it in knownSet }
                    ?: decoded?.takeIf { it in knownSet }
                    ?: knownPaths.firstOrNull { it.endsWith("/$raw") || raw.endsWith("/$it") || (decoded != null && (it.endsWith("/$decoded") || decoded.endsWith("/$it"))) }
                    ?: return@mapNotNull null
                val resource = if (position.href.startsWith("/")) "/$candidate" else candidate
                candidate to position.copy(href = EpubProgressionSync.href(resource))
            }.groupBy({ it.first }, { it.second })
            val chapters = paths.map { rawPath ->
                val cleanPath = rawPath.removePrefix("/")
                grouped[cleanPath]?.sortedBy { it.locations!!.progression }?.distinctBy { it.locations!!.progression }.orEmpty()
            }
            require(chapters.any { it.isNotEmpty() }) { "Komga positions do not match this EPUB" }
            return EpubServerPositions(chapters)
        }
    }
}
