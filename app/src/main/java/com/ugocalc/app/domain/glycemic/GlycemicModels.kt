package com.ugocalc.app.domain.glycemic

import com.ugocalc.app.domain.model.CalculatedFoodLine
import kotlinx.serialization.Serializable

/**
 * Classificazione clinica del Carico Glicemico (CG / GL) del pasto (Atkinson et al., 2021):
 * - BASSO: GL <= 10
 * - MEDIO: 10 < GL < 20
 * - ALTO: GL >= 20
 */
@Serializable
enum class GlycemicLoadLevel {
    BASSO,
    MEDIO,
    ALTO,
}

/**
 * Preset di profilo glicemico selezionabile dall'utente nelle Impostazioni.
 * Ogni preset definisce glicemia basale a digiuno, sensibilità ai carboidrati (mg/dL per 10g CHO)
 * e soglia target post-prandiale massima.
 */
@Serializable
enum class GlycemicProfilePreset(
    val label: String,
    val defaultBaselineMgDl: Int,
    val defaultCarbSensitivity: Int,
    val defaultTargetMaxMgDl: Int,
) {
    STANDARD("Standard", 90, 18, 140),
    SENSIBILITA_MODERATA("Sensibilità moderata", 105, 28, 160),
    IMPATTO_PIENO("Diabete / Impatto pieno", 110, 40, 180),
}

/** Parametri fisiologici personalizzabili utilizzati dal simulatore di curva post-prandiale. */
@Serializable
data class GlycemicProfile(
    val preset: GlycemicProfilePreset = GlycemicProfilePreset.STANDARD,
    val baselineGlucoseMgDl: Double = 90.0,
    val carbSensitivityMgDlPer10g: Double = 18.0,
    val targetPostPrandialMaxMgDl: Double = 140.0,
)

/** Indice Glicemico risolto per un alimento, con indicazione se proviene da euristica o dato esplicito. */
@Serializable
data class ResolvedGi(
    val gi: Int,
    val isEstimated: Boolean,
)

/** Contributo glicemico del singolo alimento nel pasto. */
@Serializable
data class FoodGlycemicBreakdown(
    val foodName: String,
    val weightG: Double,
    val choG: Double,
    val gi: Int,
    val isGiEstimated: Boolean,
    val glycemicLoad: Double,
)

/** Singolo punto campionato sulla curva post-prandiale (da 0 a 240 minuti). */
@Serializable
data class GlycemicPoint(
    val minutesFromMeal: Int,
    val glucoseMgDl: Double,
    val choDeltaMgDl: Double,
    val fatProteinDeltaMgDl: Double,
)

/** Output completo della simulazione post-prandiale per un singolo pasto. */
@Serializable
data class GlycemicSimulationResult(
    val totalGlycemicLoad: Double,
    val glycemicLoadLevel: GlycemicLoadLevel,
    val effectiveMealGi: Double,
    val fpuCount: Double,
    val baselineMgDl: Double,
    val peakMgDl: Double,
    val deltaPeakMgDl: Double,
    val peakTimeMinutes: Int,
    val glucoseAt60MinMgDl: Double,
    val glucoseAt120MinMgDl: Double,
    val glucoseAt180MinMgDl: Double,
    val returnToBaselineMinutes: Int?,
    val points: List<GlycemicPoint>,
    val hasEstimatedGiFoods: Boolean,
    val perFoodLoads: List<FoodGlycemicBreakdown>,
    val insightText: String,
)

/** Ingresso di un pasto per la simulazione della timeline glicemica giornaliera multi-pasto. */
data class MealTimelineInput(
    val title: String,
    val analyzedAtEpochMillis: Long,
    val choG: Double,
    val grassiG: Double,
    val proteineG: Double,
    val glycemicLoad: Double,
    val lines: List<CalculatedFoodLine> = emptyList(),
)

/** Punto campionato lungo la giornata (asse temporale assoluto in epochMillis). */
@Serializable
data class DailyTimelinePoint(
    val epochMillis: Long,
    val glucoseMgDl: Double,
)

/** Timeline glicemica stimata sovrapponendo tutti i pasti di una giornata. */
data class DailyGlycemicTimeline(
    val points: List<DailyTimelinePoint>,
    val mealMarkers: List<Pair<Long, String>>,
    val peakMgDl: Double,
    val totalDailyGl: Double,
    val targetMaxMgDl: Double,
)
