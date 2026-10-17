package com.sunvader.komgasun.ui.reader

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class ReaderDiagnosticsTest {
    @Test fun reportLabelsEachPageAndOrdersStartsRatherThanCompletions() {
        var now = 100L
        val diagnostics = ReaderDiagnostics(clock = { now })
        val preload = diagnostics.start(2, ReaderDiagnostics.Kind.PRELOAD)
        now = 110
        val cancelled = diagnostics.start(1, ReaderDiagnostics.Kind.VISIBLE)
        now = 120
        diagnostics.finish(cancelled, ReaderDiagnostics.Status.CANCELLED)
        now = 130
        diagnostics.finish(preload, ReaderDiagnostics.Status.SUCCESS, ReaderDiagnostics.Source.NETWORK)
        now = 140
        diagnostics.start(2, ReaderDiagnostics.Kind.VISIBLE)
        now = 150
        val report = diagnostics.report(2, 12, 64)
        val first = "#$preload Página 2 PRELOAD SUCCESS; inicio=0ms; fin=30ms; origen=NETWORK; total=30ms; error=ninguno"
        val second = "#$cancelled Página 1 VISIBLE CANCELLED; inicio=10ms; fin=20ms; origen=UNKNOWN; total=10ms; error=ninguno"
        val third = "#3 Página 2 VISIBLE LOADING; inicio=40ms; fin=pendiente; origen=UNKNOWN; total=pendiente (10ms); error=ninguno"
        assertTrue(report.contains("Actual: $third\n"))
        assertTrue(report.contains("Precarga: $first\n"))
        assertEquals(listOf(first, second, third), report.substringAfter("Eventos recientes (máximo 64; orden de inicio, más antiguo primero; tiempos desde inicio de sesión en ms):\n").trimEnd().lines())
        val otherPage = diagnostics.report(1, null, null)
        assertTrue(otherPage.contains("Actual: $second\n"))
        assertTrue(otherPage.contains("Precarga: sin solicitud observada\n"))
    }

    @Test fun reportKeepsSessionOriginAndIdsAfterEviction() {
        var now = 500L
        val diagnostics = ReaderDiagnostics(capacity = 1, clock = { now })
        val evicted = diagnostics.start(1, ReaderDiagnostics.Kind.PRELOAD)
        now = 520
        diagnostics.start(2, ReaderDiagnostics.Kind.VISIBLE)
        diagnostics.finish(evicted, ReaderDiagnostics.Status.SUCCESS)
        val report = diagnostics.report(1, null, null)
        assertTrue(report.contains("Actual: sin solicitud observada\n"))
        assertTrue(report.contains("Precarga: sin solicitud observada\n"))
        val history = report.substringAfter("Eventos recientes")
        assertTrue(history.contains("#2 Página 2 VISIBLE LOADING; inicio=20ms; fin=pendiente"))
        assertFalse(history.contains("#1 Página"))
    }

    @Test fun recordsTimingAndSourceWithoutPrivateErrorText() {
        var now = 10L
        val diagnostics = ReaderDiagnostics(clock = { now })
        val id = diagnostics.start(2, ReaderDiagnostics.Kind.VISIBLE)
        now = 35
        diagnostics.finish(id, ReaderDiagnostics.Status.SUCCESS, ReaderDiagnostics.Source.MEMORY)
        val event = diagnostics.events.value.single()
        assertEquals(25L, event.elapsedMs)
        assertEquals(ReaderDiagnostics.Source.MEMORY, event.source)
        val failed = diagnostics.start(3, ReaderDiagnostics.Kind.PRELOAD)
        diagnostics.fail(failed, SocketTimeoutException("https://private/user?token=secret"))
        val report = diagnostics.report(3, 12, 64)
        assertTrue(report.contains("TIMEOUT"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("secret"))
        assertTrue(report.contains("no disponible"))
    }

    @Test fun cancellationPendingAndBoundsAreObservable() {
        val diagnostics = ReaderDiagnostics(capacity = 2, clock = { 0 })
        val pending = diagnostics.start(1, ReaderDiagnostics.Kind.PRELOAD)
        assertEquals(ReaderDiagnostics.Status.LOADING, diagnostics.events.value.single().status)
        diagnostics.finish(pending, ReaderDiagnostics.Status.CANCELLED)
        assertEquals(ReaderDiagnostics.Status.CANCELLED, diagnostics.events.value.single().status)
        diagnostics.start(2, ReaderDiagnostics.Kind.VISIBLE)
        diagnostics.start(3, ReaderDiagnostics.Kind.VISIBLE)
        diagnostics.finish(pending, ReaderDiagnostics.Status.SUCCESS)
        assertEquals(listOf(2, 3), diagnostics.events.value.map { it.page })
    }
}
