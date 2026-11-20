package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.PhysicalDataDao
import com.ugocalc.app.data.local.db.entity.PhysicalDataEntity
import com.ugocalc.app.data.local.db.entity.PhysicalDataType
import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.m3.PhysicalData
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.WeightEstimate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalDataRepository @Inject constructor(
    private val physicalDataDao: PhysicalDataDao
) {
    suspend fun addDensity(nome: String, value: Double) {
        upsertDensity(nome, value)
    }

    suspend fun getCustomDensities(): List<PhysicalDataEntity> {
        return physicalDataDao.getByType(PhysicalDataType.DENSITY)
    }

    suspend fun getCustomUnitWeights(): List<PhysicalDataEntity> {
        return physicalDataDao.getByType(PhysicalDataType.UNIT_WEIGHT)
    }

    suspend fun upsertDensity(nome: String, value: Double) {
        upsert(PhysicalDataType.DENSITY, nome, value)
    }

    suspend fun upsertUnitWeight(nome: String, value: Double) {
        upsert(PhysicalDataType.UNIT_WEIGHT, nome, value)
    }

    /**
     * Apprende direttamente le costanti fisiche dell'utente (peso unitario per alimenti CONTABILE,
     * densità per alimenti DISTESA_SUL_PIATTO / BLOCCO_MISURABILE) a partire dal peso reale a crudo
     * fornito dall'utente. A differenza del bias statistico per modello, questi valori valgono subito
     * per tutti i modelli AI.
     */
    suspend fun learnFromGroundTruth(
        estimates: List<WeightEstimate>,
        groundTruthPesiG: Map<String, Double>,
    ) {
        if (estimates.isEmpty() || groundTruthPesiG.isEmpty()) return
        val normalizedTruth = groundTruthPesiG.entries.associate { (k, v) ->
            StrategyRouter.normalize(k) to v
        }

        for (est in estimates) {
            val pesoVeroCrudoG = groundTruthPesiG[est.nome]
                ?: normalizedTruth[StrategyRouter.normalize(est.nome)]
                ?: continue
            if (pesoVeroCrudoG <= 0.0) continue
            if (est.confidenza.percent < MIN_CONFIDENCE_FOR_LEARNING) continue

            when (est.strategia) {
                EstimationStrategy.CONTABILE -> {
                    val conteggio = est.catenaCalcolo.valori["conteggio"]
                    if (conteggio != null && conteggio >= 1.0) {
                        val unitWeightVero = (pesoVeroCrudoG / conteggio).coerceIn(0.5, 500.0)
                        upsertUnitWeight(est.nome, unitWeightVero)
                    }
                }
                EstimationStrategy.DISTESA_SUL_PIATTO,
                EstimationStrategy.BLOCCO_MISURABILE -> {
                    val volumeCm3 = est.catenaCalcolo.valori["volume_cm3"] ?: continue
                    if (volumeCm3 <= MIN_VOLUME_CM3_FOR_LEARNING) continue
                    val resa = est.catenaCalcolo.valori["resa_cottura"] ?: 1.0
                    val riemp = est.catenaCalcolo.valori["riempimento"] ?: 1.0
                    val count = if (est.strategia == EstimationStrategy.BLOCCO_MISURABILE) {
                        (est.catenaCalcolo.valori["conteggio"] ?: 1.0).coerceAtLeast(1.0)
                    } else {
                        1.0
                    }
                    val effVol = count * volumeCm3 * riemp
                    if (effVol <= 1.0) continue

                    val obsDens = (pesoVeroCrudoG * resa) / effVol
                    val base = PhysicalData.INSTANCE.density(est.nome).valore
                    val clamped = obsDens.coerceIn(base * 0.6, base * 1.6)
                    val blended = EMA_ALPHA * base + (1.0 - EMA_ALPHA) * clamped
                    upsertDensity(est.nome, blended)
                }
                else -> Unit
            }
        }
    }

    private suspend fun upsert(type: PhysicalDataType, nome: String, value: Double) {
        val cleanName = nome.trim()
        val norm = StrategyRouter.normalize(cleanName)
        if (norm.isEmpty()) return

        val existing = physicalDataDao.getByType(type)
            .filter { StrategyRouter.normalize(it.label) == norm }

        val finalValue = if (existing.isNotEmpty()) {
            val oldValue = existing.last().value
            existing.forEach { physicalDataDao.deleteById(it.id) }
            EMA_ALPHA * oldValue + (1.0 - EMA_ALPHA) * value
        } else {
            value
        }

        physicalDataDao.insert(
            PhysicalDataEntity(
                type = type,
                value = finalValue,
                label = cleanName,
                keywords = cleanName,
                isCustom = true,
            )
        )
    }

    private companion object {
        const val MIN_CONFIDENCE_FOR_LEARNING = 55
        const val MIN_VOLUME_CM3_FOR_LEARNING = 5.0
        const val EMA_ALPHA = 0.5
    }
}
