package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BenchmarkMeal
import com.ugocalc.app.data.repository.BenchmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** Metriche aggregate per un motore (V1 o V2), calcolate sui pasti con peso vero registrato (§12: metriche già definite, non nuove). */
data class EngineStats(
    val weightMaeG: Double?,
    val weightSampleCount: Int,
    val choWithin10gPercent: Double?,
    val choWithin20gPercent: Double?,
    val choMealCount: Int,
)

data class BenchmarkDashboardUiState(
    val loading: Boolean = true,
    val mealCount: Int = 0,
    val recommendedMinMeals: Int = 30,
    val recommendedMaxMeals: Int = 50,
    val v1: EngineStats = EngineStats(null, 0, null, null, 0),
    val v2: EngineStats = EngineStats(null, 0, null, null, 0),
)

/**
 * Dashboard diagnostica del benchmark V1/V2 (piano V2 §10, §12, §13 punto 5): mostra l'errore dei
 * due motori a confronto man mano che i pesi veri si accumulano. Non decide mai quale motore usare
 * in produzione — resta una diagnostica incrementale, la decisione finale è dell'owner (§13).
 */
@HiltViewModel
class BenchmarkDashboardViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BenchmarkDashboardUiState())
    val uiState: StateFlow<BenchmarkDashboardUiState> = _uiState

    init {
        viewModelScope.launch {
            val meals = benchmarkRepository.listMealsWithGroundTruth()
            _uiState.value = BenchmarkDashboardUiState(
                loading = false,
                mealCount = meals.size,
                v1 = computeV1Stats(meals),
                v2 = computeV2Stats(meals),
            )
        }
    }

    private fun computeV1Stats(meals: List<BenchmarkMeal>): EngineStats {
        val weightErrors = mutableListOf<Double>()
        var within10 = 0
        var within20 = 0
        var mealsWithCho = 0
        for (meal in meals) {
            for ((nome, veroG) in meal.groundTruthPesi) {
                meal.v1PesiPerAlimento[nome]?.let { stimaG -> weightErrors += abs(stimaG - veroG) }
            }
            val choVero = meal.groundTruthPesi.entries.sumOf { (nome, veroG) ->
                (meal.v1NutrizionePerAlimento[nome]?.choPer100g ?: return@sumOf 0.0) * veroG / 100.0
            }
            val choStimato = meal.v1PesiPerAlimento.entries.sumOf { (nome, stimaG) ->
                (meal.v1NutrizionePerAlimento[nome]?.choPer100g ?: return@sumOf 0.0) * stimaG / 100.0
            }
            if (meal.v1NutrizionePerAlimento.isEmpty()) continue
            mealsWithCho++
            val errore = abs(choStimato - choVero)
            if (errore <= 10.0) within10++
            if (errore <= 20.0) within20++
        }
        return EngineStats(
            weightMaeG = weightErrors.takeIf { it.isNotEmpty() }?.average(),
            weightSampleCount = weightErrors.size,
            choWithin10gPercent = mealsWithCho.takeIf { it > 0 }?.let { within10 * 100.0 / it },
            choWithin20gPercent = mealsWithCho.takeIf { it > 0 }?.let { within20 * 100.0 / it },
            choMealCount = mealsWithCho,
        )
    }

    private fun computeV2Stats(meals: List<BenchmarkMeal>): EngineStats {
        val weightErrors = mutableListOf<Double>()
        var within10 = 0
        var within20 = 0
        var mealsWithCho = 0
        for (meal in meals) {
            val stimePerNome = meal.v2Stime.associate { it.nome to it.pesoG }
            for ((nome, veroG) in meal.groundTruthPesi) {
                stimePerNome[nome]?.let { stimaG -> weightErrors += abs(stimaG - veroG) }
            }
            val righe = meal.v2Nutrizione?.righe.orEmpty().filterNot { it.escluso }
            if (righe.isEmpty()) continue
            // Coefficiente CHO/100g ricavato dalla riga già calcolata da V2 (choG / pesoG * 100): evita
            // di dover ripetere il matching per calcolare il CHO "vero" sul peso pesato dall'utente.
            val coeffPerNome = righe.mapNotNull { riga ->
                if (riga.pesoG <= 0.0 || riga.choG == null) null else riga.nome to riga.choG / riga.pesoG * 100.0
            }.toMap()
            if (coeffPerNome.isEmpty()) continue
            val choVero = meal.groundTruthPesi.entries.sumOf { (nome, veroG) -> (coeffPerNome[nome] ?: 0.0) * veroG / 100.0 }
            val choStimato = righe.sumOf { it.choG ?: 0.0 }
            mealsWithCho++
            val errore = abs(choStimato - choVero)
            if (errore <= 10.0) within10++
            if (errore <= 20.0) within20++
        }
        return EngineStats(
            weightMaeG = weightErrors.takeIf { it.isNotEmpty() }?.average(),
            weightSampleCount = weightErrors.size,
            choWithin10gPercent = mealsWithCho.takeIf { it > 0 }?.let { within10 * 100.0 / it },
            choWithin20gPercent = mealsWithCho.takeIf { it > 0 }?.let { within20 * 100.0 / it },
            choMealCount = mealsWithCho,
        )
    }
}
