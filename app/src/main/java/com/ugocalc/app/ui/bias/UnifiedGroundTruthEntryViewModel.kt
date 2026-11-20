package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Una voce della lista unificata. Può provenire da un record V2 (alimenta i fattori BIAS),
 * da una benchmark_meal (alimenta il confronto V1/V2), oppure da entrambi quando la stessa
 * sessione ha un record V2 linkato alla benchmark_meal corrispondente.
 *
 * Le vecchie misurazioni V2 senza benchmark_meal (create prima che esistesse il benchmark)
 * compaiono con tipo V2_ONLY: utili per BIAS, non per il confronto V1/V2.
 */
sealed interface UnifiedGroundTruthEntry {
    val sessionId: String?
    val capturedAtEpochMillis: Long
    val aiModelId: String
    val nomiAlimenti: List<String>

    data class V2Only(
        val record: V2AnalysisRecordEntity,
        val stime: List<WeightEstimate>,
    ) : UnifiedGroundTruthEntry {
        override val sessionId get() = record.sessionId
        override val capturedAtEpochMillis get() = record.analyzedAtEpochMillis
        override val aiModelId get() = record.aiModelId
        override val nomiAlimenti get() = stime.map { it.nome }
    }

    data class BenchmarkWithOptionalV2(
        val meal: BenchmarkMealEntity,
        val v2Record: V2AnalysisRecordEntity?,
        val stime: List<WeightEstimate>,
        override val nomiAlimenti: List<String>,
    ) : UnifiedGroundTruthEntry {
        override val sessionId get() = meal.sessionId
        override val capturedAtEpochMillis get() = meal.capturedAtEpochMillis
        override val aiModelId get() = meal.aiModelId
        val hasV2 get() = v2Record != null
    }
}

data class UnifiedGroundTruthUiState(
    val loading: Boolean = true,
    val consentGiven: Boolean = false,
    val entries: List<UnifiedGroundTruthEntry> = emptyList(),
    val doneSessionIds: Set<String> = emptySet(),
    val doneBenchmarkIds: Set<String> = emptySet(),
)

