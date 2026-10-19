package com.sunvader.komgasun.ui.reader.epub

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sunvader.komgasun.ui.reader.ReaderLoadStatus
import com.sunvader.komgasun.ui.reader.readerLoadStatus

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var imageDiagnostics by remember(bookId) { mutableStateOf("") }
    var showChapterDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val themeBackground = if (state.isDarkTheme) Color(0xFF121212) else Color.White
    val themeTextColor = if (state.isDarkTheme) Color.White else Color(0xFF202124)
    val subduedColor = if (state.isDarkTheme) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
    val overlayBg = if (state.isDarkTheme) Color(0xFF1E1E1E).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
    val totalChapters = state.epubBook?.chapters?.size ?: 0
    var bookPageMap by remember(state.epubBook, state.fontSizeSp, state.useOriginalFonts) {
        mutableStateOf<EpubBookPageMap?>(null)
    }
    var paginationFailed by remember(state.epubBook, state.fontSizeSp, state.useOriginalFonts) {
        mutableStateOf(false)
    }

    var measuredChapters by remember(state.epubBook, state.fontSizeSp, state.useOriginalFonts) { mutableStateOf(0) }

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

    EpubImmersiveMode(showControls = state.showControls)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeBackground)
    ) {
        val loadStatus = readerLoadStatus(state.isLoading, state.isReady, state.errorMessage)
        when {
            loadStatus == ReaderLoadStatus.ERROR -> {
                ErrorView(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::initEpub,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
                        color = themeTextColor
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
                        color = subduedColor
                    )
                }
            }
            loadStatus == ReaderLoadStatus.LOADING -> {
                LoadingView(
                    message = "Procesando contenido del libro...",
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.epubBook != null -> {
                // LAYER 1: Full reading view matching Google Play Books screenshot
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                ) {
                    // Top margin space (clean reading immersion like Play Books)
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clickable { viewModel.toggleControls() }
                    )

                    // Keep the reading area inset from the page edges.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Measure offscreen chapters in a separate, identically sized reader.
                        AndroidView(
                            factory = { EpubBookPaginator(it).apply { alpha = 0f } },
                            update = { paginator ->
                                paginator.onPageMap = { pages, failed ->
                                    bookPageMap = pages
                                    viewModel.onBookPageMap(pages)
                                    paginationFailed = failed
                                }
                                paginator.onProgress = { done, _ -> measuredChapters = done }
                                state.epubBook?.let { paginator.paginate(it, state.fontSizeSp, state.isDarkTheme, state.useOriginalFonts) }
                            },
                            onRelease = { it.release() },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)
                        )

                        // Left edge tap zone for previous page
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterStart)
                                .clickable { viewModel.movePage(-1) }
                        )

                        // Right edge tap zone for next page
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterEnd)
                                .clickable { viewModel.movePage(1) }
                        )

                        // Inset outside the WebView so pagination uses the available reading area.
                        AndroidView(
                            factory = { ctx ->
                                EpubWebView(ctx).apply {
                                    onImageDiagnostics = { imageDiagnostics = it }
                                    onMove = viewModel::movePage
                                    onTap = viewModel::toggleControls
                                    onMeasured = viewModel::onPageMeasured
                                }
                            },
                            update = { it.render(state) },
                            onRelease = { it.dispose() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 40.dp, vertical = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Running Footer (Exact Google Play Books style from screenshot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clickable { viewModel.toggleControls() }
                            .padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val remainingInChapter = (state.pageCount - 1 - state.page).coerceAtLeast(0)
                        val remainingText = when {
                            state.pageCount <= 1 -> "1 página en este capítulo"
                            remainingInChapter > 1 -> "Quedan $remainingInChapter páginas del capítulo"
                            remainingInChapter == 1 -> "Queda 1 página del capítulo"
                            else -> "Última página del capítulo"
                        }
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subduedColor
                        )

                        val chapterProgress = if (state.pageCount > 1) {
                            state.page.toDouble() / (state.pageCount - 1)
                        } else 0.0
                        val overallProgress = if (totalChapters > 0) {
                            ((state.currentChapterIndex + chapterProgress) / totalChapters).coerceIn(0.0, 1.0)
                        } else 0.0
                        val percentage = (overallProgress * 100).toInt()

                        Text(
                            text = "$percentage %",
                            style = MaterialTheme.typography.labelSmall,
                            color = subduedColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Diagnostics panel
                Box(Modifier.align(Alignment.TopEnd)) {
                    EpubImageDiagnosticsPanel(imageDiagnostics)
                }

                // LAYER 2: Animated Top Bar Overlay
                AnimatedVisibility(
                    visible = state.showControls,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = overlayBg,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.close),
                                    tint = themeTextColor
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = themeTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                            }

                            IconButton(onClick = viewModel::decreaseFontSize) {
                                Icon(
                                    Icons.Default.TextDecrease,
                                    contentDescription = "Reducir texto",
                                    tint = themeTextColor
                                )
                            }
                            IconButton(onClick = viewModel::increaseFontSize) {
                                Icon(
                                    Icons.Default.TextIncrease,
                                    contentDescription = "Aumentar texto",
                                    tint = themeTextColor
                                )
                            }
                            IconButton(onClick = viewModel::toggleTheme) {
                                Icon(
                                    imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Cambiar tema",
                                    tint = themeTextColor
                                )
                            }
                            IconButton(onClick = { showChapterDialog = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Índice de capítulos",
                                    tint = themeTextColor
                                )
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_file),
                                    tint = themeTextColor
                                )
                            }
                        }
                    }
                }

                // LAYER 3: Animated Bottom Bar Overlay with Scrubber
                AnimatedVisibility(
                    visible = state.showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = overlayBg,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Usar fuentes originales del EPUB", color = themeTextColor,
                                    modifier = Modifier.weight(1f))
                                androidx.compose.material3.Switch(
                                    checked = state.useOriginalFonts,
                                    onCheckedChange = viewModel::setUseOriginalFonts
                                )
                            }
                            val pages = bookPageMap
                            // Scrubber Slider + Direct Chapter Navigation Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (totalChapters > 1) {
                                    IconButton(
                                        onClick = viewModel::previousChapterDirect,
                                        enabled = state.currentChapterIndex > 0
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Capítulo anterior",
                                            tint = if (state.currentChapterIndex > 0) themeTextColor else Color.Gray.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                EpubBookScrubber(
                                    pages = pages,
                                    currentPage = pages?.let {
                                        val chapterPage = if (state.pageCount > 0) state.page else
                                            EpubPagination.page(state.proportion, it.counts[state.currentChapterIndex])
                                        it.absolutePage(state.currentChapterIndex, chapterPage)
                                    } ?: 1,
                                    ready = state.isReady,
                                    failed = paginationFailed,
                                    dark = state.isDarkTheme,
                                    onSeek = { page -> pages?.let { viewModel.seekBookPage(page, it) } },
                                    bookFraction = EpubBookProgress.fraction(state.currentChapterIndex, state.proportion, totalChapters).toFloat(),
                                    onSeekFraction = viewModel::seekBook,
                                    calculationProgress = "$measuredChapters / $totalChapters",
                                    modifier = Modifier.weight(1f)
                                )

                                if (totalChapters > 1) {
                                    IconButton(
                                        onClick = viewModel::nextChapterDirect,
                                        enabled = state.currentChapterIndex < totalChapters - 1
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Capítulo siguiente",
                                            tint = if (state.currentChapterIndex < totalChapters - 1) themeTextColor else Color.Gray.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.syncStatus?.let { message ->
            Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Text(message, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showChapterDialog && state.epubBook != null) {
        val chapters = state.epubBook!!.chapters
        val initialScrollIndex = state.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text("Índice de capítulos") },
            text = {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(chapters) { index, chapter ->
                        val isSelected = index == state.currentChapterIndex
                        val displayTitle = chapter.title.ifBlank { "Capítulo ${index + 1}" }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectChapter(index)
                                    showChapterDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. $displayTitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
