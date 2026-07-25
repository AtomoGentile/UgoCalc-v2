package com.ugocalc.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(SessionMode.CASA)
    val selectedMode: StateFlow<SessionMode> = _selectedMode

    fun setSelectedMode(mode: SessionMode) {
        _selectedMode.value = mode
        resetFilters()
    }

    /** Cronologia del modo selezionato, dalla più recente alla più antica. */
    val history: StateFlow<List<AnalysisHistoryEntity>> = _selectedMode
        .flatMapLatest { mode -> historyRepository.observeHistoryByMode(mode) }
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
    val chartInterval: StateFlow<HistoryChartInterval> = _chartInterval

    private val _chartType = MutableStateFlow(HistoryChartType.COLUMNS)
    val chartType: StateFlow<HistoryChartType> = _chartType

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
    val filters: StateFlow<HistoryFilters> = _filters

    /** Cronologia filtrata (§ richiesta utente): quella mostrata nell'elenco, indipendente dall'intervallo del grafico. */
    val filteredHistory: StateFlow<List<AnalysisHistoryEntity>> = combine(history, _filters) { entries, filters ->
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
            afterFrom && beforeTo && inChoRange && inHourRange
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDateFrom(millis: Long?) = _filters.update { it.copy(dateFromMillis = millis) }
    fun setDateTo(millis: Long?) = _filters.update { it.copy(dateToMillis = millis) }
    fun setChoRange(range: ClosedFloatingPointRange<Float>) = _filters.update { it.copy(choRange = range) }
    fun setHourRange(range: ClosedFloatingPointRange<Float>) = _filters.update { it.copy(hourRange = range) }
    fun resetFilters() { _filters.value = HistoryFilters() }

    /** "Pulizia database cronologia" (§2.2, icona cestino). */
    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
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
