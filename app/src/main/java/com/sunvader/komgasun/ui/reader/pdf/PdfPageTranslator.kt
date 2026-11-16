package com.sunvader.komgasun.ui.reader.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sunvader.komgasun.ui.reader.MlKitTextTranslator
import com.sunvader.komgasun.ui.reader.TextBlockTranslator
import com.sunvader.komgasun.ui.reader.awaitTaskResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Línea visual reconstruida a partir de los segmentos de OCR que comparten la misma fila horizontal.
 */
internal data class OcrVisualLine(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Float get() = (top + bottom) * 0.5f
}

/**
 * Párrafo individual detectado dentro de un bloque o columna de la página.
 */
internal data class OcrParagraph(
    val text: String,
    val indented: Boolean
)

/**
 * Bloque bruto detectado por OCR con sus párrafos separados por geometría de líneas.
 */
internal data class OcrRawBlock(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lineCount: Int,
    val paragraphs: List<OcrParagraph>
) {
    constructor(
        left: Int,
        top: Int,
        right: Int,
        valBottom: Int,
        lineCount: Int,
        text: String
    ) : this(
        left = left,
        top = top,
        right = right,
        bottom = valBottom,
        lineCount = lineCount,
        paragraphs = listOf(OcrParagraph(text = text, indented = false))
    )

    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val lineHeight: Float get() = height.toFloat() / lineCount.coerceAtLeast(1)
    val text: String get() = paragraphs.joinToString(separator = "\n") { it.text }
}

/**
 * Bloque de texto posicionado con su recuadro de borrado original y su recuadro expandido de maquetación.
 */
data class PdfTextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lineCount: Int,
    val translatedText: String,
    val eraseLeft: Int = left,
    val eraseTop: Int = top,
    val eraseRight: Int = right,
    val eraseBottom: Int = bottom
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val originalHeight: Int get() = (eraseBottom - eraseTop).coerceAtLeast(1)
}

/**
 * Contrato para extraer bloques de texto posicionados de una página renderizada como [Bitmap] y traducirlos.
 */
interface PdfPageTranslator {
    suspend fun translatePageBlocks(bitmap: Bitmap): Result<List<PdfTextBox>>
    fun close()
}

/**
 * Implementación local basada en ML Kit (OCR de caracteres latinos + traducción EN → ES en el dispositivo).
 * Reconstruye líneas visuales y párrafos con sangría, fusiona bloques de la misma columna y aprovecha el espacio libre.
 */
