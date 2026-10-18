package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubImageDiagnosticsTest {
    @Test fun failedCompleteImageIsNotReportedAsSuccess() {
        val report = EpubImageDiagnostics(0, true, 0, 0, 100, 80, "UNKNOWN (sin callback observado)", "NO_ATTEMPT").report()
        assertEquals("Imagen 0: complete=true natural=0x0 rect=100x80\ntransporte=UNKNOWN (sin callback observado) recuperación=NO_ATTEMPT categoría=DECODE_OR_LOAD\nfuente=UNKNOWN selección=UNKNOWN permitidas=0 base=UNKNOWN\n", report)
        assertFalse(report.contains("https:"))
    }
    @Test fun sourceMetadataIsAllowlisted() {
        val report = EpubImageDiagnostics(0, true, 0, 0, 0, 0, "UNKNOWN", "EMPTY_SOURCE", "file:///private", "secret", -1, "https://private").report()
        assertTrue(report.contains("fuente=UNKNOWN selección=UNKNOWN permitidas=0 base=UNKNOWN"))
        assertFalse(report.contains("private"))
    }
    @Test fun recoveredImageKeepsMeasurementsDistinctFromTransport() {
        val report = EpubImageDiagnostics(1, true, 10, 20, 5, 10, "HTTP403 reason=POLICY mime=unknown bytes=0", "ASSIGNED (decode no confirmado)").report()
        assertTrue(report.contains("natural=10x20 rect=5x10"))
        assertTrue(report.contains("HTTP403"))
        assertTrue(report.contains("categoría=NONE"))
    }
}
