package com.ugocalc.app.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionResultsUiState(
    val outcomes: List<ModelVisionOutcome> = emptyList(),
    val referenceModelId: String? = null,
    val isMatching: Boolean = false,
    val matchingDone: Boolean = false,
    val usingV2Engine: Boolean = false,
    val v2AmbiguitaRisolte: List<com.ugocalc.app.domain.inference.model.AmbiguityResolution> = emptyList(),
    val shadowV2Results: Map<String, AnalysisSessionStore.V2ShadowResult> = emptyMap(),
) {
    val allFailed: Boolean get() = outcomes.isNotEmpty() && outcomes.all { it is ModelVisionOutcome.Failure }
    val successfulOutcomes: List<ModelVisionOutcome.Success> get() = outcomes.filterIsInstance<ModelVisionOutcome.Success>()
}

@HiltViewModel
class VisionResultsViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
    private val matchFoodsAcrossTables: MatchFoodsAcrossTablesUseCase,
    private val notificationHelper: AnalysisNotificationHelper,
    private val benchmarkRepository: BenchmarkRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisionResultsUiState())
    val uiState: StateFlow<VisionResultsUiState> = _uiState

    init {
        sessionStore.session
            .map { s -> listOf(s.visionOutcomes, s.referenceModelId, s.usingV2Engine, s.v2AmbiguitaRisolte, s.shadowV2Results) }
            .distinctUntilChanged()
            .onEach { (outcomes, referenceModelId, usingV2Engine, v2Ambiguita, shadowResults) ->
                @Suppress("UNCHECKED_CAST")
                _uiState.value = _uiState.value.copy(
                    outcomes = outcomes as List<ModelVisionOutcome>,
                    referenceModelId = referenceModelId as String?,
                    usingV2Engine = usingV2Engine as Boolean,
                    v2AmbiguitaRisolte = v2Ambiguita as List<com.ugocalc.app.domain.inference.model.AmbiguityResolution>,
                    shadowV2Results = shadowResults as Map<String, AnalysisSessionStore.V2ShadowResult>,
                )
            }.launchIn(viewModelScope)
    }

    fun selectReferenceModel(modelId: String) {
        sessionStore.setReferenceModelId(modelId)
    }

    fun retryAnalysis() {
        sessionStore.resetForRetry()
    }

    /**
     * Tocco su "Ricevi i CHO" (§2.4 punto 5): esegue il Passaggio 3 e calcola i totali. Se il
     * risultato mostrato viene dalla pipeline V2 (piano V2 §13 punto 1), M6/M7 hanno già calcolato
     * i totali dentro la pipeline — qui non si rifà l'abbinamento, si passa solo allo stato
     * "fatto" perché [sessionStore]'s totals sono già quelli giusti.
     */
    fun receiveCho() {
        val state = _uiState.value
        if (sessionStore.session.value.usingV2Engine) {
            _uiState.value = state.copy(matchingDone = true)
            viewModelScope.launch {
                notificationHelper.notifyAnalysisCompletedIfBackgrounded()
            }
            return
        }
        val referenceOutcome = state.successfulOutcomes.firstOrNull { it.model.id == state.referenceModelId }
            ?: state.successfulOutcomes.firstOrNull()
            ?: return
        _uiState.value = state.copy(isMatching = true)
        viewModelScope.launch {
            val lines = matchFoodsAcrossTables(referenceOutcome.result.alimenti, referenceOutcome.model)
            sessionStore.setTotals(AnalysisTotals(lines))
            _uiState.value = _uiState.value.copy(isMatching = false, matchingDone = true)
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
            // §13 punto 5 (benchmark V1/V2): salva i coefficienti CHO/100g già trovati da V1, così la
            // dashboard di confronto può calcolare il CHO "vero" senza richiamare l'AI di matching.
            benchmarkRepository.recordV1NutritionCoefficients(sessionStore.session.value.sessionId, lines)
        }
    }

    /**
     * Salva solo i pesi saltando il matching nutrizionale (§ richiesta utente): crea dei totali
     * fittizi dove CHO e grassi sono zero (perché le righe non hanno abbinamento), ma il peso
     * stimato resta conservato. Permette di arrivare alla schermata finale e salvare il pasto
     * anche se non si ha tempo/voglia di aspettare il matching o di correggerlo.
     */
    fun saveWeightsOnly() {
        val state = _uiState.value
        val referenceOutcome = state.successfulOutcomes.firstOrNull { it.model.id == state.referenceModelId }
            ?: state.successfulOutcomes.firstOrNull()
            ?: return

        val lines = referenceOutcome.result.alimenti.map { food ->
            CalculatedFoodLine(
                recognizedFood = food,
                match = null,
                tableEntry = null,
            )
        }
        sessionStore.setTotals(AnalysisTotals(lines))
        _uiState.value = state.copy(matchingDone = true)
        viewModelScope.launch {
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        }
    }

    /**
     * Da chiamare quando l'evento di navigazione verso i risultati finali è stato consumato dalla
     * UI (§ bug fix): evita che tornando indietro da FinalResultScreen si venga immediatamente
     * rimbalzati di nuovo in avanti perché matchingDone è ancora true.
     */
    fun onMatchingDoneConsumed() {
        _uiState.value = _uiState.value.copy(matchingDone = false)
    }
}
