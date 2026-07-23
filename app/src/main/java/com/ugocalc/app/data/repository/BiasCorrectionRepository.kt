package com.ugocalc.app.data.repository

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.domain.inference.m2.FoodCategoryRouter
import com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator
import com.ugocalc.app.domain.inference.m8.BiasCorrectionResult
import com.ugocalc.app.domain.inference.m8.BiasObservation
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.FoodCategory
import com.ugocalc.app.domain.inference.model.WeightEstimate
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ricalcolo automatico dei fattori di correzione (piano V2 §8): legge da M8
 * ([V2AnalysisRecordDao.getWithGroundTruthByModel]) tutte le analisi con ground truth per l'esatto
 * segmento (strategia, modello M1, categoria alimentare) — mai un'aggregazione cross-modello —
 * e aggiorna [BiasCorrectionDao] solo se [BiasCorrectionCalculator] giudica il bias stabile.
 * La modalità di sessione (CASA/RISTORANTE) non fa parte del segmento: l'errore sistematico
 * dell'AI dipende dalla morfologia dell'alimento, non dal contesto di consumo. Interamente
 * automatico: nessuna decisione dell'owner nel loop (§8, "Questo è il meccanismo di
 * auto-miglioramento del sistema").
 *
 * `consentGiven` è sempre esplicito a carico del chiamante (mai letto da qui): questa classe non
 * dipende da [SettingsRepository] per restare testabile senza un Context Android.
 */
