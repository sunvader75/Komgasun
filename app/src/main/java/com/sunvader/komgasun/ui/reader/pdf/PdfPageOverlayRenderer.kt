package com.sunvader.komgasun.ui.reader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.get

/**
 * Sustituye visualmente los bloques de texto detectados sobre una copia del [Bitmap] de la página,
 * tapando cada recuadro con el color de fondo estimado de la hoja y ajustando el texto traducido a su caja.
 */
object PdfPageOverlayRenderer {

    fun renderTranslatedPage(source: Bitmap, boxes: List<PdfTextBox>): Bitmap {
        val base = source.copy(Bitmap.Config.ARGB_8888, true)
        if (boxes.isEmpty()) return base

        val canvas = Canvas(base)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SERIF
        }

        val imgWidth = base.width
        val imgHeight = base.height
        val pad = 2

        // Primero muestreamos todos los colores de fondo desde `base` (ya en ARGB_8888, nunca HARDWARE)
        // antes de pintar ningún parche encima para no contaminar muestras vecinas ni fallar con Config.HARDWARE.
        val boxColors = IntArray(boxes.size) { i ->
            sampleBoxBackgroundColor(base, boxes[i])
        }
        for (i in boxes.indices) {
            val box = boxes[i]
            bgPaint.color = boxColors[i]
            val rect = RectF(
                (box.left - pad).coerceAtLeast(0).toFloat(),
                (box.top - pad).coerceAtLeast(0).toFloat(),
                (box.right + pad).coerceAtMost(imgWidth).toFloat(),
                (box.bottom + pad).coerceAtMost(imgHeight).toFloat()
            )
            canvas.drawRoundRect(rect, 3f, 3f, bgPaint)
        }

        // Luego dibujamos el texto traducido autoajustado dentro de cada recuadro.
        for (i in boxes.indices) {
            val box = boxes[i]
            val clampedLeft = box.left.coerceIn(0, imgWidth - 1)
            val clampedTop = box.top.coerceIn(0, imgHeight - 1)
            val clampedRight = box.right.coerceIn(clampedLeft + 1, imgWidth)
            val clampedBottom = box.bottom.coerceIn(clampedTop + 1, imgHeight)
            val availableWidth = (clampedRight - clampedLeft).coerceAtLeast(1)
            val availableHeight = (clampedBottom - clampedTop).coerceAtLeast(1)

            textPaint.color = chooseContrastingTextColor(boxColors[i])
            val bestSize = findBestFontSize(
                boxHeight = availableHeight,
                lineCount = box.lineCount
            ) { candidateSize ->
                textPaint.textSize = candidateSize
                val layout = buildStaticLayout(box.translatedText, textPaint, availableWidth)
                layout.height <= availableHeight && maxLineWidth(layout) <= availableWidth + 2f
            }

            textPaint.textSize = bestSize
            val layout = buildStaticLayout(box.translatedText, textPaint, availableWidth)

            canvas.withClip(clampedLeft, clampedTop, clampedRight, clampedBottom) {
                val verticalOffset = ((availableHeight - layout.height) / 2f).coerceAtLeast(0f)
                translate(clampedLeft.toFloat(), clampedTop.toFloat() + verticalOffset)
                layout.draw(this)
            }
        }

        return base
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.0f)
            .build()

    private fun maxLineWidth(layout: StaticLayout): Float {
        var max = 0f
        for (line in 0 until layout.lineCount) {
            val w = layout.getLineWidth(line)
            if (w > max) max = w
        }
        return max
    }

    private fun sampleBoxBackgroundColor(bitmap: Bitmap, box: PdfTextBox): Int {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0xFFFFFFFF.toInt()

        val left = (box.left - 2).coerceIn(0, w - 1)
        val right = (box.right + 2).coerceIn(0, w - 1)
        val top = (box.top - 2).coerceIn(0, h - 1)
        val bottom = (box.bottom + 2).coerceIn(0, h - 1)

        val samples = IntArray(36)
        var count = 0
        val steps = 8
        for (s in 0..steps) {
            val x = left + ((right - left) * s) / steps
            val y = top + ((bottom - top) * s) / steps
            samples[count++] = bitmap[x, top]
            samples[count++] = bitmap[x, bottom]
            samples[count++] = bitmap[left, y]
            samples[count++] = bitmap[right, y]
        }
        return chooseBackgroundColor(samples.copyOf(count))
    }
}

/**
 * Calcula el color de fondo por mediana de canales RGB para ignorar trazos de letras en el borde.
 */
internal fun chooseBackgroundColor(borderPixels: IntArray): Int {
    if (borderPixels.isEmpty()) return 0xFFFFFFFF.toInt()
    val n = borderPixels.size
    val reds = IntArray(n)
    val greens = IntArray(n)
    val blues = IntArray(n)
    for (i in 0 until n) {
        val c = borderPixels[i]
        reds[i] = (c shr 16) and 0xFF
        greens[i] = (c shr 8) and 0xFF
        blues[i] = c and 0xFF
    }
    reds.sort()
    greens.sort()
    blues.sort()
    val mid = n / 2
    val r = reds[mid]
    val g = greens[mid]
    val b = blues[mid]
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Devuelve tinta oscura sobre fondos claros y tinta clara sobre fondos oscuros según luminancia relativa.
 */
internal fun chooseContrastingTextColor(backgroundColor: Int): Int {
    val r = (backgroundColor shr 16) and 0xFF
    val g = (backgroundColor shr 8) and 0xFF
    val b = backgroundColor and 0xFF
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (luminance >= 0.5) {
        0xFF1C1C1C.toInt()
    } else {
        0xFFF2F2F2.toInt()
    }
}

/**
 * Busca por bisección el mayor tamaño de fuente que entra en la caja del bloque original.
 */
internal inline fun findBestFontSize(
    boxHeight: Int,
    lineCount: Int,
    minSize: Float = 6f,
    fitsAtSize: (Float) -> Boolean
): Float {
    val safeLines = lineCount.coerceAtLeast(1)
    val maxSize = (boxHeight.toFloat() / safeLines * 0.92f).coerceIn(minSize, 96f)
    if (fitsAtSize(maxSize)) return maxSize
    if (!fitsAtSize(minSize)) return minSize

    var low = minSize
    var high = maxSize
    repeat(8) {
        val mid = (low + high) * 0.5f
        if (fitsAtSize(mid)) {
            low = mid
        } else {
            high = mid
        }
    }
    return low
}
