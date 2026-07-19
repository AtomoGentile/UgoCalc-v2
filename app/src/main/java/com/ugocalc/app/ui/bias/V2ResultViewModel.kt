package com.ugocalc.app.ui.bias

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.model.AmbiguityResolution
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
    private val json: Json,
) : ViewModel() {
    private val _uiState = MutableStateFlow(V2ResultUiState())
    val uiState: StateFlow<V2ResultUiState> = _uiState.asStateFlow()

    init {
        val recordId = savedStateHandle.get<String>("recordId")?.toLongOrNull()
        if (recordId != null) {
            viewModelScope.launch {
                val record = telemetryRepository.getById(recordId) ?: return@launch
                val stime = json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
                val sceneGraph = json.decodeFromString<com.ugocalc.app.domain.inference.model.SceneGraph>(record.sceneGraphJson)
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
                    _uiState.update { it.copy(
                        v2Stime = session.v2Stime,
                        ambiguities = session.v2AmbiguitaRisolte
                    ) }
                }
            }
        }
    }
}