@Singleton
class BiasCorrectionRepository @Inject constructor(
    private val analysisDao: V2AnalysisRecordDao,
    private val biasCorrectionDao: BiasCorrectionDao,
    private val json: Json,
) {
    /**
     * Tutti i fattori calcolati finora, per la vista di trasparenza (Impostazioni > BIAS ed
     * evoluzione del comportamento): mostrati indipendentemente dallo stato attuale del consenso,
     * che regola solo se si calcolano/salvano *nuovi* fattori, non la visibilità di quelli già
     * esistenti.
     */
    suspend fun getAllCorrections(): List<BiasCorrectionEntity> = biasCorrectionDao.getAll()

    /**
     * Caricamento ottimizzato di TUTTI i segmenti che hanno almeno un'osservazione (§ richiesta
     * utente: "vedere tutte le analisi"), anche se il bias non è ancora stabile o il campione è
     * insufficiente per calcolare un fattore. Permette di capire perché una correzione non è
     * ancora attiva (motivazione del bias).
     */
    fun getAllCorrectionsWithUsageStatsFlow(): kotlinx.coroutines.flow.Flow<List<BiasCorrectionWithUsageStats>> =
        kotlinx.coroutines.flow.combine(
            biasCorrectionDao.getAllFlow(),
            analysisDao.getWithGroundTruthAllFlow()
        ) { existingList, allGroundTruthRecords ->
            val existingCorrections = existingList.associateBy {
                "${it.aiModelId}|${it.strategy}|${it.foodCategory}"
            }

            // Raggruppa le osservazioni per segmento in un unico passaggio O(N_record * N_stime)
            val observationsBySegment = mutableMapOf<String, MutableList<BiasObservation>>()

            for (record in allGroundTruthRecords) {
                val estimates = try {
                    json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
                } catch (e: Exception) {
                    emptyList()
                }
                val groundTruth = try {
                    record.groundTruthPesiJson?.let { json.decodeFromString<Map<String, Double>>(it) } ?: emptyMap()
                } catch (e: Exception) {
                    emptyMap()
                }

                for (est in estimates) {
                    val category = FoodCategoryRouter.categorize(est.nome).name
                    val pesoVero = groundTruth[est.nome]
                    if (pesoVero != null) {
                        val key = "${record.aiModelId}|${est.strategia.name}|$category"
                        // Il ground truth è a crudo: confronto con pesoCrudoG (§ piano crudo/cotto).
                        val pesoCrudo = est.pesoCrudoG
                        observationsBySegment.getOrPut(key) { mutableListOf() }.add(BiasObservation(pesoCrudo, pesoVero))
                    }
                }
            }

            observationsBySegment.map { (key, observations) ->
                val parts = key.split("|")
                val modelId = parts[0]
                val strategy = parts[1]
                val category = parts[2]

                val existing = existingCorrections[key]
                val result = BiasCorrectionCalculator.calculate(observations)

                BiasCorrectionWithUsageStats(
                    correction = existing ?: BiasCorrectionEntity(
                        aiModelId = modelId,
                        strategy = strategy,
                        foodCategory = category,
                        factor = 1.0,
                        sampleCount = observations.size,
                        updatedAtEpochMillis = 0
                    ),
                    usageStats = computeUsageStats(observations, existing?.factor ?: 1.0),
                    calculationResult = result
                )
            }.sortedByDescending { it.usageStats?.observationCount ?: 0 }
        }

    suspend fun getAllCorrectionsWithUsageStats(): List<BiasCorrectionWithUsageStats> {
        val existingList = biasCorrectionDao.getAll()
        val allGroundTruthRecords = analysisDao.getWithGroundTruthAll()
        val existingCorrections = existingList.associateBy {
            "${it.aiModelId}|${it.strategy}|${it.foodCategory}"
        }

        val observationsBySegment = mutableMapOf<String, MutableList<BiasObservation>>()
        for (record in allGroundTruthRecords) {
            val estimates = try {
                json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
            } catch (e: Exception) {
                emptyList()
            }
            val groundTruth = try {
                record.groundTruthPesiJson?.let { json.decodeFromString<Map<String, Double>>(it) } ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }

            for (est in estimates) {
                val category = FoodCategoryRouter.categorize(est.nome).name
                val pesoVero = groundTruth[est.nome]
                if (pesoVero != null) {
                    val key = "${record.aiModelId}|${est.strategia.name}|$category"
                    observationsBySegment.getOrPut(key) { mutableListOf() }.add(BiasObservation(est.pesoCrudoG, pesoVero))
                }
            }
        }

        return observationsBySegment.map { (key, observations) ->
            val parts = key.split("|")
            val modelId = parts[0]
            val strategy = parts[1]
            val category = parts[2]

            val existing = existingCorrections[key]
            val result = BiasCorrectionCalculator.calculate(observations)

            BiasCorrectionWithUsageStats(
                correction = existing ?: BiasCorrectionEntity(
                    aiModelId = modelId,
                    strategy = strategy,
                    foodCategory = category,
                    factor = 1.0,
                    sampleCount = observations.size,
                    updatedAtEpochMillis = 0
                ),
                usageStats = computeUsageStats(observations, existing?.factor ?: 1.0),
                calculationResult = result
            )
        }.sortedByDescending { it.usageStats?.observationCount ?: 0 }
    }

    private fun computeUsageStats(observations: List<BiasObservation>, factor: Double): BiasCorrectionUsageStats {
        val rawErrors = observations.map { it.pesoStimatoG - it.pesoVeroG }
        val correctedEstimates = observations.map { it.pesoStimatoG * factor }
        val correctedErrors = correctedEstimates.zip(observations).map { (corrected, observation) ->
            corrected - observation.pesoVeroG
        }
        val squaredErrors = rawErrors.map { it.pow(2) }
        val correctedSquaredErrors = correctedErrors.map { it.pow(2) }

        return BiasCorrectionUsageStats(
            observationCount = observations.size,
            meanEstimatedWeightG = observations.map { it.pesoStimatoG }.average(),
            meanGroundTruthWeightG = observations.map { it.pesoVeroG }.average(),
            meanCorrectedWeightG = correctedEstimates.average(),
            meanAppliedCorrectionG = correctedEstimates.zip(observations).map { (corrected, observation) ->
                corrected - observation.pesoStimatoG
            }.average(),
            meanErrorG = rawErrors.average(),
            meanAbsoluteErrorG = rawErrors.map { abs(it) }.average(),
            meanSquaredErrorG2 = squaredErrors.average(),
            rootMeanSquaredErrorG = sqrt(squaredErrors.average()),
            correctedMeanErrorG = correctedErrors.average(),
            correctedMeanAbsoluteErrorG = correctedErrors.map { abs(it) }.average(),
            correctedRootMeanSquaredErrorG = sqrt(correctedSquaredErrors.average()),
        )
    }

    /** Fattore corrente per il segmento, se esiste (§8: consumato da M3 nella catena di calcolo). Null se il consenso non è dato, dati insufficienti o bias instabile. */
    suspend fun currentFactor(strategy: EstimationStrategy, aiModelId: String, foodCategory: FoodCategory, consentGiven: Boolean): Double? {
        if (!consentGiven) return null
        return biasCorrectionDao.get(strategy.name, aiModelId, foodCategory.name)?.factor
    }

    /** Ricalcola il fattore per (strategy, aiModelId, foodCategory) dalle correzioni accumulate in M8, aggregando casa+ristorante. Va chiamato a ogni nuova correzione o come job periodico (§8); no-op se il consenso non è dato. */
    suspend fun recalculate(strategy: EstimationStrategy, aiModelId: String, foodCategory: FoodCategory, consentGiven: Boolean) {
        if (!consentGiven) return
        val observations = observationsFor(strategy.name, aiModelId, foodCategory)

        val result = BiasCorrectionCalculator.calculate(observations)
        (result as? BiasCorrectionResult.Applicabile)?.let { applicable ->
            biasCorrectionDao.upsert(
                BiasCorrectionEntity(
                    strategy = strategy.name,
                    aiModelId = aiModelId,
                    foodCategory = foodCategory.name,
                    factor = applicable.fattore,
                    sampleCount = applicable.campione,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
        // Dati insufficienti o bias instabile (§8): nessun fattore salvato, M3 resta sulla stima grezza.
    }

    /**
     * Ricalcola tutti i segmenti (strategia, modello, categoria alimentare) che hanno almeno un
     * ground truth registrato (§8, §13 punto 3, §14): pensato per un trigger periodico o
     * all'apertura dell'app. La combinazione (strategy, foodCategory) da ricalcolare è dedotta
     * dalle osservazioni effettivamente presenti per ciascun modello, invece di provare tutto il
     * prodotto cartesiano — molte combinazioni non possono mai comparire nei dati. I dati di
     * entrambe le modalità (CASA e RISTORANTE) vengono aggregati insieme. No-op se il consenso non
     * è dato — [recalculate] lo verifica comunque per ogni segmento.
     */
    suspend fun recalculateAll(consentGiven: Boolean) {
        if (!consentGiven) return
        val aiModelIds = analysisDao.getAiModelIdsWithGroundTruth()
        for (aiModelId in aiModelIds) {
            val records = analysisDao.getWithGroundTruthByModel(aiModelId)
            val presentCombos = records.asSequence().flatMap { record ->
                json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
                    .map { estimate -> estimate.strategia to FoodCategoryRouter.categorize(estimate.nome) }
            }.distinct().toList()
            for ((strategy, foodCategory) in presentCombos) {
                recalculate(strategy, aiModelId, foodCategory, consentGiven)
            }
        }
    }

    private suspend fun usageStatsFor(correction: BiasCorrectionEntity): BiasCorrectionUsageStats? {
        val foodCategory = FoodCategory.entries.firstOrNull { it.name == correction.foodCategory } ?: return null
        val observations = observationsFor(correction.strategy, correction.aiModelId, foodCategory)
        if (observations.isEmpty()) return null

        val rawErrors = observations.map { it.pesoStimatoG - it.pesoVeroG }
        val correctedEstimates = observations.map { it.pesoStimatoG * correction.factor }
        val correctedErrors = correctedEstimates.zip(observations).map { (corrected, observation) ->
            corrected - observation.pesoVeroG
        }
        val squaredErrors = rawErrors.map { it.pow(2) }
        val correctedSquaredErrors = correctedErrors.map { it.pow(2) }

        return BiasCorrectionUsageStats(
            observationCount = observations.size,
            meanEstimatedWeightG = observations.map { it.pesoStimatoG }.average(),
            meanGroundTruthWeightG = observations.map { it.pesoVeroG }.average(),
            meanCorrectedWeightG = correctedEstimates.average(),
            meanAppliedCorrectionG = correctedEstimates.zip(observations).map { (corrected, observation) ->
                corrected - observation.pesoStimatoG
            }.average(),
            meanErrorG = rawErrors.average(),
            meanAbsoluteErrorG = rawErrors.map { abs(it) }.average(),
            meanSquaredErrorG2 = squaredErrors.average(),
            rootMeanSquaredErrorG = sqrt(squaredErrors.average()),
            correctedMeanErrorG = correctedErrors.average(),
            correctedMeanAbsoluteErrorG = correctedErrors.map { abs(it) }.average(),
            correctedRootMeanSquaredErrorG = sqrt(correctedSquaredErrors.average()),
        )
    }

    private suspend fun observationsFor(strategy: String, aiModelId: String, foodCategory: FoodCategory): List<BiasObservation> {
        val records = analysisDao.getWithGroundTruthByModel(aiModelId)
        return records.flatMap { record ->
            val estimates = json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
            val groundTruth = record.groundTruthPesiJson?.let { json.decodeFromString<Map<String, Double>>(it) } ?: emptyMap()
            estimates.filter { (it.strategia.name == strategy) && (FoodCategoryRouter.categorize(it.nome) == foodCategory) }.mapNotNull { estimate ->
                groundTruth[estimate.nome]?.let { pesoVero -> BiasObservation(estimate.pesoCrudoG, pesoVero) }
            }
        }
    }
}

@Immutable
data class BiasCorrectionWithUsageStats(
    val correction: BiasCorrectionEntity,
    val usageStats: BiasCorrectionUsageStats?,
    val calculationResult: BiasCorrectionResult,
)

@Stable
data class BiasCorrectionUsageStats(
    val observationCount: Int,
    val meanEstimatedWeightG: Double,
    val meanGroundTruthWeightG: Double,
    val meanCorrectedWeightG: Double,
    val meanAppliedCorrectionG: Double,
    val meanErrorG: Double,
    val meanAbsoluteErrorG: Double,
    val meanSquaredErrorG2: Double,
    val rootMeanSquaredErrorG: Double,
    val correctedMeanErrorG: Double,
    val correctedMeanAbsoluteErrorG: Double,
    val correctedRootMeanSquaredErrorG: Double,
)
