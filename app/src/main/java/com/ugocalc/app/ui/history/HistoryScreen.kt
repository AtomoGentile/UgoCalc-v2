package com.ugocalc.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.HistoryColumnChart
import com.ugocalc.app.ui.components.HistoryLineChart
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

/** Elenco delle analisi svolte, dalla più recente alla più antica (§2.3), con filtri per data/CHO/ora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val favoritedIds by viewModel.favoritedHistoryIds.collectAsState()
    val chartEnabled by viewModel.chartEnabled.collectAsState()
    val chartInterval by viewModel.chartInterval.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val chartEntries by viewModel.chartEntries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filteredHistory by viewModel.filteredHistory.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cronologia") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtri",
                            tint = if (filters.isActive) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                        )
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Pulisci cronologia")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (showFilters) {
                item {
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
            if (chartEnabled) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            HistoryChartInterval.entries.forEach { interval ->
                                FilterChip(
                                    selected = chartInterval == interval,
                                    onClick = { viewModel.setChartInterval(interval) },
                                    label = { Text(interval.label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            HistoryChartType.entries.forEach { type ->
                                FilterChip(
                                    selected = chartType == type,
                                    onClick = { viewModel.setChartType(type) },
                                    label = { Text(type.label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        when (chartType) {
                            HistoryChartType.COLUMNS -> HistoryColumnChart(entries = chartEntries, modifier = Modifier.padding(top = 12.dp))
                            HistoryChartType.LINE -> HistoryLineChart(entries = chartEntries, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            if (filters.isActive && filteredHistory.isEmpty()) {
                item {
                    Text(
                        "Nessuna analisi corrisponde ai filtri impostati",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(filteredHistory, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpenEntry(entry.id) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${entry.foodTitle} — ${dateFormat.format(Date(entry.analyzedAtEpochMillis))}", fontWeight = FontWeight.Bold)
                            Text(
                                "CHO ${"%.1f".format(entry.totalChoG)}g · Grassi ${"%.1f".format(entry.totalGrassiG)}g",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        val isFavorite = entry.id in favoritedIds
                        IconButton(onClick = { viewModel.toggleFavorite(entry) }) {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterPanel(
    filters: HistoryFilters,
    onDateFromSelected: (Long?) -> Unit,
    onDateToSelected: (Long?) -> Unit,
    onChoRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onHourRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text("Filtri", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DateFilterButton(
                label = "Da",
                selectedMillis = filters.dateFromMillis,
                onSelected = onDateFromSelected,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
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
        )
        RangeSlider(
            value = filters.hourRange,
            onValueChange = onHourRangeChange,
            valueRange = 0f..24f,
        )

        if (filters.isActive) {
            TextButton(onClick = onReset) { Text("Reimposta filtri") }
        }
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
            Text(selectedMillis?.let { dateOnlyFormat.format(Date(it)) } ?: label)
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
