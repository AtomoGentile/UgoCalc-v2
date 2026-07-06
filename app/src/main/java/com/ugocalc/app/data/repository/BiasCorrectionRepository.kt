package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator
import com.ugocalc.app.domain.inference.m8.BiasCorrectionResult
import com.ugocalc.app.domain.inference.m8.BiasObservation
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ricalcolo automatico dei fattori di correzione (piano V2 §8): legge da M8
 * ([V2AnalysisRecordDao.getWithGroundTruth]) tutte le analisi con ground truth per l'esatto
 * segmento (modalità, strategia, modello M1) — mai un'aggregazione cross-modello o
 * cross-modalità — e aggiorna [BiasCorrectionDao] solo se [BiasCorrectionCalculator] giudica il
 * bias stabile. Interamente automatico: nessuna decisione dell'owner nel loop (§8, "Questo è il
 * meccanismo di auto-miglioramento del sistema").
 *
 * [consentGiven] è sempre esplicito a carico del chiamante (mai letto da qui): questa classe non
 * dipende da [SettingsRepository] per restare testabile senza un Context Android. Il consenso
 * dell'utente (Impostazioni > richiesta trattamento dati per l'identificazione del bias) va
 * verificato dal chiamante prima di invocare [recalculate] o [currentFactor] — con consenso non
 * dato, nessun dato viene letto, calcolato o restituito, coerente con "richiesta al trattamento dei
 * dati" come opt-in esplicito, non implicito nell'uso della funzione.
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

    suspend fun getAllCorrectionsWithUsageStats(): List<BiasCorrectionWithUsageStats> =
        getAllCorrections().map { correction ->
            BiasCorrectionWithUsageStats(
                correction = correction,
                usageStats = usageStatsFor(correction),
            )
        }

    /** Fattore corrente per il segmento, se esiste (§8: consumato da M3 nella catena di calcolo). Null se il consenso non è dato, dati insufficienti o bias instabile. */
    suspend fun currentFactor(sessionMode: SessionMode, strategy: EstimationStrategy, aiModelId: String, consentGiven: Boolean): Double? {
        if (!consentGiven) return null
        return biasCorrectionDao.get(sessionMode.name, strategy.name, aiModelId)?.factor
    }

    /** Ricalcola il fattore per (sessionMode, strategy, aiModelId) dalle correzioni accumulate in M8. Va chiamato a ogni nuova correzione o come job periodico (§8); no-op se il consenso non è dato. */
    suspend fun recalculate(sessionMode: SessionMode, strategy: EstimationStrategy, aiModelId: String, consentGiven: Boolean) {
        if (!consentGiven) return
        val observations = observationsFor(sessionMode.name, strategy.name, aiModelId)

        val result = BiasCorrectionCalculator.calculate(observations)
        if (result is BiasCorrectionResult.Applicabile) {
            biasCorrectionDao.upsert(
                BiasCorrectionEntity(
                    sessionMode = sessionMode.name,
                    strategy = strategy.name,
                    aiModelId = aiModelId,
                    factor = result.fattore,
                    sampleCount = result.campione,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
        // Dati insufficienti o bias instabile (§8): nessun fattore salvato, M3 resta sulla stima grezza.
    }

    /**
     * Ricalcola tutti i segmenti (modalità, strategia, modello) che hanno almeno un ground truth
     * registrato (§8, §13 punto 3): pensato per un trigger periodico o all'apertura dell'app, invece
     * di richiedere che il chiamante conosca in anticipo ogni combinazione possibile. No-op se il
     * consenso non è dato — [recalculate] lo verifica comunque per ogni segmento.
     */
    suspend fun recalculateAll(consentGiven: Boolean) {
        if (!consentGiven) return
        val segments = analysisDao.getSessionModeAiModelPairsWithGroundTruth()
        for (segment in segments) {
            val sessionMode = SessionMode.entries.firstOrNull { it.name == segment.sessionMode } ?: continue
            for (strategy in EstimationStrategy.entries) {
                recalculate(sessionMode, strategy, segment.aiModelId, consentGiven)
            }
        }
    }

    private suspend fun usageStatsFor(correction: BiasCorrectionEntity): BiasCorrectionUsageStats? {
        val observations = observationsFor(correction.sessionMode, correction.strategy, correction.aiModelId)
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

    private suspend fun observationsFor(sessionMode: String, strategy: String, aiModelId: String): List<BiasObservation> {
        val records = analysisDao.getWithGroundTruth(sessionMode, aiModelId)
        return records.flatMap { record ->
            val estimates = json.decodeFromString<List<WeightEstimate>>(record.weightEstimatesJson)
            val groundTruth = record.groundTruthPesiJson?.let { json.decodeFromString<Map<String, Double>>(it) } ?: emptyMap()
            estimates.filter { it.strategia.name == strategy }.mapNotNull { estimate ->
                groundTruth[estimate.nome]?.let { pesoVero -> BiasObservation(estimate.pesoG, pesoVero) }
            }
        }
    }
}

data class BiasCorrectionWithUsageStats(
    val correction: BiasCorrectionEntity,
    val usageStats: BiasCorrectionUsageStats?,
)

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
