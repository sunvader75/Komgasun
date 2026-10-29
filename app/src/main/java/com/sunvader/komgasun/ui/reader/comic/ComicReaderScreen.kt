package com.sunvader.komgasun.ui.reader.comic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import com.sunvader.komgasun.ui.reader.ReaderDiagnostics
import com.sunvader.komgasun.ui.reader.requestListener
import coil.request.SuccessResult
import coil.request.ErrorResult
import androidx.compose.runtime.collectAsState
import com.sunvader.komgasun.ui.reader.readerPageRequest
import com.sunvader.komgasun.R
import com.sunvader.komgasun.data.remote.KomgaApiClient
import com.sunvader.komgasun.ui.common.ErrorView
import com.sunvader.komgasun.ui.common.LoadingView
import com.sunvader.komgasun.ui.common.ReaderZoomState
import com.sunvader.komgasun.ui.common.ZoomableBox
import kotlinx.coroutines.launch

/**
 * Compone la sesión de [bookId], su paginador con índices desde cero y controles con números desde uno.
 * Comparte resultados decodificados entre vista y precarga; libera el planificador al salir y envía el último progreso.
 * [onBack] solicita salir, también tras borrar con éxito el archivo remoto; el propietario gestiona la navegación.
 * Desactiva el paso por gesto durante zoom o multitáctil y muestra diagnóstico hasta confirmar la imagen visible.
 */
