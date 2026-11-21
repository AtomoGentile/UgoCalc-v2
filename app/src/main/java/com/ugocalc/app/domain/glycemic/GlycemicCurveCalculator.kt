package com.ugocalc.app.domain.glycemic

import com.ugocalc.app.domain.model.CalculatedFoodLine
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Motore deterministico pure-Kotlin per il calcolo del Carico Glicemico (GL), delle Fat-Protein
 * Units (FPU) e della curva glicemica post-prandiale stimata (0..240 minuti).
 *
 * Modello fisiologico (informativo/educativo, non dispositivo medico):
 * - Assorbimento carboidrati (onda gamma-like di ordine 2) modulato dall'IG medio ponderato del
 *   pasto e rallentato dallo svuotamento gastrico indotto da grassi e proteine (`deltaDelay`, `blunt`).
 * - Onda secondaria ritardata Fat-Protein (ordine 3 centrata a ~175 min, effetto "pizza/pasto ricco")
 *   attiva quando `fpu >= 0.5`.
 */
object GlycemicCurveCalculator {

    private const val CURVE_DURATION_MIN = 240
    private const val CURVE_STEP_MIN = 10
    private const val TIMELINE_STEP_MIN = 15
    private const val TIMELINE_PRE_MEAL_MIN = 15
    private const val TIMELINE_POST_MEAL_MIN = 240
    private const val TIMELINE_MEAL_EFFECT_CUTOFF_MIN = 360.0
    private const val T_FP_MINUTES = 175.0
    private const val RETURN_TO_BASELINE_TOLERANCE_MG_DL = 10.0

    private data class WaveParams(
        val tPeak: Double,
        val choAmp: Double,
        val tFp: Double,
        val fpAmp: Double,
    ) {
        fun choDeltaAt(tMin: Double): Double {
            if (tMin <= 0.0 || choAmp <= 0.0 || tPeak <= 0.0) return 0.0
            val r = tMin / tPeak
            return choAmp * r * r * exp(2.0 * (1.0 - r))
        }

        fun fpDeltaAt(tMin: Double): Double {
            if (tMin <= 0.0 || fpAmp <= 0.0 || tFp <= 0.0) return 0.0
            val r = tMin / tFp
            return fpAmp * r * r * r * exp(3.0 * (1.0 - r))
        }
    }

    /** Calcola il Carico Glicemico totale del pasto sommando `IG_i * CHO_i / 100` sulle righe incluse. */
    fun computeTotalGlycemicLoad(lines: List<CalculatedFoodLine>): Double {
        return lines.asSequence()
            .filter { !it.isExcluded }
            .sumOf { line ->
                val resolved = GlycemicIndexResolver.resolve(line.tableEntry!!)
                resolved.gi * line.choContributionG / 100.0
            }
    }

    /** Classifica il Carico Glicemico nelle fasce cliniche standard (BASSO <= 10, MEDIO < 20, ALTO >= 20). */
    fun classifyGlycemicLoad(gl: Double): GlycemicLoadLevel = when {
        gl <= 10.0 -> GlycemicLoadLevel.BASSO
        gl < 20.0 -> GlycemicLoadLevel.MEDIO
        else -> GlycemicLoadLevel.ALTO
    }

