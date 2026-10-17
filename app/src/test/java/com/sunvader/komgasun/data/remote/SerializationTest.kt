package com.sunvader.komgasun.data.remote

import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.BookPageDto
import com.sunvader.komgasun.data.model.LibraryDto
import com.sunvader.komgasun.data.model.PageResponseDto
import com.sunvader.komgasun.data.model.SeriesDto
import com.sunvader.komgasun.data.model.UserDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testUserDtoSerialization() {
        val jsonStr = """{"id":"0A1B2C","email":"user@komga.org","roles":["ADMIN"],"sharedAllLibraries":true}"""
        val user = json.decodeFromString<UserDto>(jsonStr)
        assertEquals("0A1B2C", user.id)
        assertEquals("user@komga.org", user.email)
        assertEquals(listOf("ADMIN"), user.roles)
        assertEquals(true, user.sharedAllLibraries)
    }

    @Test
    fun testLibraryDtoSerialization() {
        val jsonStr = """[{"id":"lib1","name":"Comics","root":"/data/comics","unavailable":false}]"""
        val libraries = json.decodeFromString<List<LibraryDto>>(jsonStr)
        assertEquals(1, libraries.size)
        assertEquals("lib1", libraries[0].id)
        assertEquals("Comics", libraries[0].name)
    }

    @Test
    fun testSeriesPageSerialization() {
        val jsonStr = """
            {
                "content": [
                    {
                        "id": "s1",
                        "libraryId": "lib1",
                        "name": "Spider-Man",
                        "booksCount": 12,
                        "metadata": {"title":"The Amazing Spider-Man","status":"ONGOING"}
                    }
                ],
                "empty": false,
                "totalElements": 1,
                "totalPages": 1
            }
        """.trimIndent()
        val page = json.decodeFromString<PageResponseDto<SeriesDto>>(jsonStr)
        assertFalse(page.empty)
        assertEquals(1, page.content.size)
        assertEquals("s1", page.content[0].id)
        assertEquals("The Amazing Spider-Man", page.content[0].metadata.title)
    }

    @Test
    fun testBookPagesSerialization() {
        val jsonStr = """
            [
                {"number":1,"fileName":"01.jpg","mediaType":"image/jpeg","width":1080,"height":1920,"sizeBytes":12345},
                {"number":2,"fileName":"02.jpg","mediaType":"image/jpeg","width":1080,"height":1920,"sizeBytes":12346}
            ]
        """.trimIndent()
        val pages = json.decodeFromString<List<BookPageDto>>(jsonStr)
        assertEquals(2, pages.size)
        assertEquals(1, pages[0].number)
        assertEquals("image/jpeg", pages[0].mediaType)
    }
}