@HiltViewModel
class UnifiedGroundTruthEntryViewModel @Inject constructor(
    private val telemetryRepository: V2TelemetryRepository,
    private val benchmarkRepository: BenchmarkRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val physicalDataRepository: PhysicalDataRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedGroundTruthUiState())
    val uiState: StateFlow<UnifiedGroundTruthUiState> = _uiState

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val consent = settingsRepository.settings.first().biasDataConsentGiven

                val v2Pending = telemetryRepository.listPendingGroundTruth()
                val benchmarkPending = benchmarkRepository.listPendingGroundTruth()

            // Benchmark meals che hanno un V2 record linkato: mostriamo una card unificata.
            val benchmarkV2RecordIds = benchmarkPending.mapNotNull { it.v2RecordId }.toSet()
            // V2 records già coperti da una benchmark_meal: da escludere dalla lista V2-only.
            val coveredV2Ids = v2Pending.filter { it.id in benchmarkV2RecordIds }.map { it.id }.toSet()

            val entries = mutableListOf<UnifiedGroundTruthEntry>()

            // Benchmark meals (con o senza V2 collegato)
            for (meal in benchmarkPending) {
                try {
                    val v2Record = meal.v2RecordId?.let { id -> v2Pending.firstOrNull { it.id == id } }
                    val stime = v2Record?.let {
                        try { json.decodeFromString<List<WeightEstimate>>(it.weightEstimatesJson) }
                        catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                    val foodNames = try {
                        benchmarkRepository.foodNamesFor(meal)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    entries += UnifiedGroundTruthEntry.BenchmarkWithOptionalV2(
                        meal = meal,
                        v2Record = v2Record,
                        stime = stime,
                        nomiAlimenti = foodNames,
                    )
                } catch (_: Exception) {
                    // Skip this meal if there's an error
                    continue
                }
            }

            // V2 records non coperti da nessuna benchmark_meal (vecchie analisi pre-benchmark)
            for (record in v2Pending) {
                if (record.id in coveredV2Ids) continue
                try {
                    val stime = try {
                        json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
                    } catch (_: Exception) { emptyList() }
                    entries += UnifiedGroundTruthEntry.V2Only(record = record, stime = stime)
                } catch (_: Exception) {
                    continue
                }
            }

            // Ordine cronologico inverso
            entries.sortByDescending { it.capturedAtEpochMillis }

                _uiState.value = UnifiedGroundTruthUiState(
                    loading = false,
                    consentGiven = consent,
                    entries = entries,
                )
            } catch (e: Exception) {
                _uiState.value = UnifiedGroundTruthUiState(
                    loading = false,
                    consentGiven = false,
                    entries = emptyList(),
                )
            }
        }
    }

    fun submitBenchmark(sessionId: String, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        if (pesiG.isEmpty()) return
        viewModelScope.launch {
            try {
                benchmarkRepository.recordGroundTruth(sessionId, pesiG, livello)
                // Se c'era un V2 record linkato, recordGroundTruth lo propaga già (BenchmarkRepository:139).
                // Ricalcola i fattori BIAS sulle stime V2 se presenti.
                val entry = _uiState.value.entries
                    .filterIsInstance<UnifiedGroundTruthEntry.BenchmarkWithOptionalV2>()
                    .firstOrNull { it.meal.sessionId == sessionId }
                entry?.v2Record?.let { v2 ->
                    try {
                        recalculateBiasFor(v2.aiModelId, entry.stime, pesiG, livello)
                    } catch (_: Exception) {
                        // Continue even if bias recalculation fails
                    }
                }
                removeEntry(sessionId)
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    fun submitV2(recordId: Long, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        if (pesiG.isEmpty()) return
        viewModelScope.launch {
            try {
                val entry = _uiState.value.entries
                    .filterIsInstance<UnifiedGroundTruthEntry.V2Only>()
                    .firstOrNull { it.record.id == recordId } ?: return@launch
                telemetryRepository.recordGroundTruth(recordId, pesiG, livello)
                try {
                    recalculateBiasFor(entry.record.aiModelId, entry.stime, pesiG, livello)
                } catch (_: Exception) {
                    // Continue even if bias recalculation fails
                }
                _uiState.value = _uiState.value.copy(
                    entries = _uiState.value.entries.filterNot {
                        it is UnifiedGroundTruthEntry.V2Only && it.record.id == recordId
                    }
                )
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    fun deleteV2(recordId: Long) {
        viewModelScope.launch {
            telemetryRepository.deleteRecord(recordId)
            _uiState.value = _uiState.value.copy(
                entries = _uiState.value.entries.filterNot {
                    it is UnifiedGroundTruthEntry.V2Only && it.record.id == recordId
                }
            )
        }
    }

    private fun removeEntry(sessionId: String) {
        _uiState.value = _uiState.value.copy(
            entries = _uiState.value.entries.filterNot { it.sessionId == sessionId }
        )
    }

    private suspend fun recalculateBiasFor(
        aiModelId: String,
        stime: List<WeightEstimate>,
        pesiG: Map<String, Double>,
        livello: GroundTruthLevel,
    ) {
        try {
            physicalDataRepository.learnFromGroundTruth(stime, pesiG)
        } catch (_: Exception) {
            // Silently ignore physical learning failure
        }
        try {
            val consent = settingsRepository.settings.first().biasDataConsentGiven
            stime.filter { it.nome in pesiG.keys }.forEach { stima ->
                try {
                    val categoria = FoodCategoryRouter.categorize(stima.nome)
                    biasCorrectionRepository.recalculate(stima.strategia, aiModelId, categoria, consent)
                } catch (_: Exception) {
                    // Skip individual food if recalculation fails
                }
            }
        } catch (_: Exception) {
            // Silently fail if bias recalculation encounters issues
        }
    }
}
