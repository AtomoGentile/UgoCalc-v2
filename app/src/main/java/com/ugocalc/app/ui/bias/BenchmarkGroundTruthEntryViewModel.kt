package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.GroundTruthLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
            benchmarkRepository.recordGroundTruth(sessionId, pesiG, livello)
            _uiState.value = _uiState.value.copy(
                savedSessionIds = _uiState.value.savedSessionIds + sessionId,
                pending = _uiState.value.pending.filterNot { it.entity.sessionId == sessionId },
            )
        }
    }
}
