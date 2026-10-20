package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.sunvader.komgasun.ui.reader.ReaderDiagnostics
import kotlinx.coroutines.delay

/**
 * Ofrece diagnóstico tras 1500 ms sin mostrar [page] (número desde uno); el éxito visible oculta y reinicia el panel.
 * [memory] proporciona uso y límite global en bytes, opcionalmente desconocidos, al refrescar o copiar.
 * Los eventos actualizan la instantánea; no hay sondeo periódico. Copiar escribe el informe en el portapapeles.
 */
@Composable
fun ComicDiagnosticsPanel(diagnostics: ReaderDiagnostics, page: Int, memory: () -> Pair<Long?, Long?>, modifier: Modifier = Modifier, pageDisplayed: Boolean = false) {
    var delayElapsed by remember(page, pageDisplayed) { mutableStateOf(false) }
    // Conserva el efecto incluso tras el éxito para cancelar la espera pendiente.
    LaunchedEffect(page, pageDisplayed) {
        if (!pageDisplayed) {
            delay(1500)
            delayElapsed = true
        }
    }
    // No compone Surface durante cargas breves; el éxito también descarta el estado de interfaz.
    if (pageDisplayed || !delayElapsed) return
    var expanded by remember(page) { mutableStateOf(false) }
    var copied by remember(page) { mutableStateOf(false) }
    val events by diagnostics.events.collectAsState()
    val clipboard = LocalClipboardManager.current
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(Modifier.padding(4.dp)) {
            TextButton(onClick = { expanded = !expanded; copied = false }) {
                Text(if (expanded) "Ocultar diagnóstico" else "Diagnóstico")
            }
            if (expanded) {
                // Instantánea renovada por eventos o actualización explícita; sin sondeo en segundo plano.
                var refresh by remember { mutableIntStateOf(0) }
                val report = remember(events, page, refresh) {
                    val (used, limit) = memory()
                    diagnostics.report(page, used, limit)
                }
                Text(report, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                TextButton(onClick = { refresh++ }) { Text("Actualizar tiempos y memoria") }
                TextButton(onClick = {
                    val (used, limit) = memory()
                    clipboard.setText(AnnotatedString(diagnostics.report(page, used, limit)))
                    copied = true
                }) { Text(if (copied) "Diagnóstico copiado" else "Copiar diagnóstico") }
            }
        }
    }
}
