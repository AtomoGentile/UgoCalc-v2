package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.EngineStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BenchmarkDashboardUiState(
    val loading: Boolean = true,
    val mealCount: Int = 0,
    val recommendedMinMeals: Int = 30,
    val recommendedMaxMeals: Int = 50,
    val v1: EngineStats = EngineStats(null, 0, null, null, 0),
    val v2: EngineStats = EngineStats(null, 0, null, null, 0),
    /** Esito della stessa decisione automatica che pilota il motore in produzione (§13 punto 1, §20): qui solo mostrato, non ricalcolato due volte con criteri diversi. */
    val v2Promoted: Boolean = false,
)

/**
 * Dashboard diagnostica del benchmark V1/V2 (piano V2 §10, §12, §13 punto 5): mostra l'errore dei
 * due motori a confronto man mano che i pesi veri si accumulano, e se la V2 ha già superato la V1
 * secondo la stessa soglia usata da [BenchmarkRepository.evaluatePromotion] per scegliere il motore
 * mostrato in produzione — qui è solo trasparenza, la decisione vera avviene altrove.
 */
@HiltViewModel
class BenchmarkDashboardViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BenchmarkDashboardUiState())
    val uiState: StateFlow<BenchmarkDashboardUiState> = _uiState

    init {
        viewModelScope.launch {
            val decision = benchmarkRepository.evaluatePromotion()
            _uiState.value = BenchmarkDashboardUiState(
                loading = false,
                mealCount = decision.mealCount,
                recommendedMinMeals = decision.recommendedMinMeals,
                v1 = decision.v1,
                v2 = decision.v2,
                v2Promoted = decision.promoteV2,
            )
        }
    }
}
