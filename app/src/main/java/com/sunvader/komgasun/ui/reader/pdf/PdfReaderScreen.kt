package com.sunvader.komgasun.ui.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.R
import com.sunvader.komgasun.data.remote.KomgaApiClient
import com.sunvader.komgasun.ui.common.ErrorView
import com.sunvader.komgasun.ui.common.LoadingView
import com.sunvader.komgasun.ui.common.ZoomableBox
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfReaderViewModel = viewModel(key = bookId) {
        val app = KomgasunApp.instance
        PdfReaderViewModel(app, bookId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_file)) },
            text = {
                Text("¿Eliminar archivo de este PDF? Se borrará físicamente del servidor y quedará como no disponible hasta vaciar la papelera.")
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
            viewModel.initReader()
        }
    }

    DisposableEffect(bookId) {
        onDispose {
            viewModel.onReaderClosed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.downloadProgress != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Descargando PDF para renderizado nativo...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${((state.downloadProgress ?: 0f) * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            state.isLoading || !state.isReady -> {
                LoadingView(
                    message = "Cargando documento PDF...",
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.errorMessage != null -> {
                ErrorView(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::initReader,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val totalPages = when (val mode = state.mode) {
                    is PdfSourceMode.NativeRenderer -> mode.totalPages
                    is PdfSourceMode.StreamFromKomga -> state.streamPages.size
                }

                if (totalPages == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron páginas en este PDF", color = Color.White)
                    }
                } else {
                    val pagerState = rememberPagerState(
                        initialPage = state.initialPage,
                        pageCount = { totalPages }
                    )
                    var sliderValue by remember(state.initialPage) { mutableFloatStateOf(state.initialPage.toFloat()) }
                    var isCurrentPageZoomed by remember { mutableStateOf(false) }
                    var isMultiTouchActive by remember { mutableStateOf(false) }

                    LaunchedEffect(state.initialPage) {
                        if (state.initialPage in 0 until totalPages && pagerState.currentPage != state.initialPage) {
                            pagerState.scrollToPage(state.initialPage)
                            sliderValue = state.initialPage.toFloat()
                        }
                    }

                    LaunchedEffect(pagerState.currentPage) {
                        sliderValue = pagerState.currentPage.toFloat()
                        isCurrentPageZoomed = false
                        isMultiTouchActive = false
                        viewModel.onPageChanged(pagerState.currentPage, totalPages)
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !isCurrentPageZoomed && !isMultiTouchActive
                    ) { pageIndex ->
                        when (state.mode) {
                            is PdfSourceMode.NativeRenderer -> {
                                var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                                var isPageLoading by remember { mutableStateOf(true) }

                                LaunchedEffect(pageIndex) {
                                    isPageLoading = true
                                    bitmap = viewModel.renderPageBitmap(pageIndex)
                                    isPageLoading = false
                                }

                                ZoomableBox(
                                    modifier = Modifier.fillMaxSize(),
                                    resetKey = pageIndex,
                                    onTap = viewModel::toggleControls,
                                    onZoomChanged = { zoomed ->
                                        if (pageIndex == pagerState.currentPage) {
                                            isCurrentPageZoomed = zoomed
                                        }
                                    },
                                    onMultiTouchChanged = { multiTouch ->
                                        if (pageIndex == pagerState.currentPage) {
                                            isMultiTouchActive = multiTouch
                                        }
                                    }
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap!!.asImageBitmap(),
                                            contentDescription = "Página ${pageIndex + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (isPageLoading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                            is PdfSourceMode.StreamFromKomga -> {
                                val page = state.streamPages.getOrNull(pageIndex)
                                val pageNumber = page?.number ?: (pageIndex + 1)
                                val imageUrl = KomgaApiClient.getPageImageUrl(state.serverUrl, bookId, pageNumber)

                                ZoomableBox(
                                    modifier = Modifier.fillMaxSize(),
                                    resetKey = pageIndex,
                                    onTap = viewModel::toggleControls,
                                    onZoomChanged = { zoomed ->
                                        if (pageIndex == pagerState.currentPage) {
                                            isCurrentPageZoomed = zoomed
                                        }
                                    },
                                    onMultiTouchChanged = { multiTouch ->
                                        if (pageIndex == pagerState.currentPage) {
                                            isMultiTouchActive = multiTouch
                                        }
                                    }
                                ) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Página $pageNumber",
                                        contentScale = ContentScale.Fit,
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
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Top Bar Overlay
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
                                    val modeLabel = if (state.mode is PdfSourceMode.NativeRenderer) {
                                        "Renderizado nativo (PdfRenderer)"
                                    } else {
                                        "Streaming desde servidor"
                                    }
                                    Text(
                                        text = "$modeLabel • Página ${pagerState.currentPage + 1} de $totalPages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }

                                if (state.mode is PdfSourceMode.StreamFromKomga) {
                                    IconButton(
                                        onClick = viewModel::downloadAndRenderLocally
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = "Descargar para render nativo",
                                            tint = Color.White
                                        )
                                    }
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

                    // Bottom Bar Scrubber Overlay
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
                                    text = "${pagerState.currentPage + 1} / $totalPages",
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
                                            val targetPage = newValue.toInt().coerceIn(0, totalPages - 1)
                                            if (targetPage != pagerState.currentPage) {
                                                scope.launch {
                                                    pagerState.scrollToPage(targetPage)
                                                }
                                            }
                                        },
                                        valueRange = 0f..(totalPages - 1).toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.DarkGray
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            if (pagerState.currentPage < totalPages - 1) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        },
                                        enabled = pagerState.currentPage < totalPages - 1
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Siguiente",
                                            tint = if (pagerState.currentPage < totalPages - 1) Color.White else Color.Gray
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
}
