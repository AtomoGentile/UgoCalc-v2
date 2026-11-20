package com.ugocalc.app.ui.bias

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.PhysicalDataRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.model.AmbiguityResolution
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.VerificationReport
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class V2ResultUiState(
    val reasoning: String = "",
    val v2Stime: List<WeightEstimate> = emptyList(),
    val ambiguities: List<AmbiguityResolution> = emptyList(),
)

@HiltViewModel
class V2ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionStore: AnalysisSessionStore,
    private val telemetryRepository: V2TelemetryRepository,
    private val physicalDataRepository: PhysicalDataRepository,
    private val json: Json,
) : ViewModel() {
    private val _uiState = MutableStateFlow(V2ResultUiState())
    val uiState: StateFlow<V2ResultUiState> = _uiState.asStateFlow()

    private var _recordId: Long? = null

    init {
        val recordIdStr = savedStateHandle.get<String>("recordId")
        val recordId = if (recordIdStr == "null") null else recordIdStr?.toLongOrNull()
        _recordId = recordId
        if (recordId != null) {
            viewModelScope.launch {
                val record = telemetryRepository.getById(recordId) ?: return@launch
                val stime = json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
                val sceneGraph = json.decodeFromString<SceneGraph>(record.sceneGraphJson)
                val verification = json.decodeFromString<VerificationReport>(record.verificationReportJson)
                _uiState.update { it.copy(
                    reasoning = sceneGraph.ragionamento,
                    v2Stime = stime,
                    ambiguities = verification.ambiguitaRisolta
                ) }
            }
        } else {
            viewModelScope.launch {
                sessionStore.session.collect { session ->
                    // In shadow mode, i risultati V2 non sono quelli primari della sessione.
                    // Se v2Stime è vuoto, proviamo a recuperare il primo risultato shadow disponibile (piano V2 §13.1).
                    val stime = if (session.usingV2Engine) session.v2Stime else {
                        session.shadowV2Results.values.firstOrNull()?.let { shadow ->
                            (shadow.outcome as? AnalysisSessionStore.PipelineOutcomeForShadow.Success)?.stime
                        } ?: emptyList()
                    }
                    val ambiguita = if (session.usingV2Engine) session.v2AmbiguitaRisolte else {
                        session.shadowV2Results.values.firstOrNull()?.let { shadow ->
                            (shadow.outcome as? AnalysisSessionStore.PipelineOutcomeForShadow.Success)?.ambiguita
                        } ?: emptyList()
                    }
                    val reasoning = if (session.usingV2Engine) "" else {
                        session.shadowV2Results.values.firstOrNull()?.let { shadow ->
                            (shadow.outcome as? AnalysisSessionStore.PipelineOutcomeForShadow.Success)?.visionOutcome?.result?.ragionamento
                        } ?: ""
                    }

                    _uiState.update { it.copy(
                        reasoning = reasoning,
                        v2Stime = stime,
                        ambiguities = ambiguita
                    ) }
                }
            }
        }
    }

    fun updateDensity(foodName: String, density: Double) {
        viewModelScope.launch {
            physicalDataRepository.addDensity(foodName, density)
        }
    }

    fun deleteRecord(onDeleted: () -> Unit) {
        val recordId = _recordId ?: return
        viewModelScope.launch {
            telemetryRepository.deleteRecord(recordId)
            onDeleted()
        }
    }
}
