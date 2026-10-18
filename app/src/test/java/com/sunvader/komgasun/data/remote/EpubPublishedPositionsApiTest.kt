package com.sunvader.komgasun.data.remote

import com.sunvader.komgasun.data.model.*
import com.sunvader.komgasun.ui.reader.epub.EpubProgressionSync
import com.sunvader.komgasun.ui.reader.epub.EpubServerPositions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class EpubPublishedPositionsApiTest {
    @Test fun publishedPositionPassesKomgaValidationWhenByteDerivedPositionIsRejected() {
        // Reproduce the rejection contract for a different/stale server analysis, not a live server claim.
        val path = "OEBPS/Text/capitulo01.xhtml"
        val published = EpubPositionsDto(3, listOf(0.0, 0.33333334, 0.6666667).map {
            EpubLocatorDto(path, "application/xhtml+xml", EpubLocationsDto(it))
        })
        val writes = AtomicInteger(0)
        val saved = AtomicReference<Double?>()
        val failure = AtomicReference<Throwable?>()
        val requests = mutableListOf<String>()
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val responder = thread {
                try {
                    repeat(3) {
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val input = socket.getInputStream().bufferedReader()
                            val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                            val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }
                                ?.substringAfter(':')?.trim()?.toInt() ?: 0
                            val body = CharArray(length)
                            var offset = 0
                            while (offset < length) {
                                val count = input.read(body, offset, length - offset)
                                check(count > 0)
                                offset += count
                            }
                            assertTrue(headers.any { it == "Authorization: Basic dXNlcjpwYXNzd29yZA==" })
                            val request = headers.first()
                            requests += request
                            val (status, response) = if (request.startsWith("GET ")) {
                                assertEquals("GET /komga/api/v1/books/book/positions HTTP/1.1", request)
                                200 to Json.encodeToString(published)
                            } else {
                                assertEquals("PUT /komga/api/v1/books/book/progression HTTP/1.1", request)
                                val locator = Json.decodeFromString<EpubProgressionDto>(String(body)).locator
                                val value = locator.locations!!.progression!!.toFloat()
                                val values = published.positions.map { it.locations!!.progression!!.toFloat() }
                                // Same resource and exact-match / before-after rules as BookLifecycle.markProgression.
                                val valid = locator.href == path && (value in values || (values.any { it < value } && values.any { it > value }))
                                if (valid) {
                                    writes.incrementAndGet()
                                    saved.set(locator.locations.progression)
                                    204 to ""
                                } else 400 to "{\"message\":\"Invalid progression\"}"
                            }
                            val bytes = response.toByteArray()
                            socket.getOutputStream().write("HTTP/1.1 $status Response\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                        }
                    }
                } catch (error: Throwable) { failure.set(error) }
            }
            runBlocking {
                val client = KomgaApiClient(okhttp3.OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build())
                val config = ServerConfig("http://127.0.0.1:${server.localPort}/komga/", "user", "password")
                val positions = client.getEpubPositions(config, "book").getOrThrow()
                fun progress(locator: EpubLocatorDto) = EpubProgressionDto("2026-10-17T22:00:00.123Z", EpubDeviceDto("test", "Test"), locator)
                val old = EpubLocatorDto(path, "application/xhtml+xml", EpubLocationsDto(EpubProgressionSync.discreteProgression(1.0, 12477)))
                val rejected = client.putEpubProgression(config, "book", progress(old))
                assertTrue(rejected.isFailure)
                assertTrue(rejected.exceptionOrNull()!!.message!!.startsWith("HTTP 400:"))
                assertEquals(0, writes.get())
                assertNull(saved.get())
                val locator = EpubServerPositions.create(listOf(path), positions).locator(0, 1.0)!!
                assertTrue(client.putEpubProgression(config, "book", progress(locator)).isSuccess)
                assertEquals(1, writes.get())
                assertEquals(0.6666667, saved.get()!!, 0.0)
            }
            responder.join(6000)
            assertFalse(responder.isAlive)
            failure.get()?.let { throw AssertionError("Mock server failed", it) }
            assertEquals(3, requests.size)
            assertTrue(requests.none { it.startsWith("PATCH ") })
        }
    }
}
