package com.sunvader.komgasun.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sunvader.komgasun.R
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.BookType
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.detectBookType
import com.sunvader.komgasun.data.model.formatBadge
import com.sunvader.komgasun.data.model.matchesFilters
import com.sunvader.komgasun.data.model.sortedByNumbering
import com.sunvader.komgasun.data.remote.KomgaApiClient
import com.sunvader.komgasun.ui.common.EmptyView
import com.sunvader.komgasun.ui.common.ErrorView
import com.sunvader.komgasun.ui.common.FilterChipsRow
import com.sunvader.komgasun.ui.common.LoadingView
import kotlinx.coroutines.launch

/**
 * Muestra los libros filtrados de la serie y recoge el estado respetando el ciclo de vida.
 * Recarga al reanudarse sin forzar el indicador; distingue carga, error y lista vacía.
 * Confirma los borrados y el vaciado de papelera antes de delegarlos en el ViewModel.
 * [onBookClick] recibe el libro elegido; [onBack] también se invoca tras borrar la serie con éxito.
 * [libraryId] habilita el vaciado de papelera cuando no está vacío.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    seriesId: String,
    seriesTitle: String,
    onBookClick: (book: BookDto) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    libraryId: String = "",
    viewModel: BooksViewModel = viewModel(key = seriesId) { BooksViewModel(seriesId, libraryId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bookToDelete by remember { mutableStateOf<BookDto?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    val filteredBooks = remember(state.books, state.selectedFilters) {
        state.books
            .filter { it.matchesFilters(state.selectedFilters) }
            .sortedByNumbering()
    }

    LifecycleResumeEffect(seriesId) {
        viewModel.loadBooks(showLoading = false)
        onPauseOrDispose { }
    }

    if (bookToDelete != null) {
        val target = bookToDelete!!
        val title = target.metadata.title.ifBlank { target.name }
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text(stringResource(R.string.delete_file)) },
            text = {
                Text("¿Eliminar archivo de '$title'? Esta acción eliminará físicamente el archivo del cómic del servidor. Quedará como no disponible hasta vaciar la papelera.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete = null
                        viewModel.deleteBook(target.id) { res ->
                            scope.launch {
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Archivo de '$title' eliminado")
                                } else {
                                    snackbarHostState.showSnackbar("Error al eliminar: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteSeriesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSeriesDialog = false },
            title = { Text(stringResource(R.string.delete_series)) },
            text = {
                Text("¿Eliminar todos los archivos de la serie '$seriesTitle'? Se borrarán del servidor los archivos de todos sus libros. Quedarán como no disponibles hasta vaciar la papelera.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSeriesDialog = false
                        viewModel.deleteSeries { res ->
                            if (res.isSuccess) {
                                onBack()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error al eliminar serie: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = {
                Text("¿Vaciar papelera de la biblioteca? Se eliminarán definitivamente de la base de datos todos los elementos marcados como no disponibles.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyTrashDialog = false
                        viewModel.emptyTrash { res ->
                            scope.launch {
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Papelera vaciada")
                                } else {
                                    snackbarHostState.showSnackbar("Error al vaciar: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.empty_trash), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = seriesTitle.ifBlank { stringResource(R.string.books) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    if (libraryId.isNotBlank()) {
                        IconButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.empty_trash)
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Opciones"
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_series), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showTopMenu = false
                                    showDeleteSeriesDialog = true
                                }
                            )
                        }
                    }

                    IconButton(onClick = viewModel::loadBooks) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FilterChipsRow(
                selectedFilters = state.selectedFilters,
                onToggleFilter = viewModel::toggleFilter,
                onClearFilters = viewModel::clearFilters
            )

            when {
                state.isLoading -> {
                    LoadingView(modifier = Modifier.weight(1f))
                }
                state.errorMessage != null -> {
                    ErrorView(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::loadBooks,
                        modifier = Modifier.weight(1f)
                    )
                }
                filteredBooks.isEmpty() -> {
                    EmptyView(
                        message = if (state.books.isEmpty()) {
                            stringResource(R.string.empty_books)
                        } else {
                            "No hay libros que coincidan con el filtro seleccionado"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookGridItem(
                                book = book,
                                serverUrl = state.serverUrl,
                                onClick = { onBookClick(book) },
                                onDeleteClick = { bookToDelete = book },
                                readStatusUpdating = book.id in state.updatingReadStatus,
                                onReadStatusClick = { completed ->
                                    viewModel.setBookReadStatus(book, completed) { result ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Estado sincronizado con Komga"
                                                else "No se pudo sincronizar: ${result.exceptionOrNull()?.localizedMessage}"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Presenta portada, formato y progreso usando los mismos criterios que el catálogo.
 * Una [serverUrl] vacía evita solicitar la miniatura. Las acciones solo emiten callbacks:
 * [onDeleteClick] no borra ni pide confirmación y [onReadStatusClick] recibe el estado deseado.
 * [readStatusUpdating] deshabilita los cambios de lectura; [showActions] oculta todo el menú.
 */
@Composable
fun BookGridItem(
    book: BookDto,
    serverUrl: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    readStatusUpdating: Boolean = false,
    onReadStatusClick: (Boolean) -> Unit = {},
    showActions: Boolean = true
) {
    val title = book.metadata.title.ifBlank { book.name }
    val thumbnailUrl = if (serverUrl.isNotBlank()) {
        KomgaApiClient.getBookThumbnailUrl(serverUrl, book.id)
    } else ""

    val readingStatus = book.readingStatus()
    val coverPages = book.coverPageCount()
    val badgeText = book.formatBadge()
    val badgeColor = when (book.detectBookType()) {
        BookType.PDF -> MaterialTheme.colorScheme.error
        BookType.EPUB -> MaterialTheme.colorScheme.tertiary
        BookType.COMIC -> MaterialTheme.colorScheme.primary
        BookType.UNKNOWN -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top format badge (CBZ, CBR, PDF, EPUB)
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Bottom page count
                if (coverPages != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(bottom = if (readingStatus.fraction != null) 6.dp else 0.dp)
                    ) {
                        Text(
                            text = "$coverPages p.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Separate row below the format badge prevents collisions on narrow covers.
                readingStatus.readBadge?.let { label ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                readingStatus.fraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    readingStatus.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (readingStatus.unavailable) {
                        Text(
                            text = "No disponible",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (book.metadata.number.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "#${book.metadata.number}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showActions) {
                var showItemMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showItemMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showItemMenu,
                        onDismissRequest = { showItemMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Marcar como leído") },
                            enabled = !readStatusUpdating,
                            onClick = {
                                showItemMenu = false
                                onReadStatusClick(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcar como no leído") },
                            enabled = !readStatusUpdating,
                            onClick = {
                                showItemMenu = false
                                onReadStatusClick(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_file), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showItemMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
                }
            }
        }
    }
}