class MlKitPdfPageTranslator(
    private val textTranslator: TextBlockTranslator = MlKitTextTranslator()
) : PdfPageTranslator {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun translatePageBlocks(bitmap: Bitmap): Result<List<PdfTextBox>> = withContext(Dispatchers.IO) {
        try {
            val softwareBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val image = InputImage.fromBitmap(softwareBitmap, 0)
            val visionText = recognizer.process(image).awaitTaskResult()

            val rawBlocks = visionText.textBlocks.mapNotNull { block ->
                val rect = block.boundingBox ?: return@mapNotNull null
                if (rect.width() <= 0 || rect.height() <= 0) return@mapNotNull null

                val rawLines = block.lines.mapNotNull { line ->
                    val lr = line.boundingBox ?: return@mapNotNull null
                    val cleaned = normalizeOcrBlock(line.text)
                    if (cleaned.isBlank() || lr.width() <= 0 || lr.height() <= 0) {
                        null
                    } else {
                        OcrVisualLine(
                            left = lr.left,
                            top = lr.top,
                            right = lr.right,
                            bottom = lr.bottom,
                            text = cleaned
                        )
                    }
                }

                val visualLines = mergeSameRowLines(rawLines)
                val paragraphs = if (visualLines.isNotEmpty()) {
                    extractParagraphsFromVisualLines(visualLines, rect.left, rect.right)
                } else {
                    val fallback = normalizeOcrBlock(block.text)
                    if (fallback.isBlank()) emptyList() else listOf(OcrParagraph(fallback, indented = false))
                }

                if (paragraphs.isEmpty()) {
                    null
                } else {
                    OcrRawBlock(
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                        lineCount = visualLines.size.coerceAtLeast(1),
                        paragraphs = paragraphs
                    )
                }
            }

            if (rawBlocks.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val mergedBlocks = mergeContiguousOcrBlocks(rawBlocks, softwareBitmap.width)

            val allParagraphTexts = mergedBlocks.flatMap { block -> block.paragraphs.map { it.text } }
            val translatedParagraphs = textTranslator.translateBlocks(allParagraphTexts).getOrThrow()

            var cursor = 0
            val initialBoxes = mergedBlocks.mapNotNull { block ->
                val count = block.paragraphs.size
                val formattedParagraphs = ArrayList<String>(count)
                for (i in 0 until count) {
                    val p = block.paragraphs[i]
                    val tr = translatedParagraphs.getOrNull(cursor++)?.trim().orEmpty()
                    if (tr.isNotEmpty()) {
                        val prefix = if (p.indented || (count > 1 && i > 0)) "\u2003" else ""
                        formattedParagraphs.add(prefix + tr)
                    }
                }
                val joinedText = formattedParagraphs.joinToString(separator = "\n")
                if (joinedText.isBlank()) {
                    null
                } else {
                    PdfTextBox(
                        left = block.left,
                        top = block.top,
                        right = block.right,
                        bottom = block.bottom,
                        lineCount = block.lineCount,
                        translatedText = joinedText,
                        eraseLeft = block.left,
                        eraseTop = block.top,
                        eraseRight = block.right,
                        eraseBottom = block.bottom
                    )
                }
            }

            Result.success(
                expandBoxesToPageSpace(
                    boxes = initialBoxes,
                    pageWidth = softwareBitmap.width,
                    pageHeight = softwareBitmap.height
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
        textTranslator.close()
    }
}

internal fun normalizeOcrBlock(rawText: String): String =
    rawText
        .replace(Regex("\\s*\\r?\\n\\s*"), " ")
        .trim()

/**
 * Une segmentos de línea que ML Kit partió en la misma fila horizontal (p. ej. por guiones largos o cursivas).
 */
internal fun mergeSameRowLines(lines: List<OcrVisualLine>): List<OcrVisualLine> {
    if (lines.size <= 1) return lines
    val sorted = lines.sortedWith(compareBy<OcrVisualLine> { it.top }.thenBy { it.left })
    val rows = ArrayList<OcrVisualLine>(sorted.size)
    for (line in sorted) {
        val last = rows.lastOrNull()
        val refHeight = if (last != null) min(last.height, line.height).coerceAtLeast(1) else line.height
        if (last != null && abs(line.centerY - last.centerY) <= refHeight * 0.55f) {
            val leftFirst = if (last.left <= line.left) last.text else line.text
            val rightSecond = if (last.left <= line.left) line.text else last.text
            rows[rows.lastIndex] = OcrVisualLine(
                left = min(last.left, line.left),
                top = min(last.top, line.top),
                right = max(last.right, line.right),
                bottom = max(last.bottom, line.bottom),
                text = "$leftFirst $rightSecond"
            )
        } else {
            rows.add(line)
        }
    }
    return rows
}

/**
 * Agrupa las líneas visuales de un bloque en párrafos reales.
 * IMPORTANTE: En libros impresos la distancia en blanco entre renglones consecutivos (`line.top - prev.bottom`)
 * suele ser igual al alto de las letras (`avgLineHeight`), por lo que jamás debe usarse como salto de párrafo.
 * Un párrafo nuevo dentro de un bloque se detecta por sangría de primera línea (`isIndented`) o por un paso
 * entre centros de línea (`linePitch`) al menos un 45 % mayor que el paso mediano del bloque.
 */
internal fun extractParagraphsFromVisualLines(
    lines: List<OcrVisualLine>,
    blockLeft: Int,
    blockRight: Int
): List<OcrParagraph> {
    if (lines.isEmpty()) return emptyList()
    val blockWidth = (blockRight - blockLeft).coerceAtLeast(1)
    if (lines.size == 1) {
        return listOf(OcrParagraph(text = lines[0].text, indented = false))
    }

    val indentThreshold = (blockWidth * 0.04f).toInt().coerceAtLeast(14)
    val pitches = (1 until lines.size)
        .map { i -> (lines[i].centerY - lines[i - 1].centerY).coerceAtLeast(1f) }
        .sorted()
    val medianPitch = pitches[pitches.size / 2]

    val paragraphs = ArrayList<OcrParagraph>()
    val currentText = StringBuilder()
    var currentIndented = (lines[0].left - blockLeft) >= indentThreshold

    for (i in lines.indices) {
        val line = lines[i]
        if (i == 0) {
            currentText.append(line.text)
            continue
        }
        val prev = lines[i - 1]
        val isIndented = (line.left - blockLeft) >= indentThreshold && (line.left - prev.left) >= indentThreshold / 2
        val pitch = line.centerY - prev.centerY
        val hasExtraParagraphSpace = pitch > medianPitch * 1.45f

        if (isIndented || hasExtraParagraphSpace) {
            val built = finishHyphenatedParagraph(currentText.toString())
            if (built.isNotBlank()) {
                paragraphs.add(OcrParagraph(text = built, indented = currentIndented))
            }
            currentText.setLength(0)
            currentText.append(line.text)
            currentIndented = isIndented
        } else {
            appendLineContinuation(currentText, line.text)
        }
    }

    val lastBuilt = finishHyphenatedParagraph(currentText.toString())
    if (lastBuilt.isNotBlank()) {
        paragraphs.add(OcrParagraph(text = lastBuilt, indented = currentIndented))
    }
    return paragraphs
}

private fun appendLineContinuation(builder: StringBuilder, nextLine: String) {
    if (builder.isEmpty()) {
        builder.append(nextLine)
        return
    }
    if (builder.length >= 2 && builder.endsWith("-") && !builder.endsWith("—") && !builder.endsWith("--") &&
        builder[builder.length - 2].isLetter() && nextLine.firstOrNull()?.isLowerCase() == true
    ) {
        builder.setLength(builder.length - 1)
        builder.append(nextLine)
    } else {
        builder.append(' ').append(nextLine)
    }
}

private fun finishHyphenatedParagraph(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()

/**
 * Une bloques verticales contiguos que pertenecen a la misma columna de prosa conservando sus párrafos.
 */
internal fun mergeContiguousOcrBlocks(blocks: List<OcrRawBlock>, pageWidth: Int): List<OcrRawBlock> {
    if (blocks.size <= 1) return blocks
    val sorted = blocks.sortedWith(compareBy<OcrRawBlock> { it.top }.thenBy { it.left })
    val merged = ArrayList<OcrRawBlock>(sorted.size)
    val maxLeftDelta = (pageWidth * 0.08f).toInt().coerceAtLeast(16)

    for (candidate in sorted) {
        val prev = merged.lastOrNull()
        if (prev != null && shouldMergeBlocks(prev, candidate, maxLeftDelta)) {
            val combinedParagraphs = ArrayList<OcrParagraph>(prev.paragraphs.size + candidate.paragraphs.size)
            combinedParagraphs.addAll(prev.paragraphs)
            candidate.paragraphs.forEachIndexed { idx, p ->
                combinedParagraphs.add(if (idx == 0) p.copy(indented = true) else p)
            }
            merged[merged.lastIndex] = OcrRawBlock(
                left = min(prev.left, candidate.left),
                top = min(prev.top, candidate.top),
                right = max(prev.right, candidate.right),
                bottom = max(prev.bottom, candidate.bottom),
                lineCount = prev.lineCount + candidate.lineCount,
                paragraphs = combinedParagraphs
            )
        } else {
            merged.add(candidate)
        }
    }
    return merged
}

private fun shouldMergeBlocks(a: OcrRawBlock, b: OcrRawBlock, maxLeftDelta: Int): Boolean {
    val verticalGap = b.top - a.bottom
    val refLineHeight = max(a.lineHeight, b.lineHeight)
    if (verticalGap < -refLineHeight * 0.3f || verticalGap > refLineHeight * 0.75f) return false

    val lineRatio = a.lineHeight / b.lineHeight.coerceAtLeast(1f)
    if (lineRatio !in 0.65f..1.50f) return false

    val overlapWidth = min(a.right, b.right) - max(a.left, b.left)
    val minWidth = min(a.width, b.width).coerceAtLeast(1)
    if (overlapWidth < minWidth * 0.5f) return false

    return abs(a.left - b.left) <= maxLeftDelta
}

/**
 * Expande las cajas de maquetación hacia el margen derecho de la columna y el hueco vertical libre
 * inferior para que la traducción al español aproveche toda la caja disponible de la página.
 */
internal fun expandBoxesToPageSpace(
    boxes: List<PdfTextBox>,
    pageWidth: Int,
    pageHeight: Int
): List<PdfTextBox> {
    if (boxes.isEmpty() || pageWidth <= 0 || pageHeight <= 0) return boxes

    val bodyRightEdge = boxes
        .filter { it.width >= pageWidth * 0.35f }
        .maxOfOrNull { it.right }
        ?: (pageWidth * 0.90f).toInt()
    val maxPageRight = (pageWidth * 0.95f).toInt().coerceAtLeast(bodyRightEdge)
    val maxPageBottom = (pageHeight * 0.96f).toInt()

    return boxes.mapIndexed { index, box ->
        val lineHeight = (box.originalHeight.toFloat() / box.lineCount.coerceAtLeast(1)).coerceAtLeast(12f)

        val obstacleRight = boxes
            .filterIndexed { otherIdx, other ->
                otherIdx != index &&
                    other.left >= box.right - 4 &&
                    min(box.bottom, other.bottom) > max(box.top, other.top)
            }
            .minOfOrNull { it.left - 8 }

        val targetRight = if (box.lineCount >= 2 || box.width >= pageWidth * 0.30f) {
            max(box.right, bodyRightEdge).coerceAtMost(maxPageRight)
        } else {
            (box.right + (box.width * 0.25f).toInt()).coerceAtMost(maxPageRight)
        }
        val expandedRight = (obstacleRight?.let { min(targetRight, it) } ?: targetRight)
            .coerceAtLeast(box.right)

        val nextTopBelow = boxes
            .filterIndexed { otherIdx, other ->
                otherIdx != index &&
                    other.top >= box.bottom - 2 &&
                    min(expandedRight, other.right) > max(box.left, other.left)
            }
            .minOfOrNull { it.top }
            ?: maxPageBottom

        val freeVerticalGap = (nextTopBelow - box.bottom - 4).coerceAtLeast(0)
        val maxExtraHeight = (lineHeight * 2.5f).toInt().coerceAtLeast(12)
        val extraBottom = min((freeVerticalGap * 0.88f).toInt(), maxExtraHeight)
        val expandedBottom = (box.bottom + extraBottom).coerceAtMost(maxPageBottom).coerceAtLeast(box.bottom)

        box.copy(
            right = expandedRight,
            bottom = expandedBottom
        )
    }
}
