package com.ugocalc.app.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionResultsUiState(
    val outcomes: List<ModelVisionOutcome> = emptyList(),
    val referenceModelId: String? = null,
    val isMatching: Boolean = false,
    val matchingDone: Boolean = false,
    val matchingError: String? = null,
    val usingV2Engine: Boolean = false,
    val v2Stime: List<com.ugocalc.app.domain.inference.model.WeightEstimate> = emptyList(),
    val v2AmbiguitaRisolte: List<com.ugocalc.app.domain.inference.model.AmbiguityResolution> = emptyList(),
    val shadowV2Results: Map<String, AnalysisSessionStore.V2ShadowResult> = emptyMap(),
    val confidenceIndicatorEnabled: Boolean = false,
    val confidenceAlertThresholdPercent: Int = ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
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
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisionResultsUiState())
    val uiState: StateFlow<VisionResultsUiState> = _uiState

    init {
        sessionStore.session
            .distinctUntilChanged { old, new ->
                old.visionOutcomes == new.visionOutcomes &&
                    old.referenceModelId == new.referenceModelId &&
                    old.usingV2Engine == new.usingV2Engine &&
                    old.v2Stime == new.v2Stime &&
                    old.v2AmbiguitaRisolte == new.v2AmbiguitaRisolte &&
                    old.shadowV2Results == new.shadowV2Results
            }
            .onEach { s ->
                _uiState.update {
                    it.copy(
                        outcomes = s.visionOutcomes,
                        referenceModelId = s.referenceModelId,
                        usingV2Engine = s.usingV2Engine,
                        v2Stime = s.v2Stime,
                        v2AmbiguitaRisolte = s.v2AmbiguitaRisolte,
                        shadowV2Results = s.shadowV2Results,
                    )
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.settings
            .distinctUntilChanged()
            .onEach { settings ->
                _uiState.update {
                    it.copy(
                        confidenceIndicatorEnabled = settings.confidenceIndicatorEnabled,
                        confidenceAlertThresholdPercent = settings.confidenceAlertThresholdPercent,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectReferenceModel(modelId: String) {
        sessionStore.setReferenceModelId(modelId)
    }

    fun retryAnalysis() {
        sessionStore.resetForRetry()
    }

    fun dismissMatchingError() {
        _uiState.update { it.copy(matchingError = null) }
    }

    /**
     * Tocco su "Calcola CHO e Grassi" (§2.4 punto 5): esegue il Passaggio 3 e calcola i totali.
     * In modalità V2 i totali sono già stati calcolati da M6/M7 dentro la pipeline.
     */
    fun receiveCho() {
        val state = _uiState.value
        if (state.isMatching) return

        if (sessionStore.session.value.usingV2Engine) {
            _uiState.update { it.copy(matchingDone = true, matchingError = null) }
            viewModelScope.launch {
                notificationHelper.notifyAnalysisCompletedIfBackgrounded()
            }
            return
        }

        val referenceOutcome = resolveReferenceOutcome(state) ?: return

        _uiState.update { it.copy(isMatching = true, matchingError = null) }
        val sessionId = sessionStore.session.value.sessionId
        viewModelScope.launch {
            try {
                val lines = matchFoodsAcrossTables(
                    recognizedFoods = referenceOutcome.result.alimenti,
                    referenceModel = referenceOutcome.model,
                    sessionId = sessionId,
                )
                sessionStore.setTotals(AnalysisTotals(lines))
                _uiState.update { it.copy(matchingDone = true) }
                notificationHelper.notifyAnalysisCompletedIfBackgrounded()
                benchmarkRepository.recordV1NutritionCoefficients(sessionId, lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        matchingError = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "Errore durante l'abbinamento alle tabelle nutrizionali.",
                    )
                }
            } finally {
                _uiState.update { it.copy(isMatching = false) }
            }
        }
    }

    /**
     * Salva solo i pesi saltando il matching nutrizionale (§ richiesta utente).
     */
    fun saveWeightsOnly() {
        val state = _uiState.value
        val referenceOutcome = resolveReferenceOutcome(state) ?: return

        val lines = referenceOutcome.result.alimenti.map { food ->
            CalculatedFoodLine(
                recognizedFood = food,
                match = null,
                tableEntry = null,
            )
        }
        sessionStore.setTotals(AnalysisTotals(lines))
        _uiState.update { it.copy(matchingDone = true) }
        viewModelScope.launch {
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        }
    }

    private fun resolveReferenceOutcome(state: VisionResultsUiState): ModelVisionOutcome.Success? {
        val ref = state.referenceModelId
        return state.successfulOutcomes.firstOrNull { it.model.uid == ref }
            ?: state.successfulOutcomes.firstOrNull { it.model.id == ref }
            ?: state.successfulOutcomes.firstOrNull()
    }

    /**
     * Consuma l'evento di navigazione verso i risultati finali (§ bug fix ritorno indietro).
     */
    fun onMatchingDoneConsumed() {
        _uiState.update { it.copy(matchingDone = false) }
    }
}
