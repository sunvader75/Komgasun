package com.sunvader.komgasun.ui.reader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min

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
        val pad = 3

        // 1. Muestreamos todos los colores de fondo desde `base` (ARGB_8888) antes de pintar encima.
        val boxColors = IntArray(boxes.size) { i ->
            sampleBoxBackgroundColor(base, boxes[i])
        }

        // 2. Tapamos los recuadros originales del texto en inglés.
        for (i in boxes.indices) {
            val box = boxes[i]
            bgPaint.color = boxColors[i]
            val eraseRect = RectF(
                (box.eraseLeft - pad).coerceAtLeast(0).toFloat(),
                (box.eraseTop - pad).coerceAtLeast(0).toFloat(),
                (box.eraseRight + pad).coerceAtMost(imgWidth).toFloat(),
                (box.eraseBottom + pad).coerceAtMost(imgHeight).toFloat()
            )
            canvas.drawRoundRect(eraseRect, 3f, 3f, bgPaint)
        }

        // 3. Para cada bloque buscamos el mayor tamaño de fuente que llena su altura disponible y lo dibujamos.
        for (i in boxes.indices) {
            val box = boxes[i]
            val clampedLeft = box.left.coerceIn(0, imgWidth - 1)
            val clampedTop = box.top.coerceIn(0, imgHeight - 1)
            val clampedRight = box.right.coerceIn(clampedLeft + 1, imgWidth)
            val clampedBottom = box.bottom.coerceIn(clampedTop + 1, imgHeight)
            val availableWidth = (clampedRight - clampedLeft).coerceAtLeast(1)
            val availableHeight = (clampedBottom - clampedTop).coerceAtLeast(1)
            val justify = box.lineCount >= 2 && availableWidth >= imgWidth * 0.40f

            val bestSize = findBestFontSize(
                boxHeight = availableHeight,
                lineCount = box.lineCount
            ) { candidateSize ->
                textPaint.textSize = candidateSize
                val layout = buildStaticLayout(box.translatedText, textPaint, availableWidth, justify)
                layout.height <= availableHeight
            }

            textPaint.textSize = bestSize
            textPaint.color = chooseContrastingTextColor(boxColors[i])
            val layout = buildStaticLayout(box.translatedText, textPaint, availableWidth, justify)

            val drawnWidth = min(availableWidth.toFloat(), maxLineWidth(layout) + pad * 2)
            val drawnHeight = min(availableHeight.toFloat(), layout.height.toFloat() + pad)
            bgPaint.color = boxColors[i]
            canvas.drawRoundRect(
                RectF(
                    (clampedLeft - pad).coerceAtLeast(0).toFloat(),
                    (clampedTop - pad).coerceAtLeast(0).toFloat(),
                    (clampedLeft + drawnWidth).coerceAtMost(imgWidth.toFloat()),
                    (clampedTop + drawnHeight).coerceAtMost(imgHeight.toFloat())
                ),
                3f,
                3f,
                bgPaint
            )

            canvas.save()
            canvas.clipRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
            val verticalOffset = if (layout.lineCount == 1 && layout.height < box.originalHeight) {
                ((box.originalHeight - layout.height) / 2f).coerceAtLeast(0f)
            } else {
                0f
            }
            canvas.translate(clampedLeft.toFloat(), clampedTop.toFloat() + verticalOffset)
            layout.draw(canvas)
            canvas.restore()
        }

        return base
    }

    private fun buildStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        justify: Boolean
    ): StaticLayout {
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.12f)
        if (justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
    }

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

        val left = (box.eraseLeft - 2).coerceIn(0, w - 1)
        val right = (box.eraseRight + 2).coerceIn(0, w - 1)
        val top = (box.eraseTop - 2).coerceAtLeast(0).coerceAtMost(h - 1)
        val bottom = (box.eraseBottom + 2).coerceIn(0, h - 1)

        val samples = IntArray(36)
        var count = 0
        val steps = 8
        for (s in 0..steps) {
            val x = left + ((right - left) * s) / steps
            val y = top + ((bottom - top) * s) / steps
            samples[count++] = bitmap.getPixel(x, top)
            samples[count++] = bitmap.getPixel(x, bottom)
            samples[count++] = bitmap.getPixel(left, y)
            samples[count++] = bitmap.getPixel(right, y)
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
 * Busca por bisección el mayor tamaño de fuente real que entra en la caja disponible,
 * sin limitar el tope superior por el número de segmentos de línea reportados por el OCR.
 */
internal inline fun findBestFontSize(
    boxHeight: Int,
    @Suppress("UNUSED_PARAMETER") lineCount: Int = 1,
    minSize: Float = 8f,
    fitsAtSize: (Float) -> Boolean
): Float {
    val maxSize = boxHeight.toFloat().coerceIn(minSize, 160f)
    if (fitsAtSize(maxSize)) return maxSize
    if (!fitsAtSize(minSize)) return minSize

    var low = minSize
    var high = maxSize
    repeat(10) {
        val mid = (low + high) * 0.5f
        if (fitsAtSize(mid)) {
            low = mid
        } else {
            high = mid
        }
    }
    return low
}
