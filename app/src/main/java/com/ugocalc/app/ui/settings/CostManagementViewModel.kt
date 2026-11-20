package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import com.ugocalc.app.data.repository.CostManagementRepository
import com.ugocalc.app.data.repository.CostSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CostManagementUiState(
    val loading: Boolean = true,
    val summary: CostSummary? = null,
    val totalCostUsd: Double = 0.0,
    val monthCostUsd: Double = 0.0,
    val selectedStage: String? = null,
    val selectedModel: String? = null,
)

@HiltViewModel
class CostManagementViewModel @Inject constructor(
    private val costManagementRepository: CostManagementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CostManagementUiState())
    val uiState: StateFlow<CostManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val summary = costManagementRepository.getCostSummary()
                val monthCost = costManagementRepository.getCurrentMonthCost()
                _uiState.update { it.copy(
                    loading = false,
                    summary = summary,
                    totalCostUsd = summary.totalCostUsd,
                    monthCostUsd = monthCost,
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun selectStage(stage: String?) {
        _uiState.update { it.copy(selectedStage = stage) }
    }

    fun selectModel(modelId: String?) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                costManagementRepository.deleteAllTransactions()
                refresh()
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
