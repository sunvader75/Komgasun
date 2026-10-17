package com.sunvader.komgasun.data.remote

import com.sunvader.komgasun.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class InProgressBooksTest {
    @Test fun loadsAllPagesOfInProgressBooks() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val requests = mutableListOf<String>()
            val responder = thread {
                repeat(2) { page ->
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        requests += reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = """{"content":[{"id":"book-$page","seriesId":"series","name":"Test.cbz","readProgress":{"page":3,"completed":false}}],"totalPages":2,"number":$page}"""
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                    }
                }
            }
            val result = runBlocking {
                KomgaApiClient().getInProgressBooks(ServerConfig("http://127.0.0.1:${server.localPort}", "u", "p"))
            }
            responder.join(6000)
            assertFalse(responder.isAlive)
            assertTrue(result.isSuccess)
            assertEquals(listOf("book-0", "book-1"), result.getOrThrow().map { it.id })
            assertEquals(listOf(
                "GET /api/v1/books?read_status=IN_PROGRESS&size=500&page=0 HTTP/1.1",
                "GET /api/v1/books?read_status=IN_PROGRESS&size=500&page=1 HTTP/1.1"
            ), requests)
        }
    }
}
