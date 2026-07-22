package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.m2.FoodCategoryRouter
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Un'analisi V2 ancora senza ground truth, con le stime già decodificate per mostrare un campo per alimento. */
data class PendingGroundTruthRecord(
    val record: V2AnalysisRecordEntity,
    val stime: List<WeightEstimate>,
)

data class GroundTruthEntryUiState(
    val loading: Boolean = true,
    val consentGiven: Boolean = false,
    val pending: List<PendingGroundTruthRecord> = emptyList(),
    val savedRecordIds: Set<Long> = emptySet(),
)

/**
 * Registrazione manuale del peso vero per un'analisi V2 già salvata (piano V2 §7, §8, §13 punto 3):
 * è il pezzo che mancava per far entrare ground truth reale nel ciclo di correzione — senza questa
 * schermata, `recordGroundTruth()` restava raggiungibile solo dai test.
 */
@HiltViewModel
class GroundTruthEntryViewModel @Inject constructor(
    private val telemetryRepository: V2TelemetryRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroundTruthEntryUiState())
    val uiState: StateFlow<GroundTruthEntryUiState> = _uiState

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            val pending = telemetryRepository.listPendingGroundTruth().map { record ->
                PendingGroundTruthRecord(
                    record = record,
                    stime = json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson),
                )
            }
            _uiState.value = _uiState.value.copy(loading = false, consentGiven = consentGiven, pending = pending)
        }
    }

    /** Salva il peso vero per gli alimenti valorizzati e ricalcola subito i fattori del segmento coinvolto (§8). */
    fun submit(recordId: Long, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        viewModelScope.launch {
            val entry = _uiState.value.pending.firstOrNull { it.record.id == recordId } ?: return@launch
            if (pesiG.isEmpty()) return@launch

            telemetryRepository.recordGroundTruth(recordId, pesiG, livello)

            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            val sessionMode = SessionMode.entries.firstOrNull { it.name == entry.record.sessionMode }
            if (sessionMode != null) {
                entry.stime.filter { it.nome in pesiG.keys }.forEach { stima ->
                    val categoria = FoodCategoryRouter.categorize(stima.nome)
                    biasCorrectionRepository.recalculate(sessionMode, stima.strategia, entry.record.aiModelId, categoria, consentGiven)
                }
            }

            _uiState.value = _uiState.value.copy(
                savedRecordIds = _uiState.value.savedRecordIds + recordId,
                pending = _uiState.value.pending.filterNot { it.record.id == recordId },
            )
        }
    }

    /** Elimina un'analisi se giudicata "sbagliata" (es. test, inquadratura pessima, alimenti non riconosciuti) per non inquinare i futuri fattori di correzione (§ richiesta utente). */
    fun delete(recordId: Long) {
        viewModelScope.launch {
            telemetryRepository.deleteRecord(recordId)
            _uiState.value = _uiState.value.copy(
                pending = _uiState.value.pending.filterNot { it.record.id == recordId }
            )
        }
    }
}
