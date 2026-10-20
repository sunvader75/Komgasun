package com.sunvader.komgasun.data.remote

import com.sunvader.komgasun.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ReadStatusTest {
    /**
     * Ejecuta el cambio de estado contra un servidor de prueba de una sola petición.
     * Responde con [status] y devuelve la línea de petición y el cuerpo separados por
     * un salto de línea, junto con el resultado de la API. Comprueba que termina el hilo servidor.
     */
    private fun request(completed: Boolean, status: Int = 204): Pair<String, Result<Unit>> {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            var captured = ""
            val responder = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers += line
                    }
                    val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }
                        ?.substringAfter(":")?.trim()?.toInt() ?: 0
                    val body = CharArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = input.read(body, offset, length - offset)
                        check(count > 0)
                        offset += count
                    }
                    captured = headers.first() + "\n" + String(body)
                    socket.getOutputStream().write(
                        "HTTP/1.1 $status Response\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                    )
                }
            }
            val result = runBlocking {
                KomgaApiClient().setBookReadStatus(
                    ServerConfig("http://127.0.0.1:${server.localPort}", "user", "password"),
                    "book-1", completed
                )
            }
            responder.join(6000)
            assertFalse("Server did not finish", responder.isAlive)
            return captured to result
        }
    }

    @Test fun marksReadOnServer() {
        val (request, result) = request(true)
        assertTrue(result.isSuccess)
        assertEquals("PATCH /api/v1/books/book-1/read-progress HTTP/1.1\n{\"completed\":true}", request)
    }

    @Test fun resetsProgressOnServer() {
        val (request, result) = request(false)
        assertTrue(result.isSuccess)
        assertEquals("DELETE /api/v1/books/book-1/read-progress HTTP/1.1\n", request)
    }

    @Test fun reportsServerRejection() {
        val (_, result) = request(false, 403)
        assertTrue(result.isFailure)
        assertEquals("HTTP 403: Response", result.exceptionOrNull()?.message)
    }
}
