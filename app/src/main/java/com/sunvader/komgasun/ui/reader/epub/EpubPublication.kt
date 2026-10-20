package com.sunvader.komgasun.ui.reader.epub

import java.io.File
import java.util.zip.ZipFile

open class EpubException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class EpubResource(val path: String, val mediaType: String, val bytes: ByteArray)

/** No extrae archivos ni retiene ZIP abiertos o una caché ilimitada; el propietario debe conservar el fichero fuente. */
class EpubPublication internal constructor(
    private val file: File,
    val packagePath: String,
    val mediaTypes: Map<String, String>
) {
    /** Calcula SHA-256 de todo el fichero en IO: incluye estilos, fuentes e imágenes, no solo capítulos. */
    internal fun paginationFingerprint(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Delega la resolución de referencias URI relativas en la validación de [EpubPath]. */
    fun resolve(basePath: String, reference: String): String = EpubPath.resolve(basePath, reference)

    /**
     * Lee un recurso relativo a un miembro ZIP canónico y decodificado; admite fragmentos en la referencia.
     * Abre y cierra el ZIP en cada lectura, aplica el límite de entrada y usa MIME genérico si no está
     * en el manifest. A diferencia de [readCanonicalResource], no exige pertenencia al manifest.
     */
    fun readResource(basePath: String, reference: String): EpubResource {
        val path = resolve(basePath, reference)
        return withArchive(file) { zip ->
            EpubResource(path, mediaTypes[path] ?: "application/octet-stream", readEntry(zip, path))
        }
    }

    /**
     * Devuelve el miembro de una URL exactamente canónica de `https://epub.invalid`, presente en el
     * manifest. Rechaza también variantes de codificación, consultas y fragmentos; lanza
     * [EpubResourceFailure] con una categoría fija. Validación JVM compartida por transporte y recuperación.
     */
    fun canonicalPath(source: String): String {
        fun reject(category: EpubResourceCategory): Nothing = throw EpubResourceFailure(category)
        if (source.isEmpty()) reject(EpubResourceCategory.EMPTY_SOURCE)
        val uri = try { java.net.URI(source) } catch (_: Exception) { reject(EpubResourceCategory.URI_OR_CANONICAL) }
        if (uri.scheme != "https" || uri.rawAuthority != "epub.invalid") reject(EpubResourceCategory.INVALID_SOURCE_ORIGIN)
        val path = try { resolve("", uri.rawPath.removePrefix("/")) } catch (_: Exception) { reject(EpubResourceCategory.URI_OR_CANONICAL) }
        val canonical = "https://epub.invalid/" + path.split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20").replace("%7E", "~").replace("%21", "!").replace("%27", "'")
                .replace("%28", "(").replace("%29", ")")
        }
        if (source != canonical) reject(EpubResourceCategory.URI_OR_CANONICAL)
        if (path !in mediaTypes) reject(EpubResourceCategory.NOT_MANIFEST)
        return path
    }

    /**
     * Valida origen, forma canónica y manifest antes de leer hasta 8 MiB, sin conservar el ZIP abierto.
     * Clasifica fichero ausente o ilegible y fallos de archivo, entrada o E/S mediante
     * [EpubResourceFailure]; no devuelve los mensajes originales como diagnóstico de transporte.
     */
    fun readCanonicalResource(source: String): EpubResource {
        val path = canonicalPath(source)
        if (!file.exists()) throw EpubResourceFailure(EpubResourceCategory.ARCHIVE_MISSING)
        if (!file.isFile || !file.canRead()) throw EpubResourceFailure(EpubResourceCategory.ARCHIVE_UNREADABLE)
        try {
            return withArchive(file) { zip -> EpubResource(path, mediaTypes.getValue(path), readEntry(zip, path)) }
        } catch (e: Exception) {
            val causes = generateSequence<Throwable>(e) { it.cause }.take(8).toList()
            causes.filterIsInstance<EpubResourceFailure>().firstOrNull()?.let { throw it }
            throw EpubResourceFailure(when {
                causes.any { it is java.util.zip.ZipException } -> EpubResourceCategory.INVALID_ARCHIVE
                causes.any { it is java.io.IOException } -> EpubResourceCategory.READ_IO
                else -> EpubResourceCategory.UNEXPECTED_CLASS
            })
        }
    }

    companion object {
        const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
        const val MAX_PARSE_BYTES = 32 * 1024 * 1024
        const val MAX_ENTRIES = 10000

        /**
         * Abre el ZIP solo durante [block], tras rechazar nombres duplicados o más de 10 000 entradas.
         * Cierra el archivo incluso si falla el bloque; conserva EpubException y envuelve otras excepciones.
         * No extrae miembros ni valida aquí sus rutas o contenido.
         */
        internal fun <T> withArchive(file: File, block: (ZipFile) -> T): T = try {
            ZipFile(file).use { zip ->
                val names = HashSet<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (names.size >= MAX_ENTRIES || !names.add(name)) throw EpubResourceFailure(EpubResourceCategory.INVALID_ARCHIVE, "EPUB con demasiadas entradas o rutas duplicadas")
                }
                block(zip)
            }
        } catch (e: EpubException) { throw e } catch (e: Exception) {
            throw EpubException("No se pudo leer el EPUB (ZIP inválido o contenido no compatible): ${e.message}", e)
        }

        /**
         * Lee un miembro existente, no directorio, con límite en bytes descomprimidos. Comprueba tanto
         * el tamaño declarado como los bytes reales, incluso con tamaño ZIP desconocido; cierra el flujo,
         * no el ZIP prestado. Lanza fallos categorizados si falta la entrada o rebasa [limit].
         */
        internal fun readEntry(zip: ZipFile, path: String, limit: Int = MAX_ENTRY_BYTES): ByteArray {
            val entry = zip.getEntry(path) ?: throw EpubResourceFailure(EpubResourceCategory.ENTRY_MISSING, "Falta el recurso EPUB: $path")
            if (entry.isDirectory || entry.size > limit) throw EpubResourceFailure(EpubResourceCategory.ENTRY_LIMIT, "Recurso EPUB demasiado grande o inválido: $path (límite $limit bytes)")
            return zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > limit - output.size()) throw EpubResourceFailure(EpubResourceCategory.ENTRY_LIMIT, "Recurso EPUB demasiado grande: $path (límite $limit bytes)")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }
}