    /** Simula la risposta post-prandiale completa a partire dalle singole righe calcolate del pasto. */
    fun simulateMeal(
        lines: List<CalculatedFoodLine>,
        profile: GlycemicProfile = GlycemicProfile(),
    ): GlycemicSimulationResult {
        val included = lines.filter { !it.isExcluded }
        val breakdowns = included.map { line ->
            val entry = line.tableEntry!!
            val resolved = GlycemicIndexResolver.resolve(entry)
            val choG = line.choContributionG.coerceAtLeast(0.0)
            val gl = resolved.gi * choG / 100.0
            FoodGlycemicBreakdown(
                foodName = entry.nome,
                weightG = line.recognizedFood.pesoStimatoG.coerceAtLeast(0.0),
                choG = choG,
                gi = resolved.gi,
                isGiEstimated = resolved.isEstimated,
                glycemicLoad = gl,
            )
        }

        val totalChoG = included.sumOf { it.choContributionG.coerceAtLeast(0.0) }
        val totalGrassiG = included.sumOf { it.grassiContributionG.coerceAtLeast(0.0) }
        val totalProteineG = included.sumOf { it.proteineContributionG.coerceAtLeast(0.0) }
        val totalGl = breakdowns.sumOf { it.glycemicLoad }
        val effectiveMealGi = if (totalChoG > 0.0) {
            (breakdowns.sumOf { it.gi * it.choG } / totalChoG).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
        val hasEstimatedGi = breakdowns.any { it.isGiEstimated && it.choG > 0.0 }

        return runSimulation(
            totalChoG = totalChoG,
            totalGrassiG = totalGrassiG,
            totalProteineG = totalProteineG,
            effectiveGl = totalGl,
            effectiveMealGi = effectiveMealGi,
            profile = profile,
            hasEstimatedGiFoods = hasEstimatedGi,
            perFoodLoads = breakdowns,
        )
    }

    /**
     * Simula la risposta post-prandiale a partire dai soli totali aggregati del pasto
     * (utile per voci storiche legacy senza dettaglio o con `glycemicLoad == 0` da migrazione).
     */
    fun simulateFromTotals(
        choG: Double,
        grassiG: Double,
        proteineG: Double,
        glycemicLoad: Double? = null,
        effectiveGi: Double = 55.0,
        profile: GlycemicProfile = GlycemicProfile(),
    ): GlycemicSimulationResult {
        val safeCho = choG.coerceAtLeast(0.0)
        val safeGrassi = grassiG.coerceAtLeast(0.0)
        val safeProteine = proteineG.coerceAtLeast(0.0)

        val resolvedGl: Double
        val resolvedGi: Double
        val hasEstimated: Boolean

        if (safeCho <= 0.0) {
            resolvedGl = 0.0
            resolvedGi = 0.0
            hasEstimated = false
        } else if (glycemicLoad != null && glycemicLoad > 0.0) {
            resolvedGl = glycemicLoad
            resolvedGi = ((glycemicLoad * 100.0) / safeCho).coerceIn(15.0, 100.0)
            hasEstimated = false
        } else {
            resolvedGi = effectiveGi.coerceIn(0.0, 100.0)
            resolvedGl = resolvedGi * safeCho / 100.0
            hasEstimated = true
        }

        return runSimulation(
            totalChoG = safeCho,
            totalGrassiG = safeGrassi,
            totalProteineG = safeProteine,
            effectiveGl = resolvedGl,
            effectiveMealGi = resolvedGi,
            profile = profile,
            hasEstimatedGiFoods = hasEstimated,
            perFoodLoads = emptyList(),
        )
    }

    /**
     * Sovrappone le curve post-prandiali di più pasti lungo la giornata (step 15 min,
     * da 15 minuti prima del primo pasto a 240 minuti dopo l'ultimo pasto).
     * Restituisce `null` se la lista [meals] è vuota.
     */
    fun simulateDayTimeline(
        meals: List<MealTimelineInput>,
        profile: GlycemicProfile = GlycemicProfile(),
    ): DailyGlycemicTimeline? {
        if (meals.isEmpty()) return null

        val sortedMeals = meals.sortedBy { it.analyzedAtEpochMillis }

        data class ScheduledMealWave(
            val analyzedAtEpochMillis: Long,
            val title: String,
            val totalGl: Double,
            val wave: WaveParams,
        )

        val scheduledWaves = sortedMeals.map { meal ->
            val sim = if (meal.lines.isNotEmpty()) {
                simulateMeal(meal.lines, profile)
            } else {
                simulateFromTotals(
                    choG = meal.choG,
                    grassiG = meal.grassiG,
                    proteineG = meal.proteineG,
                    glycemicLoad = meal.glycemicLoad.takeIf { it > 0.0 },
                    profile = profile,
                )
            }
            val safeGrassi = if (meal.lines.isNotEmpty()) {
                meal.lines.filter { !it.isExcluded }.sumOf { it.grassiContributionG.coerceAtLeast(0.0) }
            } else {
                meal.grassiG.coerceAtLeast(0.0)
            }
            val safeProteine = if (meal.lines.isNotEmpty()) {
                meal.lines.filter { !it.isExcluded }.sumOf { it.proteineContributionG.coerceAtLeast(0.0) }
            } else {
                meal.proteineG.coerceAtLeast(0.0)
            }
            val wave = computeWaveParams(
                grassiG = safeGrassi,
                proteineG = safeProteine,
                effectiveGl = sim.totalGlycemicLoad,
                effectiveMealGi = sim.effectiveMealGi,
                fpuCount = sim.fpuCount,
                profile = profile,
            )
            ScheduledMealWave(
                analyzedAtEpochMillis = meal.analyzedAtEpochMillis,
                title = meal.title,
                totalGl = sim.totalGlycemicLoad,
                wave = wave,
            )
        }

        val startMillis = sortedMeals.first().analyzedAtEpochMillis - TIMELINE_PRE_MEAL_MIN * 60_000L
        val endMillis = sortedMeals.last().analyzedAtEpochMillis + TIMELINE_POST_MEAL_MIN * 60_000L
        val stepMillis = TIMELINE_STEP_MIN * 60_000L

        val timelinePoints = mutableListOf<DailyTimelinePoint>()
        var currentMillis = startMillis
        while (currentMillis <= endMillis) {
            var deltaSum = 0.0
            for (scheduled in scheduledWaves) {
                val dtMin = (currentMillis - scheduled.analyzedAtEpochMillis) / 60_000.0
                if (dtMin > 0.0 && dtMin <= TIMELINE_MEAL_EFFECT_CUTOFF_MIN) {
                    deltaSum += scheduled.wave.choDeltaAt(dtMin) + scheduled.wave.fpDeltaAt(dtMin)
                }
            }
            timelinePoints += DailyTimelinePoint(
                epochMillis = currentMillis,
                glucoseMgDl = profile.baselineGlucoseMgDl + deltaSum,
            )
            currentMillis += stepMillis
        }

        if (timelinePoints.last().epochMillis < endMillis) {
            var deltaSum = 0.0
            for (scheduled in scheduledWaves) {
                val dtMin = (endMillis - scheduled.analyzedAtEpochMillis) / 60_000.0
                if (dtMin > 0.0 && dtMin <= TIMELINE_MEAL_EFFECT_CUTOFF_MIN) {
                    deltaSum += scheduled.wave.choDeltaAt(dtMin) + scheduled.wave.fpDeltaAt(dtMin)
                }
            }
            timelinePoints += DailyTimelinePoint(
                epochMillis = endMillis,
                glucoseMgDl = profile.baselineGlucoseMgDl + deltaSum,
            )
        }

        val peakMgDl = timelinePoints.maxOf { it.glucoseMgDl }
        val totalDailyGl = scheduledWaves.sumOf { it.totalGl }
        val mealMarkers = scheduledWaves.map { it.analyzedAtEpochMillis to it.title }

        return DailyGlycemicTimeline(
            points = timelinePoints,
            mealMarkers = mealMarkers,
            peakMgDl = peakMgDl,
            totalDailyGl = totalDailyGl,
            targetMaxMgDl = profile.targetPostPrandialMaxMgDl,
        )
    }

    private fun computeWaveParams(
        grassiG: Double,
        proteineG: Double,
        effectiveGl: Double,
        effectiveMealGi: Double,
        fpuCount: Double,
        profile: GlycemicProfile,
    ): WaveParams {
        val tBase = (78.0 - 0.50 * effectiveMealGi).coerceIn(32.0, 75.0)
        val deltaDelay = (0.85 * grassiG + 0.30 * proteineG).coerceIn(0.0, 55.0)
        val tPeak = tBase + deltaDelay
        val blunt = 1.0 / (1.0 + deltaDelay / 130.0)
        val choAmp = (effectiveGl / 10.0) * profile.carbSensitivityMgDlPer10g * 1.15 * blunt

        val fpAmp = if (fpuCount >= 0.5) {
            ((fpuCount - 0.3).coerceAtLeast(0.0) * profile.carbSensitivityMgDlPer10g * 0.38)
                .coerceAtMost(75.0)
        } else {
            0.0
        }

        return WaveParams(
            tPeak = tPeak,
            choAmp = choAmp.coerceAtLeast(0.0),
            tFp = T_FP_MINUTES,
            fpAmp = fpAmp,
        )
    }

    private fun runSimulation(
        totalChoG: Double,
        totalGrassiG: Double,
        totalProteineG: Double,
        effectiveGl: Double,
        effectiveMealGi: Double,
        profile: GlycemicProfile,
        hasEstimatedGiFoods: Boolean,
        perFoodLoads: List<FoodGlycemicBreakdown>,
    ): GlycemicSimulationResult {
        val baseline = profile.baselineGlucoseMgDl
        val fpuCount = (totalGrassiG * 9.0 + totalProteineG * 4.0) / 100.0
        val wave = computeWaveParams(
            grassiG = totalGrassiG,
            proteineG = totalProteineG,
            effectiveGl = effectiveGl,
            effectiveMealGi = effectiveMealGi,
            fpuCount = fpuCount,
            profile = profile,
        )

        val points = (0..CURVE_DURATION_MIN step CURVE_STEP_MIN).map { t ->
            val tDouble = t.toDouble()
            val choDelta = wave.choDeltaAt(tDouble)
            val fpDelta = wave.fpDeltaAt(tDouble)
            GlycemicPoint(
                minutesFromMeal = t,
                glucoseMgDl = baseline + choDelta + fpDelta,
                choDeltaMgDl = choDelta,
                fatProteinDeltaMgDl = fpDelta,
            )
        }

        // Valutazione fine (risoluzione 1 min) per individuare picco esatto e rientro a basale.
        var peakMgDl = baseline
        var peakTimeMinutes = 0
        for (t in 0..CURVE_DURATION_MIN) {
            val g = baseline + wave.choDeltaAt(t.toDouble()) + wave.fpDeltaAt(t.toDouble())
            if (g > peakMgDl) {
                peakMgDl = g
                peakTimeMinutes = t
            }
        }
        val deltaPeakMgDl = (peakMgDl - baseline).coerceAtLeast(0.0)

        val returnToBaselineMinutes: Int? = if (deltaPeakMgDl < 1.0) {
            0
        } else {
            ((peakTimeMinutes + 1)..CURVE_DURATION_MIN).firstOrNull { t ->
                val g = baseline + wave.choDeltaAt(t.toDouble()) + wave.fpDeltaAt(t.toDouble())
                g <= baseline + RETURN_TO_BASELINE_TOLERANCE_MG_DL
            }
        }

        val glucoseAt60 = points.first { it.minutesFromMeal == 60 }.glucoseMgDl
        val glucoseAt120 = points.first { it.minutesFromMeal == 120 }.glucoseMgDl
        val glucoseAt180 = points.first { it.minutesFromMeal == 180 }.glucoseMgDl
        val level = classifyGlycemicLoad(effectiveGl)

        val insight = buildInsightText(
            totalChoG = totalChoG,
            level = level,
            fpuCount = fpuCount,
            peakMgDl = peakMgDl,
            deltaPeakMgDl = deltaPeakMgDl,
            peakTimeMinutes = peakTimeMinutes,
            targetMaxMgDl = profile.targetPostPrandialMaxMgDl,
            returnToBaselineMinutes = returnToBaselineMinutes,
        )

        return GlycemicSimulationResult(
            totalGlycemicLoad = effectiveGl,
            glycemicLoadLevel = level,
            effectiveMealGi = effectiveMealGi,
            fpuCount = fpuCount,
            baselineMgDl = baseline,
            peakMgDl = peakMgDl,
            deltaPeakMgDl = deltaPeakMgDl,
            peakTimeMinutes = peakTimeMinutes,
            glucoseAt60MinMgDl = glucoseAt60,
            glucoseAt120MinMgDl = glucoseAt120,
            glucoseAt180MinMgDl = glucoseAt180,
            returnToBaselineMinutes = returnToBaselineMinutes,
            points = points,
            hasEstimatedGiFoods = hasEstimatedGiFoods,
            perFoodLoads = perFoodLoads,
            insightText = insight,
        )
    }

    private fun buildInsightText(
        totalChoG: Double,
        level: GlycemicLoadLevel,
        fpuCount: Double,
        peakMgDl: Double,
        deltaPeakMgDl: Double,
        peakTimeMinutes: Int,
        targetMaxMgDl: Double,
        returnToBaselineMinutes: Int?,
    ): String {
        if (totalChoG < 0.5 && fpuCount < 0.5) {
            return "Impatto glicemico trascurabile: curva sostanzialmente stabile intorno al valore basale."
        }
        if (totalChoG < 0.5 && fpuCount >= 0.5) {
            return "Pasto a bassissimi carboidrati con %.1f FPU: possibile lieve salita tardiva verso 2–3 ore (+${deltaPeakMgDl.roundToInt()} mg/dL).".format(fpuCount)
        }

        val parts = mutableListOf<String>()
        when (level) {
            GlycemicLoadLevel.BASSO -> parts += "Carico glicemico basso con picco contenuto (~${peakMgDl.roundToInt()} mg/dL a ${peakTimeMinutes} min)."
            GlycemicLoadLevel.MEDIO -> parts += "Carico glicemico moderato con picco stimato di ~${peakMgDl.roundToInt()} mg/dL a ${peakTimeMinutes} min."
            GlycemicLoadLevel.ALTO -> parts += "Carico glicemico elevato: picco stimato ~${peakMgDl.roundToInt()} mg/dL (+${deltaPeakMgDl.roundToInt()} mg/dL) intorno a ${peakTimeMinutes} min."
        }

        if (peakMgDl > targetMaxMgDl) {
            parts += "Supera il target post-prandiale (${targetMaxMgDl.roundToInt()} mg/dL)."
        }

        if (fpuCount >= 1.5) {
            parts += "Quota grassi/proteine significativa (%.1f FPU): svuotamento rallentato e coda glicemica prolungata tra 2 e 4 ore.".format(fpuCount)
        } else if (returnToBaselineMinutes == null) {
            parts += "Rientro lento oltre le 4 ore."
        }

        return parts.joinToString(" ")
    }
}
