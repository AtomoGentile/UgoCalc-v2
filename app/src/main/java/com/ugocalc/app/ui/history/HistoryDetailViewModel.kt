package com.ugocalc.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.model.CalculatedFoodLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HistoryDetailUiState(
    val foodTitle: String = "",
    val analyzedAtEpochMillis: Long? = null,
    val sessionMode: String = "",
    val totalChoG: Double = 0.0,
    val totalGrassiG: Double = 0.0,
    val modelNames: List<String> = emptyList(),
    val lines: List<CalculatedFoodLine> = emptyList(),
    val v2Record: V2AnalysisRecordEntity? = null,
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    private var _historyId: Long = 0

    init {
        val historyId = checkNotNull(savedStateHandle.get<String>("historyId")).toLong()
        _historyId = historyId
        viewModelScope.launch {
            val entry = historyRepository.getById(historyId) ?: return@launch
            val v2Record = entry.sessionId?.let { telemetryRepository.getBySessionId(it) }
            _uiState.value = HistoryDetailUiState(
                foodTitle = entry.foodTitle,
                analyzedAtEpochMillis = entry.analyzedAtEpochMillis,
                sessionMode = entry.sessionMode,
                totalChoG = entry.totalChoG,
                totalGrassiG = entry.totalGrassiG,
                modelNames = runCatching { json.decodeFromString<List<String>>(entry.modelDisplayNamesJson) }.getOrDefault(emptyList()),
                lines = runCatching { json.decodeFromString<List<CalculatedFoodLine>>(entry.detailsJson) }.getOrDefault(emptyList()),
                v2Record = v2Record,
            )
        }
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
