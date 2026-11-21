package com.ugocalc.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicSimulationResult
import com.ugocalc.app.domain.model.CalculatedFoodLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HistoryDetailUiState(
    val foodTitle: String = "",
    val analyzedAtEpochMillis: Long? = null,
    val sessionMode: String = "",
    val totalChoG: Double = 0.0,
    val totalProteineG: Double = 0.0,
    val totalGrassiG: Double = 0.0,
    val glycemicLoad: Double = 0.0,
    val modelNames: List<String> = emptyList(),
    val lines: List<CalculatedFoodLine> = emptyList(),
    val v2Record: V2AnalysisRecordEntity? = null,
    val glycemicSimulation: GlycemicSimulationResult? = null,
    val targetPostPrandialMaxMgDl: Double = 140.0,
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    private val loadedEntryFlow = MutableStateFlow<Triple<AnalysisHistoryEntity, List<CalculatedFoodLine>, V2AnalysisRecordEntity?>?>(null)
    private val baselineOverrideFlow = MutableStateFlow<Int?>(null)
    private var _historyId: Long = 0

    init {
        val historyId = checkNotNull(savedStateHandle.get<String>("historyId")).toLong()
        _historyId = historyId
        viewModelScope.launch {
            val entry = historyRepository.getById(historyId) ?: return@launch
            val v2Record = entry.sessionId?.let { telemetryRepository.getBySessionId(it) }
            val lines = runCatching {
                json.decodeFromString<List<CalculatedFoodLine>>(entry.detailsJson)
            }.getOrDefault(emptyList())
            loadedEntryFlow.value = Triple(entry, lines, v2Record)
        }

        combine(
            loadedEntryFlow,
            settingsRepository.settings.map { it.toGlycemicProfile() }.distinctUntilChanged(),
            baselineOverrideFlow,
        ) { loaded, profile, overrideMgDl ->
            val (entry, lines, v2Record) = loaded ?: return@combine null
            val effectiveProfile = if (overrideMgDl != null) {
                profile.copy(baselineGlucoseMgDl = overrideMgDl.toDouble())
            } else {
                profile
            }
            val sim = if (lines.any { !it.isExcluded }) {
                GlycemicCurveCalculator.simulateMeal(lines, effectiveProfile)
            } else {
                GlycemicCurveCalculator.simulateFromTotals(
                    choG = entry.totalChoG,
                    grassiG = entry.totalGrassiG,
                    proteineG = entry.totalProteineG,
                    glycemicLoad = entry.glycemicLoad.takeIf { it > 0.0 },
                    profile = effectiveProfile,
                )
            }
            HistoryDetailUiState(
                foodTitle = entry.foodTitle,
                analyzedAtEpochMillis = entry.analyzedAtEpochMillis,
                sessionMode = entry.sessionMode,
                totalChoG = entry.totalChoG,
                totalProteineG = entry.totalProteineG,
                totalGrassiG = entry.totalGrassiG,
                glycemicLoad = entry.glycemicLoad.takeIf { it > 0.0 } ?: sim.totalGlycemicLoad,
                modelNames = runCatching {
                    json.decodeFromString<List<String>>(entry.modelDisplayNamesJson)
                }.getOrDefault(emptyList()),
                lines = lines,
                v2Record = v2Record,
                glycemicSimulation = sim,
                targetPostPrandialMaxMgDl = effectiveProfile.targetPostPrandialMaxMgDl,
            )
        }
            .onEach { state -> if (state != null) _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun adjustBaselineBy(delta: Int) {
        val currentBaseline = baselineOverrideFlow.value
            ?: _uiState.value.glycemicSimulation?.baselineMgDl?.toInt()
            ?: 90
        baselineOverrideFlow.value = (currentBaseline + delta).coerceIn(65, 180)
    }

    fun deleteEntry(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val entry = historyRepository.getById(_historyId)
            historyRepository.deleteAnalysis(_historyId)
            entry?.sessionId?.let { telemetryRepository.deleteBySessionId(it) }
            onDeleted()
        }
    }
}
