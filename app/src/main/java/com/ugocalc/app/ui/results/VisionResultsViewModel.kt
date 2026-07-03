package com.ugocalc.app.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionResultsUiState(
    val outcomes: List<ModelVisionOutcome> = emptyList(),
    val referenceModelId: String? = null,
    val isMatching: Boolean = false,
    val matchingDone: Boolean = false,
) {
    val allFailed: Boolean get() = outcomes.isNotEmpty() && outcomes.all { it is ModelVisionOutcome.Failure }
    val successfulOutcomes: List<ModelVisionOutcome.Success> get() = outcomes.filterIsInstance<ModelVisionOutcome.Success>()
}

@HiltViewModel
class VisionResultsViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
    private val matchFoodsAcrossTables: MatchFoodsAcrossTablesUseCase,
    private val notificationHelper: AnalysisNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisionResultsUiState())
    val uiState: StateFlow<VisionResultsUiState> = _uiState

    init {
        sessionStore.session.onEach { session ->
            _uiState.value = _uiState.value.copy(
                outcomes = session.visionOutcomes,
                referenceModelId = session.referenceModelId,
            )
        }.launchIn(viewModelScope)
    }

    fun selectReferenceModel(modelId: String) {
        sessionStore.setReferenceModelId(modelId)
    }

    fun retryAnalysis() {
        sessionStore.resetForRetry()
    }

    /** Tocco su "Ricevi i CHO" (§2.4 punto 5): esegue il Passaggio 3 e calcola i totali. */
    fun receiveCho() {
        val state = _uiState.value
        val referenceOutcome = state.successfulOutcomes.firstOrNull { it.model.id == state.referenceModelId }
            ?: state.successfulOutcomes.firstOrNull()
            ?: return
        _uiState.value = state.copy(isMatching = true)
        viewModelScope.launch {
            val lines = matchFoodsAcrossTables(referenceOutcome.result.alimenti, referenceOutcome.model)
            sessionStore.setTotals(com.ugocalc.app.domain.model.AnalysisTotals(lines))
            _uiState.value = _uiState.value.copy(isMatching = false, matchingDone = true)
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        }
    }
}
