package com.sunvader.komgasun.ui.reader.epub

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.R
import com.sunvader.komgasun.ui.common.ErrorView
import com.sunvader.komgasun.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpubReaderViewModel = viewModel(key = bookId) {
        EpubReaderViewModel(KomgasunApp.instance, bookId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChapterDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_file)) },
            text = {
                Text("¿Eliminar archivo de este libro EPUB? Se borrará físicamente del servidor y quedará como no disponible hasta vaciar la papelera.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteBookFile { res ->
                            if (res.isSuccess) {
                                onBack()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(bookId) {
        if (!state.isReady) {
            viewModel.initEpub()
        }
    }

    DisposableEffect(bookId) {
        onDispose {
            viewModel.onReaderClosed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val chapterTitle = state.epubBook?.chapters?.getOrNull(state.currentChapterIndex)?.title
                        if (!chapterTitle.isNullOrBlank()) {
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::decreaseFontSize) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "Reducir texto")
                    }
                    IconButton(onClick = viewModel::increaseFontSize) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "Aumentar texto")
                    }
                    IconButton(onClick = viewModel::toggleTheme) {
                        Icon(
                            imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Cambiar tema"
                        )
                    }
                    IconButton(onClick = { showChapterDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Índice de capítulos")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_file)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isDarkTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val totalChapters = state.epubBook?.chapters?.size ?: 0
            if (totalChapters > 0) {
                Surface(
                    color = if (state.isDarkTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = viewModel::previousChapter,
                            enabled = state.currentChapterIndex > 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Capítulo anterior",
                                tint = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Capítulo ${state.currentChapterIndex + 1} de $totalChapters",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = viewModel::nextChapter,
                            enabled = state.currentChapterIndex < totalChapters - 1
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Capítulo siguiente",
                                tint = if (state.isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (state.isDarkTheme) Color(0xFF121212) else Color.White)
        ) {
            when {
                state.downloadProgress != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Descargando libro EPUB...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.isDarkTheme) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${((state.downloadProgress ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isDarkTheme) Color.LightGray else Color.DarkGray
                        )
                    }
                }
                state.isLoading || !state.isReady -> {
                    LoadingView(
                        message = "Procesando contenido del libro...",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.errorMessage != null -> {
                    ErrorView(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::initEpub,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.epubBook != null -> {
                    val currentChapter = state.epubBook?.chapters?.getOrNull(state.currentChapterIndex)
                    val rawHtml = currentChapter?.htmlContent.orEmpty()
                    val styledHtml = buildStyledHtml(
                        rawHtml = rawHtml,
                        isDarkTheme = state.isDarkTheme,
                        fontSizeSp = state.fontSizeSp
                    )

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.defaultTextEncodingName = "utf-8"
                                setBackgroundColor(if (state.isDarkTheme) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
                            }
                        },
                        update = { webView ->
                            webView.setBackgroundColor(if (state.isDarkTheme) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
                            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showChapterDialog && state.epubBook != null) {
        val chapters = state.epubBook!!.chapters
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text("Índice de capítulos") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(chapters) { index, chapter ->
                        val isSelected = index == state.currentChapterIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectChapter(index)
                                    showChapterDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${chapter.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

private fun buildStyledHtml(
    rawHtml: String,
    isDarkTheme: Boolean,
    fontSizeSp: Int
): String {
    val bgColor = if (isDarkTheme) "#121212" else "#FFFFFF"
    val textColor = if (isDarkTheme) "#E0E0E0" else "#212121"
    val linkColor = if (isDarkTheme) "#90CAF9" else "#1976D2"

    val css = """
        <style>
            body {
                background-color: $bgColor;
                color: $textColor;
                font-family: sans-serif;
                font-size: ${fontSizeSp}px;
                line-height: 1.6;
                padding: 16px;
                margin: 0;
            }
            a { color: $linkColor; }
            img { max-width: 100%; height: auto; }
            pre, code {
                background-color: ${if (isDarkTheme) "#1E1E1E" else "#F5F5F5"};
                padding: 4px;
                border-radius: 4px;
            }
        </style>
    """.trimIndent()

    return if (rawHtml.contains("<head>", ignoreCase = true)) {
        rawHtml.replaceFirst("(?i)<head>".toRegex(), "<head>$css")
    } else {
        "<html><head>$css</head><body>$rawHtml</body></html>"
    }
}
