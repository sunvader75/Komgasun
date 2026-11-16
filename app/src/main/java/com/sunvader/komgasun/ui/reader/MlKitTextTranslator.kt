package com.sunvader.komgasun.ui.reader

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Contrato común de traducción de lotes de bloques de texto en el dispositivo (EN → ES).
 */
interface TextBlockTranslator {
    suspend fun translateBlocks(blocks: List<String>): Result<List<String>>
    fun close()
}

/**
 * Traductor local basado en ML Kit On-Device Translation (inglés a español).
 * Descarga el modelo la primera vez que se solicita y luego traduce íntegramente en el dispositivo.
 */
class MlKitTextTranslator : TextBlockTranslator {
    private val translator: Translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        Translation.getClient(options)
    }

    override suspend fun translateBlocks(blocks: List<String>): Result<List<String>> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTaskResult()

            val results = ArrayList<String>(blocks.size)
            for (block in blocks) {
                currentCoroutineContext().ensureActive()
                val trimmed = block.trim()
                if (trimmed.isEmpty()) {
                    results.add("")
                } else {
                    results.add(translator.translate(trimmed).awaitTaskResult().trim())
                }
            }
            Result.success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun close() {
        try {
            translator.close()
        } catch (_: Exception) {
        }
    }
}

internal suspend fun <T> Task<T>.awaitTaskResult(): T = suspendCancellableCoroutine { cont ->
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
