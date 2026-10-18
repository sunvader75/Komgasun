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
    @Test fun recoveryDoesNotPermitPublicationDataOrSvg() {
        assertTrue(EpubHtml.shell.contains("img-src https://epub.invalid blob:;"))
        assertFalse(EpubHtml.shell.contains("data:"))
        val bootstrap = EpubHtml.install("\"<img src='data:image/svg+xml,bad'>\"")
        assertTrue(bootstrap.contains("e.removeAttribute('srcset')"))
        assertTrue(bootstrap.contains("!allowedRasterUrls.has(url.href)"))
        assertTrue(bootstrap.contains("retried.has(i)"))
        assertTrue(bootstrap.contains("URL.revokeObjectURL(url)"))
        assertTrue(bootstrap.contains("e.naturalWidth===0"))
    }
    @Test fun bootstrapDoesNotInterpolatePublicationIntoMarkup() {
        assertFalse(EpubHtml.shell.contains("PUBLICATION_MARKER"))
        val bootstrap = EpubHtml.install("\"PUBLICATION_MARKER\"")
        assertTrue(bootstrap.contains("DOMParser"))
        assertTrue(bootstrap.contains("'script,base,meta,iframe"))
        assertTrue(bootstrap.contains("startsWith('on')"))
    }
    @Test fun svgCoverConversionAllowsVersionAttribute() {
        val bootstrap = EpubHtml.install("\"<svg></svg>\"")
        assertTrue(bootstrap.contains("'version'"))
        assertTrue(bootstrap.contains("refs.length<1"))
    }
    @Test fun svgCoverAllowsRelativePathParentNavigation() {
        val bootstrap = EpubHtml.install("\"<svg></svg>\"")
        assertFalse(bootstrap.contains("p==='..'"))
    }
}

