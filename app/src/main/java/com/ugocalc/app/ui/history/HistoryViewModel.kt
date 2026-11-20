package com.ugocalc.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.CalculatedFoodLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject

/** Intervallo del grafico (§ richiesta utente: "intervallo selezionabile dall'utente"). */
enum class HistoryChartInterval(val label: String, val days: Int?) {
    WEEK("7 giorni", 7),
    MONTH("30 giorni", 30),
    ALL("Tutto", null),
}

/** Tipo di grafico (§ richiesta utente: "permettere all'utente di scegliere il grafico"). */
enum class HistoryChartType(val label: String) {
    COLUMNS("Colonne"),
    LINE("Linea"),
}

/** Fascia CHO mostrata dal cursore dei filtri (§ richiesta utente): copre le porzioni tipiche di un pasto. */
const val HISTORY_CHO_FILTER_MAX = 300f

/**
 * Filtri in Cronologia (§ richiesta utente: "per data, CHO, range orario"). `choRange`/`hourRange`
 * agli estremi equivalgono a "nessun filtro" — non serve un flag separato per "attivo/disattivo".
 */
data class HistoryFilters(
    val dateFromMillis: Long? = null,
    val dateToMillis: Long? = null,
    val choRange: ClosedFloatingPointRange<Float> = 0f..HISTORY_CHO_FILTER_MAX,
    val hourRange: ClosedFloatingPointRange<Float> = 0f..24f,
) {
    val isActive: Boolean
        get() = dateFromMillis != null || dateToMillis != null ||
            choRange != 0f..HISTORY_CHO_FILTER_MAX || hourRange != 0f..24f
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val benchmarkRepository: BenchmarkRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val json: Json,
) : ViewModel() {

    private val _selectedMode = MutableStateFlow<SessionMode?>(null)
    val selectedMode: StateFlow<SessionMode?> = _selectedMode.asStateFlow()

    fun setSelectedMode(mode: SessionMode?) {
        _selectedMode.value = mode
        resetFilters()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Cronologia del modo selezionato (o tutti se null), dalla più recente alla più antica. */
    val history: StateFlow<List<AnalysisHistoryEntity>> = _selectedMode
        .flatMapLatest { mode ->
            if (mode == null) {
                historyRepository.observeHistory()
            } else {
                historyRepository.observeHistoryByMode(mode)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Id delle voci di cronologia già tra i preferiti, per mostrare la stellina piena o vuota. */
    val favoritedHistoryIds: StateFlow<Set<Long>> = favoritesRepository.observeFavorites()
        .map { favorites -> favorites.mapNotNull { it.historyEntryId }.toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val chartEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.historyChartEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _chartInterval = MutableStateFlow(HistoryChartInterval.WEEK)
    val chartInterval: StateFlow<HistoryChartInterval> = _chartInterval.asStateFlow()

    private val _chartType = MutableStateFlow(HistoryChartType.COLUMNS)
    val chartType: StateFlow<HistoryChartType> = _chartType.asStateFlow()

    fun setChartType(type: HistoryChartType) {
        _chartType.value = type
    }

    /** Voci nell'intervallo scelto, dal più vecchio al più recente (§ HistoryColumnChart: le colonne procedono nel tempo da sinistra a destra). */
    val chartEntries: StateFlow<List<AnalysisHistoryEntity>> = combine(history, _chartInterval) { entries, interval ->
        val cutoff = interval.days?.let { System.currentTimeMillis() - it * 24L * 60 * 60 * 1000 }
        entries.filter { cutoff == null || it.analyzedAtEpochMillis >= cutoff }.sortedBy { it.analyzedAtEpochMillis }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChartInterval(interval: HistoryChartInterval) {
        _chartInterval.value = interval
    }

    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    /** Cronologia filtrata per filtri avanzati e ricerca testuale. */
    val filteredHistory: StateFlow<List<AnalysisHistoryEntity>> = combine(
        history,
        _filters,
        _searchQuery,
    ) { entries, filters, rawQuery ->
        val query = rawQuery.trim()
        val calendar = Calendar.getInstance()
        entries.filter { entry ->
            val afterFrom = filters.dateFromMillis?.let { entry.analyzedAtEpochMillis >= it } ?: true
            val beforeTo = filters.dateToMillis?.let { entry.analyzedAtEpochMillis <= it } ?: true
            val inChoRange = entry.totalChoG.toFloat() in filters.choRange

            val hour = if (filters.hourRange != 0f..24f) {
                calendar.timeInMillis = entry.analyzedAtEpochMillis
                calendar.get(Calendar.HOUR_OF_DAY).toFloat()
            } else 12f
            val inHourRange = hour in filters.hourRange

            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                matchesSearchQuery(entry, query)
            }

            afterFrom && beforeTo && inChoRange && inHourRange && matchesQuery
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun matchesSearchQuery(entry: AnalysisHistoryEntity, query: String): Boolean {
        if (entry.foodTitle.contains(query, ignoreCase = true)) return true
        val lines = runCatching { json.decodeFromString<List<CalculatedFoodLine>>(entry.detailsJson) }.getOrNull()
        return if (lines != null) {
            lines.any { line ->
                line.recognizedFood.nome.contains(query, ignoreCase = true) ||
                    (line.tableEntry?.nome?.contains(query, ignoreCase = true) == true)
            }
        } else {
            entry.detailsJson.contains(query, ignoreCase = true)
        }
    }

    fun setDateFrom(millis: Long?) = _filters.update { it.copy(dateFromMillis = millis) }
    fun setDateTo(millis: Long?) = _filters.update { it.copy(dateToMillis = millis) }
    fun setChoRange(range: ClosedFloatingPointRange<Float>) = _filters.update { it.copy(choRange = range) }
    fun setHourRange(range: ClosedFloatingPointRange<Float>) = _filters.update { it.copy(hourRange = range) }
    fun resetFilters() {
        _filters.value = HistoryFilters()
    }

    fun clearAllFiltersAndSearch() {
        _filters.value = HistoryFilters()
        _searchQuery.value = ""
    }

    /** Elimina una singola voce dalla cronologia insieme ai dati V2/benchmark collegati. */
    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            val entry = historyRepository.getById(id)
            historyRepository.deleteAnalysis(id)
            entry?.sessionId?.let { telemetryRepository.deleteBySessionId(it) }
        }
    }

    /** "Pulizia database cronologia" (§2.2, icona cestino). */
    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            benchmarkRepository.clearAll()
            telemetryRepository.deleteAll()
        }
    }

    /** Stellina in Cronologia (§ richiesta utente): aggiunge/rimuove dai preferiti senza passare da Impostazioni. */
    fun toggleFavorite(entry: AnalysisHistoryEntity) {
        viewModelScope.launch {
            val favorites = favoritesRepository.observeFavorites().first()
            val existing = favorites.find { it.historyEntryId == entry.id }
            if (existing != null) {
                favoritesRepository.removeFavorite(existing)
            } else {
                favoritesRepository.addFavorite(entry.id, entry.foodTitle, favorites.size)
            }
        }
    }
}
