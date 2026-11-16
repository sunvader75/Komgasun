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
 * Contrato para extraer texto de una página renderizada como [Bitmap] y traducirlo bajo demanda.
 */
interface PdfPageTranslator {
    suspend fun translatePage(bitmap: Bitmap): Result<String>
    fun close()
}

/**
 * Implementación local basada en ML Kit (OCR de caracteres latinos + traducción EN → ES en el dispositivo).
 * Descarga el modelo de traducción la primera vez que se solicita si aún no está presente en el dispositivo.
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

    override suspend fun translatePage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).awaitResult()
            val blocks = visionText.textBlocks
                .map { block -> normalizeOcrBlock(block.text) }
                .filter { it.isNotBlank() }

            if (blocks.isEmpty()) {
                return@withContext Result.success("")
            }

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitResult()

            val translatedBlocks = ArrayList<String>(blocks.size)
            for (block in blocks) {
                currentCoroutineContext().ensureActive()
                translatedBlocks.add(translator.translate(block).awaitResult())
            }

            Result.success(translatedBlocks.joinToString(separator = "\n\n"))
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
