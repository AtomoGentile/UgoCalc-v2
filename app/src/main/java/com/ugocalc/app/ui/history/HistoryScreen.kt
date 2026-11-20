package com.ugocalc.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.HistoryColumnChart
import com.ugocalc.app.ui.components.HistoryLineChart
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.SpaceM
import com.ugocalc.app.ui.theme.SpaceS
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALIAN)
private val groupDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ITALIAN)
private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

/** Elenco delle analisi svolte, dalla più recente alla più antica (§2.3), con ricerca e filtri per data/CHO/ora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    showBackButton: Boolean = true,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsState()
    val favoritedIds by viewModel.favoritedHistoryIds.collectAsState()
    val chartEnabled by viewModel.chartEnabled.collectAsState()
    val chartInterval by viewModel.chartInterval.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val chartEntries by viewModel.chartEntries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filteredHistory by viewModel.filteredHistory.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<AnalysisHistoryEntity?>(null) }

    val groupedHistory = remember(filteredHistory) {
        groupHistoryByDay(filteredHistory)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cronologia") },
                navigationIcon = {
                    if (showBackButton) {
                        BackIconButton(onBack)
                    }
                },
                colors = ugoTopAppBarColors(),
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtri",
                            tint = if (filters.isActive || showFilters) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Pulisci cronologia",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val modeTabs: List<Pair<SessionMode?, String>> = listOf(
                null to "Tutti",
                SessionMode.CASA to "Casa",
                SessionMode.RISTORANTE to "Ristorante",
            )
            val selectedTabIndex = modeTabs.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                modeTabs.forEachIndexed { index, (mode, label) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setSelectedMode(mode) },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Cerca pasto o alimento…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella ricerca")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceM, vertical = 10.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = SpaceM, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showFilters) {
                    item(key = "filter_panel") {
                        AnimatedVisibility(visible = showFilters) {
                            HistoryFilterPanel(
                                filters = filters,
                                onDateFromSelected = viewModel::setDateFrom,
                                onDateToSelected = viewModel::setDateTo,
                                onChoRangeChange = viewModel::setChoRange,
                                onHourRangeChange = viewModel::setHourRange,
                                onReset = viewModel::resetFilters,
                            )
                        }
                    }
                }

                if (chartEnabled && history.isNotEmpty()) {
                    item(key = "history_chart") {
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            UgoSectionHeader(
                                title = "Andamento pasti",
                                subtitle = "${chartEntries.size} analisi nell'intervallo",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HistoryChartInterval.entries.forEach { interval ->
                                    FilterChip(
                                        selected = chartInterval == interval,
                                        onClick = { viewModel.setChartInterval(interval) },
                                        label = { Text(interval.label, maxLines = 1) },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HistoryChartType.entries.forEach { type ->
                                    FilterChip(
                                        selected = chartType == type,
                                        onClick = { viewModel.setChartType(type) },
                                        label = { Text(type.label, maxLines = 1) },
                                    )
                                }
                            }
                            when (chartType) {
                                HistoryChartType.COLUMNS -> HistoryColumnChart(
                                    entries = chartEntries,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                HistoryChartType.LINE -> HistoryLineChart(
                                    entries = chartEntries,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }

                val hasActiveSearchOrFilters = filters.isActive || searchQuery.isNotBlank()
                if (filteredHistory.isEmpty()) {
                    item(key = "empty_history") {
                        EmptyStateMessage(
                            icon = Icons.Default.History,
                            title = if (hasActiveSearchOrFilters) "Nessun risultato" else "Cronologia vuota",
                            message = if (hasActiveSearchOrFilters) {
                                "Nessuna analisi corrisponde alla ricerca o ai filtri impostati."
                            } else {
                                "Le analisi completate appariranno qui automaticamente."
                            },
                            actionLabel = if (hasActiveSearchOrFilters) "Ripristina filtri" else null,
                            onAction = if (hasActiveSearchOrFilters) {
                                { viewModel.clearAllFiltersAndSearch() }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                        )
                    }
                } else {
                    groupedHistory.forEach { (dateLabel, entries) ->
                        item(key = "header_$dateLabel") {
                            UgoSectionHeader(
                                title = dateLabel,
                                subtitle = if (entries.size == 1) "1 pasto" else "${entries.size} pasti",
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(entries, key = { it.id }) { entry ->
                            HistoryEntryCard(
                                entry = entry,
                                isFavorite = entry.id in favoritedIds,
                                onClick = { onOpenEntry(entry.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(entry) },
                                onRequestDelete = { pendingDeleteEntry = entry },
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showClearConfirm) {
        UgoConfirmDialog(
            title = "Pulisci cronologia",
            message = "Vuoi eliminare definitivamente tutte le analisi salvate e i relativi dati V2?",
            confirmLabel = "Elimina tutto",
            isDestructive = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearHistory()
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    pendingDeleteEntry?.let { entry ->
        UgoConfirmDialog(
            title = "Elimina analisi",
            message = "Vuoi eliminare \"${entry.foodTitle}\" dalla cronologia?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDeleteEntry = null
                viewModel.deleteHistoryEntry(entry.id)
            },
            onDismiss = { pendingDeleteEntry = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntryCard(
    entry: AnalysisHistoryEntity,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val isRistorante = entry.sessionMode == SessionMode.RISTORANTE.name
    val formattedTime = remember(entry.analyzedAtEpochMillis) {
        timeFormat.format(Date(entry.analyzedAtEpochMillis))
    }

    UgoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.foodTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isRistorante) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (isRistorante) Icons.Default.Restaurant else Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (isRistorante) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = if (isRistorante) "Ristorante" else "Casa",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRistorante) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${"%.1f".format(entry.totalChoG)} g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    MacroPill(
                        label = "Grassi",
                        value = "${"%.1f".format(entry.totalGrassiG)} g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Elimina analisi",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

private fun groupHistoryByDay(entries: List<AnalysisHistoryEntity>): Map<String, List<AnalysisHistoryEntity>> {
    if (entries.isEmpty()) return emptyMap()
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val itemCal = Calendar.getInstance()

    val grouped = linkedMapOf<String, MutableList<AnalysisHistoryEntity>>()
    for (entry in entries) {
        itemCal.timeInMillis = entry.analyzedAtEpochMillis
        val label = when {
            isSameDay(itemCal, todayCal) -> "Oggi"
            isSameDay(itemCal, yesterdayCal) -> "Ieri"
            else -> groupDateFormat.format(Date(entry.analyzedAtEpochMillis))
        }
        grouped.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return grouped
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

@Composable
private fun HistoryFilterPanel(
    filters: HistoryFilters,
    onDateFromSelected: (Long?) -> Unit,
    onDateToSelected: (Long?) -> Unit,
    onChoRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onHourRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onReset: () -> Unit,
) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        UgoSectionHeader(
            title = "Filtri avanzati",
            actionLabel = if (filters.isActive) "Reimposta" else null,
            onAction = if (filters.isActive) onReset else null,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DateFilterButton(
                label = "Da",
                selectedMillis = filters.dateFromMillis,
                onSelected = onDateFromSelected,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            DateFilterButton(
                label = "A",
                selectedMillis = filters.dateToMillis,
                onSelected = onDateToSelected,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "CHO: ${filters.choRange.start.toInt()}–${filters.choRange.endInclusive.toInt()} g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        RangeSlider(
            value = filters.choRange,
            onValueChange = onChoRangeChange,
            valueRange = 0f..HISTORY_CHO_FILTER_MAX,
        )

        Text(
            "Ora del giorno: ${filters.hourRange.start.toInt()}:00–${filters.hourRange.endInclusive.toInt()}:00",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RangeSlider(
            value = filters.hourRange,
            onValueChange = onHourRangeChange,
            valueRange = 0f..24f,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterButton(
    label: String,
    selectedMillis: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
            Text(selectedMillis?.let { dateOnlyFormat.format(Date(it)) } ?: label, maxLines = 1)
        }
        if (selectedMillis != null) {
            IconButton(onClick = { onSelected(null) }) {
                Icon(Icons.Default.Close, contentDescription = "Rimuovi filtro \"$label\"")
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { onSelected(state.selectedDateMillis); showPicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
