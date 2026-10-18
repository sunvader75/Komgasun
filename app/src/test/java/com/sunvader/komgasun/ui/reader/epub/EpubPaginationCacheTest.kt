package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EpubPaginationCacheTest {
    @Test fun persistsAcrossInstancesAndRejectsOtherLayoutsOrInvalidCounts() {
        val directory = Files.createTempDirectory("epub-pagination-test").toFile()
        try {
            val key = EpubPaginationCache.key("book", 240, 360, 18, "density-renderer")
            EpubPaginationCache(directory).save(key, EpubBookPageMap(listOf(2, 8)))
            val reopened = EpubPaginationCache(directory)
            assertEquals(listOf(2, 8), reopened.load(key, 2)?.counts)
            assertNull(reopened.load(key, 3))
            for (other in listOf(
                EpubPaginationCache.key("changed-book", 240, 360, 18, "density-renderer"),
                EpubPaginationCache.key("book", 360, 240, 18, "density-renderer"),
                EpubPaginationCache.key("book", 240, 360, 28, "density-renderer"),
                EpubPaginationCache.key("book", 240, 360, 18, "changed-renderer")
            )) assertNull(reopened.load(other, 2))
            directory.resolve(key).writeText("1\n0,-4")
            assertNull(reopened.load(key, 2))
        } finally { directory.deleteRecursively() }
    }
}
