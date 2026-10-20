package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** No muestra nada sin informe; permite abrirlo y copiarlo íntegro al portapapeles sin cerrar el diálogo. */
@Composable
fun EpubImageDiagnosticsPanel(report: String) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    if (report.isEmpty()) return
    TextButton(onClick = { expanded = true }) { Text("Diagnóstico de imágenes") }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text("Imágenes EPUB") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(report) } },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copiar diagnóstico") } },
        dismissButton = { TextButton(onClick = { expanded = false }) { Text("Cerrar") } }
    )
}
