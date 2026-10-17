package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubHtmlTest {
    @Test fun cspPrecedesEmptyPublicationSurface() {
        val shell = EpubHtml.shell
        assertTrue(shell.indexOf("Content-Security-Policy") < shell.indexOf("<body>"))
        for (directive in listOf("default-src 'none'", "script-src 'none'", "base-uri 'none'", "form-action 'none'", "frame-src 'none'", "object-src 'none'", "connect-src 'none'")) assertTrue(shell.contains(directive))
        assertFalse(shell.contains("<script"))
        assertTrue(shell.contains("<body></body>"))
    }
    @Test fun bootstrapDoesNotInterpolatePublicationIntoMarkup() {
        assertFalse(EpubHtml.shell.contains("PUBLICATION_MARKER"))
        val bootstrap = EpubHtml.install("\"PUBLICATION_MARKER\"")
        assertTrue(bootstrap.contains("DOMParser"))
        assertTrue(bootstrap.contains("'script,base,meta,iframe"))
        assertTrue(bootstrap.contains("startsWith('on')"))
    }
}
