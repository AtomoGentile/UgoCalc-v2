package com.ugocalc.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.components.DailyGlycemicTimelineCard
import com.ugocalc.app.ui.components.EditorialCanvasShell
import com.ugocalc.app.ui.components.EditorialSearchBarRow
import com.ugocalc.app.ui.components.EditorialSectionLabel
import com.ugocalc.app.ui.components.EditorialTopBar
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.FloatingCircleIconButton
import com.ugocalc.app.ui.components.FloatingSplitPillActions
import com.ugocalc.app.ui.components.FramelessListRow
import com.ugocalc.app.ui.components.HistoryColumnChart
import com.ugocalc.app.ui.components.HistoryLineChart
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.buildHighlightedMatchText
import com.ugocalc.app.ui.components.formatCompactGrams
import com.ugocalc.app.ui.components.formatMealDisplayTitle
import com.ugocalc.app.ui.components.isDarkSurfaceTheme
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALIAN)
private val groupDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ITALIAN)
private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

/** Elenco delle analisi svolte, dalla più recente alla più antica (§2.3), con ricerca live e filtri per data/CHO/ora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenGlycemicSettings: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsState()
    val favoritedIds by viewModel.favoritedHistoryIds.collectAsState()
    val chartEnabled by viewModel.chartEnabled.collectAsState()
    val todayTimeline by viewModel.todayGlycemicTimeline.collectAsState()
    val chartInterval by viewModel.chartInterval.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val chartEntries by viewModel.chartEntries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filteredHistory by viewModel.filteredHistory.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<AnalysisHistoryEntity?>(null) }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    val groupedHistory = remember(filteredHistory) {
        groupHistoryByDay(filteredHistory)
    }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isDark = isDarkSurfaceTheme()

    EditorialCanvasShell(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive || searchQuery.isNotEmpty(),
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                label = "history_topbar_mode",
            ) { searching ->
                if (searching) {
                    EditorialSearchBarRow(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        placeholder = "Cerca pasto o alimento…",
                        onBack = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                } else {
                    EditorialTopBar(
                        leading = {
                            if (showBackButton) {
                                FloatingCircleIconButton(
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Indietro",
                                    onClick = onBack,
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = if (isDark) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                                    shadowElevation = if (isDark) 0.dp else 2.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                            }
                        },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (history.isNotEmpty()) {
                                    FloatingCircleIconButton(
                                        icon = Icons.Default.DeleteSweep,
                                        contentDescription = "Pulisci cronologia",
                                        onClick = { showClearConfirm = true },
                                        contentColor = MaterialTheme.colorScheme.error,
                                    )
                                }
                                FloatingSplitPillActions(
                                    leftIcon = Icons.Default.Search,
                                    leftDescription = "Cerca nella cronologia",
                                    onLeftClick = { isSearchActive = true },
                                    rightIcon = Icons.Default.FilterList,
                                    rightDescription = "Filtri",
                                    onRightClick = { showFilters = !showFilters },
                                    rightActive = filters.isActive || showFilters,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) {
        val modeTabs: List<Pair<SessionMode?, String>> = listOf(
            null to "Tutti",
            SessionMode.CASA to "Casa",
            SessionMode.RISTORANTE to "Ristorante",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = navBarBottom + 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isSearchActive && searchQuery.isEmpty()) {
                item(key = "page_title") {
                    Text(
                        text = "Cronologia",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item(key = "mode_segmented_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    modeTabs.forEach { (mode, label) ->
                        val isSelected = selectedMode == mode
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            animationSpec = tween(150),
                            label = "hist_mode_bg_$label",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(150),
                            label = "hist_mode_fg_$label",
                        )
                        Surface(
                            onClick = { viewModel.setSelectedMode(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            color = pillBg,
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = textColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

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

            if (!isSearchActive && searchQuery.isEmpty()) {
                todayTimeline?.let { timeline ->
                    item(key = "today_glycemic_timeline") {
                        DailyGlycemicTimelineCard(
                            timeline = timeline,
                            onOpenSettings = onOpenGlycemicSettings,
                        )
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
                        EditorialSectionLabel(
                            title = "$dateLabel · ${if (entries.size == 1) "1 pasto" else "${entries.size} pasti"}",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                        HistoryEntryRow(
                            entry = entry,
                            searchQuery = searchQuery,
                            isFavorite = entry.id in favoritedIds,
                            showDivider = index < entries.lastIndex,
                            onClick = { onOpenEntry(entry.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(entry) },
                            onRequestDelete = { pendingDeleteEntry = entry },
                            modifier = Modifier.animateItem(),
                        )
                    }
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
private fun HistoryEntryRow(
    entry: AnalysisHistoryEntity,
    searchQuery: String,
    isFavorite: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRistorante = entry.sessionMode == SessionMode.RISTORANTE.name
    val formattedTime = remember(entry.analyzedAtEpochMillis) {
        timeFormat.format(Date(entry.analyzedAtEpochMillis))
    }

    FramelessListRow(
        onClick = onClick,
        modifier = modifier,
        showDivider = showDivider,
        dividerStartPadding = 44.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRistorante) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRistorante) Icons.Default.Restaurant else Icons.Default.Home,
                    contentDescription = if (isRistorante) "Ristorante" else "Casa",
                    modifier = Modifier.size(16.dp),
                    tint = if (isRistorante) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Elimina analisi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    ) {
        val displayTitle = formatMealDisplayTitle(entry.foodTitle)
        Text(
            text = if (searchQuery.isNotBlank()) {
                buildHighlightedMatchText(
                    text = displayTitle,
                    query = searchQuery,
                    highlightColor = MaterialTheme.colorScheme.onSurface,
                    baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            } else {
                androidx.compose.ui.text.AnnotatedString(displayTitle)
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (searchQuery.isNotBlank()) FontWeight.Normal else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 3.dp),
        ) {
            MacroPill(
                label = "CHO",
                value = formatCompactGrams(entry.totalChoG),
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
                compact = true,
            )
            if (entry.totalProteineG > 0.0) {
                MacroPill(
                    label = "P",
                    value = formatCompactGrams(entry.totalProteineG),
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                    compact = true,
                )
            }
            MacroPill(
                label = "G",
                value = formatCompactGrams(entry.totalGrassiG),
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
                compact = true,
            )
            if (entry.glycemicLoad > 0.0) {
                MacroPill(
                    label = "GL",
                    value = "%.1f".format(Locale.ITALIAN, entry.glycemicLoad),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
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
