package com.example.komgasun.ui.books

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.komgasun.R
import com.example.komgasun.data.model.BookDto
import com.example.komgasun.data.model.BookType
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.detectBookType
import com.example.komgasun.data.model.formatBadge
import com.example.komgasun.data.model.matchesFilters
import com.example.komgasun.data.model.sortedByNumbering
import com.example.komgasun.data.remote.KomgaApiClient
import com.example.komgasun.ui.common.EmptyView
import com.example.komgasun.ui.common.ErrorView
import com.example.komgasun.ui.common.FilterChipsRow
import com.example.komgasun.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    seriesId: String,
    seriesTitle: String,
    onBookClick: (book: BookDto) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BooksViewModel = viewModel(key = seriesId) { BooksViewModel(seriesId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredBooks = remember(state.books, state.selectedFilters) {
        state.books
            .filter { it.matchesFilters(state.selectedFilters) }
            .sortedByNumbering()
    }

    LifecycleResumeEffect(seriesId) {
        viewModel.loadBooks(showLoading = false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
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
                                onClick = { onBookClick(book) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookGridItem(
    book: BookDto,
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = book.metadata.title.ifBlank { book.name }
    val thumbnailUrl = if (serverUrl.isNotBlank()) {
        KomgaApiClient.getBookThumbnailUrl(serverUrl, book.id)
    } else ""

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
                if (book.media.pagesCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "${book.media.pagesCount} p.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top-End Read / Availability status badge
                val statusText: String? = when {
                    book.deleted || book.media.status.equals("UNAVAILABLE", true) || book.media.status.equals("ERROR", true) -> "No disp."
                    book.readProgress?.completed == true -> "Leído"
                    book.readProgress != null && book.readProgress.page > 0 -> "Pág. ${book.readProgress.page}"
                    else -> null
                }

                if (statusText != null) {
                    val statusBgColor = when (statusText) {
                        "No disp." -> MaterialTheme.colorScheme.errorContainer
                        "Leído" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    val statusTextColor = when (statusText) {
                        "No disp." -> MaterialTheme.colorScheme.onErrorContainer
                        "Leído" -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }

                    Surface(
                        color = statusBgColor.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.metadata.number.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "#${book.metadata.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
