package com.example.komgasun.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.komgasun.R
import com.example.komgasun.data.model.ItemFilter
import com.example.komgasun.data.model.SeriesDto
import com.example.komgasun.data.model.SeriesSortOrder
import com.example.komgasun.data.model.matchesFilters
import com.example.komgasun.data.model.sortedByOrder
import com.example.komgasun.data.remote.KomgaApiClient
import com.example.komgasun.ui.common.EmptyView
import com.example.komgasun.ui.common.ErrorView
import com.example.komgasun.ui.common.FilterChipsRow
import com.example.komgasun.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    libraryId: String,
    libraryName: String,
    onSeriesClick: (seriesId: String, seriesTitle: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = viewModel(key = libraryId) { SeriesViewModel(libraryId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredSeries = remember(state.seriesList, state.selectedFilters, state.sortOrder, state.randomSeed) {
        state.seriesList
            .filter { it.matchesFilters(state.selectedFilters) }
            .sortedByOrder(state.sortOrder, state.randomSeed)
    }

    LifecycleResumeEffect(libraryId) {
        viewModel.loadSeries(showLoading = false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = libraryName.ifBlank { stringResource(R.string.series) },
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
                    if (state.sortOrder == SeriesSortOrder.RANDOM) {
                        IconButton(onClick = viewModel::reshuffle) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Reordenar aleatoriamente"
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Ordenar"
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SeriesSortOrder.entries.forEach { order ->
                                val isSelected = state.sortOrder == order
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                    trailingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }

                    IconButton(onClick = viewModel::loadSeries) {
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
                        onRetry = viewModel::loadSeries,
                        modifier = Modifier.weight(1f)
                    )
                }
                filteredSeries.isEmpty() -> {
                    EmptyView(
                        message = if (state.seriesList.isEmpty()) {
                            stringResource(R.string.empty_series)
                        } else {
                            "No hay series que coincidan con el filtro seleccionado"
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
                        items(filteredSeries, key = { it.id }) { series ->
                            SeriesGridItem(
                                series = series,
                                serverUrl = state.serverUrl,
                                onClick = {
                                    val title = series.metadata.title.ifBlank { series.name }
                                    onSeriesClick(series.id, title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesGridItem(
    series: SeriesDto,
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = series.metadata.title.ifBlank { series.name }
    val thumbnailUrl = if (serverUrl.isNotBlank()) {
        KomgaApiClient.getSeriesThumbnailUrl(serverUrl, series.id)
    } else ""

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

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(topStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "${series.booksCount} ${stringResource(R.string.books).lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Read / Availability status badge
                val statusText: String? = when {
                    series.deleted || series.unavailable -> "No disponible"
                    series.booksReadCount == series.booksCount && series.booksCount > 0 -> "Leído"
                    series.booksInProgressCount > 0 -> "En progreso"
                    series.booksUnreadCount > 0 && series.booksReadCount == 0 -> "Sin leer"
                    else -> null
                }

                if (statusText != null) {
                    val badgeColor = when (statusText) {
                        "No disponible" -> MaterialTheme.colorScheme.errorContainer
                        "Leído" -> MaterialTheme.colorScheme.primaryContainer
                        "En progreso" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val textColor = when (statusText) {
                        "No disponible" -> MaterialTheme.colorScheme.onErrorContainer
                        "Leído" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "En progreso" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
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
            }
        }
    }
}
