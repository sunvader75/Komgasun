package com.sunvader.komgasun.ui.libraries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunvader.komgasun.R
import com.sunvader.komgasun.data.model.BookDto
import com.sunvader.komgasun.data.model.ItemFilter
import com.sunvader.komgasun.data.model.LibraryDto
import com.sunvader.komgasun.data.model.ServerType
import com.sunvader.komgasun.data.model.matchesFilters
import com.sunvader.komgasun.ui.books.BookGridItem
import com.sunvader.komgasun.ui.common.EmptyView
import com.sunvader.komgasun.ui.common.ErrorView
import com.sunvader.komgasun.ui.common.FilterChipsRow
import com.sunvader.komgasun.ui.common.LoadingView
import com.sunvader.komgasun.ui.common.ServerSwitchDialog
import kotlinx.coroutines.launch

/**
 * Presenta las bibliotecas filtradas y las lecturas en curso con estados de carga/error separados.
 * Recoge el estado con el ciclo de vida y actualiza las lecturas al reanudarse.
 * Confirma las operaciones destructivas y muestra sus resultados; delega la sesión y las URL
 * en el ViewModel. [onLogout] se ejecuta tras cerrar sesión, [onLibraryClick] entrega ID y nombre
 * y [onBookClick] entrega el libro seleccionado para que el contenedor elija el lector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    onLibraryClick: (libraryId: String, libraryName: String) -> Unit,
    onLogout: () -> Unit,
    onBookClick: (BookDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrariesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredLibraries = state.libraries.filter { it.matchesFilters(state.selectedFilters) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.loadInProgressBooks()
        onPauseOrDispose { }
    }

    var bookToDelete by remember { mutableStateOf<BookDto?>(null) }
    var libraryToEmptyTrash by remember { mutableStateOf<LibraryDto?>(null) }
    var showEmptyAllDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

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
                    enabled = target.id !in state.updatingBooks,
                    onClick = {
                        bookToDelete = null
                        viewModel.deleteBook(target.id) { res ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (res.isSuccess) "Archivo de '$title' eliminado"
                                    else "Error al eliminar: ${res.exceptionOrNull()?.localizedMessage}"
                                )
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

    if (showServerDialog) {
        ServerSwitchDialog(
            activeServerType = state.activeServerType,
            localUrl = state.localUrl,
            remoteUrl = state.remoteUrl,
            onDismiss = { showServerDialog = false },
            onSwitchServer = { targetType ->
                showServerDialog = false
                viewModel.switchServer(targetType) { res ->
                    scope.launch {
                        if (res.isSuccess) {
                            val url = res.getOrNull().orEmpty()
                            snackbarHostState.showSnackbar("Cambiado a ${targetType.label}: $url")
                        } else {
                            snackbarHostState.showSnackbar("Error al cambiar: ${res.exceptionOrNull()?.localizedMessage}")
                        }
                    }
                }
            },
            onSaveUrls = { local, remote, active ->
                viewModel.saveServerUrls(local, remote, active) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Direcciones de servidor guardadas")
                    }
                }
            }
        )
    }

    if (libraryToEmptyTrash != null) {
        val target = libraryToEmptyTrash!!
        AlertDialog(
            onDismissRequest = { libraryToEmptyTrash = null },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = {
                Text("¿Vaciar papelera de la biblioteca '${target.name}'? Se eliminarán definitivamente de la base de datos de Komga todos los elementos marcados como no disponibles.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryToEmptyTrash = null
                        viewModel.emptyTrash(target.id) { res ->
                            scope.launch {
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Papelera de '${target.name}' vaciada")
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
                TextButton(onClick = { libraryToEmptyTrash = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEmptyAllDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyAllDialog = false },
            title = { Text(stringResource(R.string.empty_trash_all)) },
            text = {
                Text("¿Vaciar la papelera de todas las bibliotecas? Se purgarán permanentemente de la base de datos todos los elementos eliminados o no disponibles.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyAllDialog = false
                        viewModel.emptyTrashAll { res ->
                            scope.launch {
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Todas las papeleras fueron vaciadas")
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
                TextButton(onClick = { showEmptyAllDialog = false }) {
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
                title = { Text(stringResource(R.string.libraries)) },
                actions = {
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(
                            imageVector = if (state.activeServerType == ServerType.LOCAL) Icons.Default.Wifi else Icons.Default.VpnKey,
                            contentDescription = stringResource(R.string.switch_server)
                        )
                    }
                    IconButton(onClick = { showEmptyAllDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.empty_trash_all)
                        )
                    }
                    IconButton(onClick = viewModel::loadLibraries) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                    }
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout)
                        )
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
                        onRetry = viewModel::loadLibraries,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (filteredLibraries.isEmpty()) {
                            item {
                                Text(if (state.libraries.isEmpty()) stringResource(R.string.empty_libraries)
                                    else "No hay bibliotecas que coincidan con el filtro seleccionado")
                            }
                        }
                        items(filteredLibraries, key = { it.id }) { library ->
                            LibraryItem(
                                library = library,
                                onClick = { onLibraryClick(library.id, library.name) },
                                onEmptyTrash = { libraryToEmptyTrash = library }
                            )
                        }
                        item {
                            Text("Continuar leyendo", style = MaterialTheme.typography.titleLarge)
                        }
                        item {
                            when {
                                state.progressLoading -> LoadingView(modifier = Modifier.fillMaxWidth().height(100.dp))
                                state.progressError != null -> ErrorView(
                                    message = state.progressError.orEmpty(),
                                    onRetry = viewModel::loadInProgressBooks,
                                    modifier = Modifier.fillMaxWidth().height(160.dp)
                                )
                                state.inProgressBooks.isEmpty() -> Text("No hay libros ni cómics en progreso")
                                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.inProgressBooks, key = { it.id }) { book ->
                                        BookGridItem(
                                            book = book,
                                            serverUrl = state.activeUrl,
                                            onClick = { onBookClick(book) },
                                            onDeleteClick = {
                                                if (book.id !in state.updatingBooks) bookToDelete = book
                                            },
                                            readStatusUpdating = book.id in state.updatingBooks,
                                            onReadStatusClick = { completed ->
                                                viewModel.setBookReadStatus(book, completed) { result ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            if (result.isSuccess) "Estado sincronizado con Komga"
                                                            else "No se pudo sincronizar: ${result.exceptionOrNull()?.localizedMessage}"
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.width(150.dp)
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

/**
 * Separa la apertura de la biblioteca de la solicitud de vaciar su papelera.
 * [onEmptyTrash] no ejecuta el borrado: el contenedor debe confirmar la operación.
 */
@Composable
fun LibraryItem(
    library: LibraryDto,
    onClick: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (library.root.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = library.root,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onEmptyTrash,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.empty_trash),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
