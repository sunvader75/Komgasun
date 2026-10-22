package com.sunvader.komgasun.ui.reader.epub

import kotlin.math.cos
import kotlin.math.sin

/** Reusable cylindrical sheet in camera space; no Android objects or frame allocations.
 * Backward turns bring the destination sheet back over the stationary current page.
 * The surface stays front-facing, like the broad text-bearing curl in the reference.
 */
internal class EpubCurlGeometry {
    companion object {
        const val COLUMNS = 64
        const val ROWS = 16
    }

    val vertices = FloatArray((COLUMNS + 1) * (ROWS + 1) * 2)
    val colors = IntArray((COLUMNS + 1) * (ROWS + 1))
    var edge = 0f
        private set
    var lift = 0f
        private set

    fun update(width: Float, height: Float, progress: Float, direction: Int, dark: Boolean) {
        val p = progress.coerceIn(0f, 1f)
        val t = if (direction > 0) p else 1f - p
        lift = if (t == 0f || t == 1f) 0f else sin(Math.PI * t).toFloat()
        val angle = 1.3f * lift
        for (column in 0..COLUMNS) {
            val u = column.toFloat() / COLUMNS
            val theta = angle * u
            val depth = 1f - cos(theta)
            // Perspective enlarges the lifted edge and curves lines across the page.
            val perspective = 1f / (1f - .12f * depth)
            val projected = if (angle == 0f) width * u else width * sin(theta) / angle * perspective
            val x = projected - width * t
            if (column == COLUMNS) edge = x
            val shade = (255f * (1f - (if (dark) .13f else .24f) * depth)).toInt().coerceIn(0, 255)
            val color = (255 shl 24) or (shade shl 16) or (shade shl 8) or shade
            for (row in 0..ROWS) {
                val index = row * (COLUMNS + 1) + column
                vertices[index * 2] = x
                vertices[index * 2 + 1] = height * .5f + (height * row / ROWS - height * .5f) * perspective
                colors[index] = color
            }
        }
    }
}
