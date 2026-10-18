package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubDocumentLifecycleTest {
    @Test fun duplicatePendingAndRepeatedCompletion() {
        val life = EpubDocumentLifecycle()
        life.begin("https://epub.invalid/a?generation=1")
        val ticket = life.reserve(life.url)!!
        assertNull(life.reserve(life.url))
        assertTrue(life.complete(ticket, true))
        // A trusted reload can bootstrap a fresh DOM; JS itself handles same-document repeats.
        assertNotNull(life.reserve(life.url))
    }
    @Test fun staleGenerationCannotCompleteOrInstall() {
        val life = EpubDocumentLifecycle()
        life.begin("https://epub.invalid/a?generation=1")
        val old = life.reserve(life.url)!!
        life.begin("https://epub.invalid/b?generation=2")
        assertFalse(life.isCurrent(old))
        assertFalse(life.complete(old, true))
        assertNull(life.reserve("https://epub.invalid/a?generation=1"))
        assertNotNull(life.reserve(life.url))
    }
    @Test fun errorAndForeignCallbacksAreRejected() {
        val life = EpubDocumentLifecycle()
        life.begin("https://epub.invalid/a?generation=1")
        listOf("chrome-error://chromewebdata/", "about:blank", "https://evil.invalid/a", null).forEach {
            assertNull(life.reserve(it))
        }
        val ticket = life.reserve(life.url)!!
        assertFalse(life.complete(ticket, false))
        assertNotNull(life.reserve(life.url))
    }
}
