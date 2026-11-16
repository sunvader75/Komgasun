package com.sunvader.komgasun.ui.reader.pdf

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bloque de texto detectado en la página con su recuadro en píxeles del [Bitmap] original y su traducción.
 */
data class PdfTextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lineCount: Int,
    val translatedText: String
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
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
 * Conserva las coordenadas de cada bloque para poder sustituir el texto visualmente sobre la página.
 */
class MlKitPdfPageTranslator : PdfPageTranslator {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val translator: Translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        Translation.getClient(options)
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
            val visionText = recognizer.process(image).awaitResult()

            val rawCandidates = visionText.textBlocks.mapNotNull { block ->
                val rect = block.boundingBox ?: return@mapNotNull null
                val normalized = normalizeOcrBlock(block.text)
                if (normalized.isBlank() || rect.width() <= 0 || rect.height() <= 0) {
                    null
                } else {
                    Triple(rect, block.lines.size.coerceAtLeast(1), normalized)
                }
            }

            if (rawCandidates.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitResult()

            val translatedBoxes = ArrayList<PdfTextBox>(rawCandidates.size)
            for ((rect, lineCount, sourceText) in rawCandidates) {
                currentCoroutineContext().ensureActive()
                val translated = translator.translate(sourceText).awaitResult().trim()
                if (translated.isNotEmpty()) {
                    translatedBoxes.add(
                        PdfTextBox(
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            lineCount = lineCount,
                            translatedText = translated
                        )
                    )
                }
            }

            Result.success(translatedBoxes)
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
        try {
            translator.close()
        } catch (_: Exception) {
        }
    }
}

internal fun normalizeOcrBlock(rawText: String): String =
    rawText
        .replace(Regex("\\s*\\r?\\n\\s*"), " ")
        .trim()

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
