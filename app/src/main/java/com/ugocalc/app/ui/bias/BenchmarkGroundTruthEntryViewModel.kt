package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.data.repository.PhysicalDataRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.m2.FoodCategoryRouter
import com.ugocalc.app.domain.inference.model.WeightEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Un pasto del benchmark in attesa di peso vero, con l'unione dei nomi alimento noti a V1 e V2 (§13 punto 5). */
data class PendingBenchmarkMeal(
    val entity: BenchmarkMealEntity,
    val nomiAlimenti: List<String>,
)

data class BenchmarkGroundTruthEntryUiState(
    val loading: Boolean = true,
    val pending: List<PendingBenchmarkMeal> = emptyList(),
    val savedSessionIds: Set<String> = emptySet(),
)

/**
 * Registrazione del peso vero per un pasto tracciato dal benchmark V1/V2 (§10, §13 punto 5): a
 * differenza di [GroundTruthEntryViewModel] (che alimenta solo la calibrazione interna di V2), qui
 * il peso vero serve a confrontare l'errore dei due motori sullo stesso pasto nella dashboard.
 */
@HiltViewModel
class BenchmarkGroundTruthEntryViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val physicalDataRepository: PhysicalDataRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BenchmarkGroundTruthEntryUiState())
    val uiState: StateFlow<BenchmarkGroundTruthEntryUiState> = _uiState

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val pending = benchmarkRepository.listPendingGroundTruth().map { entity ->
                PendingBenchmarkMeal(entity = entity, nomiAlimenti = benchmarkRepository.foodNamesFor(entity))
            }
            _uiState.value = _uiState.value.copy(loading = false, pending = pending)
        }
    }

    fun submit(sessionId: String, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        viewModelScope.launch {
            if (pesiG.isEmpty()) return@launch
            val pendingMeal = _uiState.value.pending.firstOrNull { it.entity.sessionId == sessionId }?.entity
            benchmarkRepository.recordGroundTruth(sessionId, pesiG, livello)

            // Propaga anche su costanti fisiche M3 e ricalcolo bias M8 per il record V2 collegato.
            try {
                val v2Record = pendingMeal?.v2RecordId?.let { telemetryRepository.getById(it) }
                    ?: telemetryRepository.getBySessionId(sessionId)
                if (v2Record != null && v2Record.failureReason == null) {
                    val stime = runCatching {
                        json.decodeFromString<List<WeightEstimate>>(v2Record.weightEstimatesJson)
                    }.getOrDefault(emptyList())
                    if (stime.isNotEmpty()) {
                        physicalDataRepository.learnFromGroundTruth(stime, pesiG)
                        val consent = settingsRepository.settings.first().biasDataConsentGiven
                        stime.filter { it.nome in pesiG.keys }.forEach { stima ->
                            val categoria = FoodCategoryRouter.categorize(stima.nome)
                            biasCorrectionRepository.recalculate(stima.strategia, v2Record.aiModelId, categoria, consent)
                        }
                    }
                }
            } catch (_: Exception) {
                // Non blocca il salvataggio del benchmark
            }

            _uiState.value = _uiState.value.copy(
                savedSessionIds = _uiState.value.savedSessionIds + sessionId,
                pending = _uiState.value.pending.filterNot { it.entity.sessionId == sessionId },
            )
        }
    }
}
