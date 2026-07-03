package com.ugocalc.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.domain.model.CalculatedFoodLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HistoryDetailUiState(
    val foodTitle: String = "",
    val totalChoG: Double = 0.0,
    val totalGrassiG: Double = 0.0,
    val modelNames: List<String> = emptyList(),
    val lines: List<CalculatedFoodLine> = emptyList(),
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState

    init {
        val historyId = checkNotNull(savedStateHandle.get<String>("historyId")).toLong()
        viewModelScope.launch {
            val entry = historyRepository.getById(historyId) ?: return@launch
            _uiState.value = HistoryDetailUiState(
                foodTitle = entry.foodTitle,
                totalChoG = entry.totalChoG,
                totalGrassiG = entry.totalGrassiG,
                modelNames = json.decodeFromString(entry.modelDisplayNamesJson),
                lines = json.decodeFromString(entry.detailsJson),
            )
        }
    }
}
