package com.sunvader.komgasun.data.remote

import com.sunvader.komgasun.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class EpubProgressionTest {
    private val payload = EpubProgressionDto("2026-10-17T22:00:00.123Z", EpubDeviceDto("stable-private-client-id", "Komgasun"),
        EpubLocatorDto("OEBPS/chapter.xhtml", "application/xhtml+xml", EpubLocationsDto(0.35)))
    private val body = """{"modified":"2026-10-17T22:00:00.123Z","device":{"id":"stable-private-client-id","name":"Komgasun"},"locator":{"href":"OEBPS/chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.35}}}"""
    private fun exchange(status: Int, response: String = "", put: Boolean = false, positions: Boolean = false): Pair<String, Result<*>> {
        ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
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
                    val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(":")?.trim()?.toInt() ?: 0
                    val chars = CharArray(length)
                    var offset = 0
                    while (offset < length) { val n = input.read(chars, offset, length - offset); check(n > 0); offset += n }
                    captured = headers.joinToString("\n") + "\n\n" + String(chars)
                    val bytes = response.toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 $status Response\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                }
            }
            val result = runBlocking {
                val client = KomgaApiClient(okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build())
                val config = ServerConfig("http://127.0.0.1:${server.localPort}/komga/", "user", "password")
                if (positions) client.getEpubPositions(config, "book-1")
                else if (put) client.putEpubProgression(config, "book-1", payload) else client.getEpubProgression(config, "book-1")
            }
            responder.join(6000)
            assertFalse(responder.isAlive)
            assertTrue("Missing authenticated request; result=$result", captured.contains("Authorization: Basic dXNlcjpwYXNzd29yZA=="))
            return captured to result
        }
    }
    @Test fun get200DecodesLocatorAndExtraServerFields() {
        val extra = body.dropLast(1) + ",\"unknown\":true}"
        val (request, result) = exchange(200, extra)
        assertEquals("GET /komga/api/v1/books/book-1/progression HTTP/1.1", request.lineSequence().first())
        assertEquals("", request.substringAfter("\n\n"))
        assertEquals(payload, result.getOrThrow())
    }
    @Test fun get204IsNoSavedLocatorNotAnError() {
        val (_, result) = exchange(204)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
    @Test fun putUsesExactDocumentedBodyWithoutPageOrCompleted() {
        val (request, result) = exchange(204, put = true)
        assertEquals("PUT /komga/api/v1/books/book-1/progression HTTP/1.1", request.lineSequence().first())
        assertTrue(request.contains("Content-Type: application/json"))
        assertEquals(body, request.substringAfter("\n\n"))
        assertTrue(result.isSuccess)
    }
    @Test fun errorsAreExplicitAndNeverFallbackToPatch() {
        for (put in listOf(false, true)) for (code in listOf(400, 401, 403, 404, 409, 500)) {
            val (request, result) = exchange(code, put = put)
            assertTrue(request.startsWith(if (put) "PUT " else "GET "))
            assertTrue(result.isFailure)
            assertEquals("HTTP $code: Response", result.exceptionOrNull()?.message)
        }
    }
    @Test fun positionsGetPreservesPublishedValuesAndRejectsIncompleteAnalysis() {
        val locator = """{"href":"OEBPS/chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.6666667}}"""
        val (request, result) = exchange(200, """{"total":1,"positions":[$locator],"unknown":true}""", positions = true)
        assertEquals("GET /komga/api/v1/books/book-1/positions HTTP/1.1", request.lineSequence().first())
        assertEquals("", request.substringAfter("\n\n"))
        assertEquals(0.6666667, (result.getOrThrow() as EpubPositionsDto).positions.single().locations!!.progression!!, 0.0)
        for (bad in listOf("{}", """{"total":2,"positions":[$locator]}""", """{"total":0,"positions":[]}""")) {
            assertTrue(exchange(200, bad, positions = true).second.isFailure)
        }
        val (missing, failure) = exchange(404, positions = true)
        assertTrue(missing.startsWith("GET "))
        assertTrue(failure.isFailure)
    }

    @Test fun malformedSuccessIsNotClaimedAsSavedPosition() {
        assertTrue(exchange(200, "{}").second.isFailure)
    }
}
