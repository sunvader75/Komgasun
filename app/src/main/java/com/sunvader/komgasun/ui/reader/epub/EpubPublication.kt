package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.util.zip.ZipFile

class EpubException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class EpubResource(val path: String, val mediaType: String, val bytes: ByteArray)

/** No extraction, open ZIP handles, or unbounded resource cache is retained. Keep the source file alive. */
class EpubPublication internal constructor(
    private val file: File,
    val packagePath: String,
    val mediaTypes: Map<String, String>
) {
    fun resolve(basePath: String, reference: String): String = EpubPath.resolve(basePath, reference)

    /** basePath is a decoded canonical ZIP member; reference is a relative URI (possibly fragmented). */
    fun readResource(basePath: String, reference: String): EpubResource {
        val path = resolve(basePath, reference)
        return withArchive(file) { zip ->
            EpubResource(path, mediaTypes[path] ?: "application/octet-stream", readEntry(zip, path))
        }
    }

    companion object {
        const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
        const val MAX_PARSE_BYTES = 32 * 1024 * 1024
        const val MAX_ENTRIES = 10000

        internal fun <T> withArchive(file: File, block: (ZipFile) -> T): T = try {
            ZipFile(file).use { zip ->
                val names = HashSet<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (names.size >= MAX_ENTRIES || !names.add(name)) throw EpubException("EPUB con demasiadas entradas o rutas duplicadas")
                }
                block(zip)
            }
        } catch (e: EpubException) { throw e } catch (e: Exception) {
            throw EpubException("No se pudo leer el EPUB (ZIP inválido o contenido no compatible): ${e.message}", e)
        }

        internal fun readEntry(zip: ZipFile, path: String, limit: Int = MAX_ENTRY_BYTES): ByteArray {
            val entry = zip.getEntry(path) ?: throw EpubException("Falta el recurso EPUB: $path")
            if (entry.isDirectory || entry.size > limit) throw EpubException("Recurso EPUB demasiado grande o inválido: $path (límite $limit bytes)")
            return zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > limit - output.size()) throw EpubException("Recurso EPUB demasiado grande: $path (límite $limit bytes)")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }
}
