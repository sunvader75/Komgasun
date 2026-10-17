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

@Composable
fun ComicDiagnosticsPanel(diagnostics: ReaderDiagnostics, page: Int, memory: () -> Pair<Long?, Long?>, modifier: Modifier = Modifier, pageDisplayed: Boolean = false) {
    var delayElapsed by remember(page, pageDisplayed) { mutableStateOf(false) }
    // Keep the effect in composition even on success so it cancels the pending delay.
    LaunchedEffect(page, pageDisplayed) {
        if (!pageDisplayed) {
            delay(1500)
            delayElapsed = true
        }
    }
    // Compose no Surface at all during short loads; success also discards UI state.
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
                // Snapshot refreshed by events or explicit refresh; no background polling.
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