@Composable
fun ComicReaderScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComicReaderViewModel = viewModel(key = bookId) { ComicReaderViewModel(bookId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val diagnostics = remember(bookId) { ReaderDiagnostics() }
    val imageLoader = LocalContext.current.imageLoader
    ComicImmersiveMode()

    LaunchedEffect(bookId) {
        if (!state.isReady) {
            viewModel.loadPages()
        }
    }

    DisposableEffect(bookId) {
        onDispose {
            viewModel.onReaderClosed()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_file)) },
            text = {
                Text("¿Eliminar archivo de este cómic? Se borrará físicamente del servidor y quedará como no disponible hasta vaciar la papelera.")
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.isLoading || !state.isReady -> {
                LoadingView(
                    message = stringResource(R.string.loading),
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.errorMessage != null -> {
                ErrorView(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::loadPages,
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.pages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron páginas",
                        color = Color.White
                    )
                }
            }
            else -> {
                val pages = state.pages
                val pagerState = rememberPagerState(
                    initialPage = state.initialPage,
                    pageCount = { pages.size }
                )
                var sliderValue by remember(state.initialPage) { mutableFloatStateOf(state.initialPage.toFloat()) }
                val readerZoom = remember(bookId) { ReaderZoomState() }
                val isCurrentPageZoomed = readerZoom.isScaled
                var restartPage by remember(bookId) { mutableStateOf<Int?>(null) }
                var isMultiTouchActive by remember { mutableStateOf(false) }

                LaunchedEffect(state.initialPage) {
                    if (state.initialPage in pages.indices && pagerState.currentPage != state.initialPage) {
                        pagerState.scrollToPage(state.initialPage)
                        sliderValue = state.initialPage.toFloat()
                    }
                }

                LaunchedEffect(pagerState.currentPage) {
                    sliderValue = pagerState.currentPage.toFloat()
                    isMultiTouchActive = false
                    viewModel.onPageChanged(pagerState.currentPage, pages.size)
                }

                val pageUrls = remember(pages, state.serverUrl, bookId) {
                    pages.map { KomgaApiClient.getPageImageUrl(state.serverUrl, bookId, it.number) }
                }
                val context = LocalContext.current
                val loader = remember(pageUrls, imageLoader, diagnostics) {
                    ComicPageLoader(scope, pageUrls.size) { index ->
                        val request = readerPageRequest(context, pageUrls[index]).newBuilder()
                            .listener(diagnostics.requestListener(pages[index].number,
                                if (index == pagerState.currentPage) ReaderDiagnostics.Kind.VISIBLE else ReaderDiagnostics.Kind.PRELOAD))
                            .build()
                        when (val result = imageLoader.execute(request)) {
                            is SuccessResult -> result
                            is ErrorResult -> throw result.throwable
                        }
                    }
                }
                DisposableEffect(loader) { onDispose { loader.close() } }
                LaunchedEffect(loader, pagerState.currentPage) { loader.moveTo(pagerState.currentPage) }
                val loadedPages by loader.pages.collectAsState()
                // Solo una composición visible correcta oculta el diagnóstico, no una mera precarga.
                var displayedPage by remember(loader) { mutableStateOf<Int?>(null) }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isCurrentPageZoomed && !isMultiTouchActive,
                    key = { pages[it].number }
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    val loaded = loadedPages[pageIndex]
                    ZoomableBox(
                        modifier = Modifier.fillMaxSize(),
                        zoomState = readerZoom,
                        onTap = viewModel::toggleControls,
                        onNavigate = { direction ->
                            val target = pagerState.currentPage + direction
                            if (target in 0 until pagerState.pageCount) {
                                scope.launch {
                                    restartPage = if (readerZoom.scale.floatValue > 1f) target else null
                                    pagerState.scrollToPage(target)
                                }
                            }
                        },
                        onMultiTouchChanged = { multiTouch ->
                            if (pageIndex == pagerState.currentPage) {
                                isMultiTouchActive = multiTouch
                            }
                        }
                    ) {
                        if (loaded?.value == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (loaded?.error != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Error al cargar página ${page.number}", color = Color.White)
                                        IconButton(onClick = { loader.retry(pageIndex) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Reintentar", tint = Color.White)
                                        }
                                    }
                                } else CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        } else SubcomposeAsyncImage(
                            // Resultado drawable: Coil no puede emitir aquí otra solicitud de red.
                            model = loaded.value.drawable,
                            onSuccess = { if (pageIndex == pagerState.currentPage) displayedPage = pageIndex },
                            imageLoader = imageLoader,
                            contentDescription = "Página ${page.number}",
                            contentScale = ContentScale.Fit,
                            success = {
                                androidx.compose.runtime.SideEffect {
                                    if (pageIndex == pagerState.currentPage) displayedPage = pageIndex
                                }
                                ComicPageContent(
                                    scrollEnabled = !isCurrentPageZoomed && !isMultiTouchActive,
                                    restartScale = if (restartPage == pageIndex && pageIndex == pagerState.currentPage) readerZoom.scale.floatValue else null,
                                    onRestart = { origin ->
                                        readerZoom.offset.value = origin
                                        restartPage = null
                                    }
                                )
                            },
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Error al cargar página ${page.number}",
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        IconButton(onClick = viewModel::loadPages) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                ComicDiagnosticsPanel(
                    pageDisplayed = loadedPages[pagerState.currentPage]?.value != null && displayedPage == pagerState.currentPage,
                    diagnostics = diagnostics,
                    page = pages[pagerState.currentPage].number,
                    memory = { imageLoader.memoryCache?.let { it.size.toLong() to it.maxSize.toLong() } ?: (null to null) },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.85f)
                )

                // Controles superiores superpuestos
                AnimatedVisibility(
                    visible = state.showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.page_indicator,
                                        pagerState.currentPage + 1,
                                        pages.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_file),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Selector de página inferior superpuesto
                AnimatedVisibility(
                    visible = state.showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${pages.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage > 0) {
                                            scope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage > 0
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Anterior",
                                        tint = if (pagerState.currentPage > 0) Color.White else Color.Gray
                                    )
                                }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = { newValue ->
                                        sliderValue = newValue
                                        val targetPage = newValue.toInt().coerceIn(0, pages.size - 1)
                                        if (targetPage != pagerState.currentPage) {
                                            scope.launch {
                                                pagerState.scrollToPage(targetPage)
                                            }
                                        }
                                    },
                                    valueRange = 0f..(pages.size - 1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage < pages.size - 1) {
                                            scope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage < pages.size - 1
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Siguiente",
                                        tint = if (pagerState.currentPage < pages.size - 1) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
