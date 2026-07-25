package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.SessionModeAiModelPair
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.FoodCategory
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.ConfidenceScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake in-memoria dei due DAO coinvolti, sullo stesso pattern di [V2TelemetryRepositoryTest]. */
private class BiasFakeV2AnalysisRecordDao : V2AnalysisRecordDao {
    private val rows = mutableMapOf<Long, V2AnalysisRecordEntity>()
    private var nextId = 1L

    override suspend fun insert(record: V2AnalysisRecordEntity): Long {
        val id = nextId++
        rows[id] = record.copy(id = id)
        return id
    }

    override suspend fun update(record: V2AnalysisRecordEntity) {
        rows[record.id] = record
    }

    override suspend fun getAll(): List<V2AnalysisRecordEntity> = rows.values.sortedByDescending { it.analyzedAtEpochMillis }

    override fun getAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.sortedByDescending { it.analyzedAtEpochMillis })

    override fun getWithGroundTruthAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.filter { it.groundTruthPesiJson != null })

    override suspend fun getWithGroundTruthAll(): List<V2AnalysisRecordEntity> = rows.values.filter { it.groundTruthPesiJson != null }

    override suspend fun getById(id: Long): V2AnalysisRecordEntity? = rows[id]

    override suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.sessionId == sessionId }

    override suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.sessionId == sessionId && it.groundTruthPesiJson == null && it.failureReason == null }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson != null && it.sessionMode == sessionMode && it.aiModelId == aiModelId }

    override suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson == null && it.failureReason == null }

    override suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair> =
        rows.values.filter { it.groundTruthPesiJson != null }
            .map { SessionModeAiModelPair(it.sessionMode, it.aiModelId) }
            .distinct()

    override suspend fun getAiModelIdsWithGroundTruth(): List<String> =
        rows.values.filter { it.groundTruthPesiJson != null }
            .map { it.aiModelId }
            .distinct()

    override suspend fun getWithGroundTruthByModel(aiModelId: String): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson != null && it.aiModelId == aiModelId }

    override suspend fun insertIfAbsent(record: V2AnalysisRecordEntity): Long {
        val existing = rows.values.firstOrNull { it.clientId == record.clientId }
        return if (existing != null) -1 else insert(record)
    }

    override suspend fun getByClientId(clientId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.clientId == clientId }
}

private class BiasFakeBiasCorrectionDao : BiasCorrectionDao {
    val saved = mutableMapOf<List<String>, BiasCorrectionEntity>()

    override suspend fun get(strategy: String, aiModelId: String, foodCategory: String): BiasCorrectionEntity? =
        saved[listOf(strategy, aiModelId, foodCategory)]

    override suspend fun getAll(): List<BiasCorrectionEntity> = saved.values.toList()

    override fun getAllFlow(): Flow<List<BiasCorrectionEntity>> = flowOf(saved.values.toList())

    override suspend fun upsert(entity: BiasCorrectionEntity) {
        saved[listOf(entity.strategy, entity.aiModelId, entity.foodCategory)] = entity
    }

    override suspend fun deleteAll() { saved.clear() }
}

class BiasCorrectionRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val modelId = "claude-opus-4.8"

    private fun estimate(nome: String, pesoG: Double, strategia: EstimationStrategy = EstimationStrategy.DISTESA_SUL_PIATTO) = WeightEstimate(
        nome = nome,
        strategia = strategia,
        pesoG = pesoG,
        pesoCrudoG = pesoG,
        intervallo = ConfidenceInterval(pesoG * 0.8, pesoG * 1.2),
        catenaCalcolo = CalculationChain(formula = "test", valori = emptyMap(), fonti = emptyList(), sintesi = "$pesoG g"),
        confidenza = ConfidenceScore(90),
    )

    private fun recordWithGroundTruth(analysisDao: BiasFakeV2AnalysisRecordDao, estimates: List<WeightEstimate>, groundTruth: Map<String, Double>) = runBlocking {
        analysisDao.insert(
            V2AnalysisRecordEntity(
                analyzedAtEpochMillis = System.currentTimeMillis(),
                sessionMode = SessionMode.CASA.name,
                aiModelId = modelId,
                schemaVersion = 1,
                sceneGraphJson = "{}",
                weightEstimatesJson = json.encodeToString(estimates),
                verificationReportJson = "{}",
                nutritionReportJson = "{}",
                groundTruthPesiJson = json.encodeToString(groundTruth),
                groundTruthLivello = "gold",
            ),
        )
    }

    @Test
    fun `a stable underestimation across enough meals saves a correction factor`() = runBlocking {
        val analysisDao = BiasFakeV2AnalysisRecordDao()
        val biasDao = BiasFakeBiasCorrectionDao()
        val repository = BiasCorrectionRepository(analysisDao, biasDao, json)

        // 5 pasti, M3 sottostima sempre il riso dello stesso segmento (casa/distesa/questo modello).
        val veri = listOf(120.0, 110.0, 130.0, 115.0, 125.0)
        veri.forEach { vero -> recordWithGroundTruth(analysisDao, listOf(estimate("riso in bianco", 100.0)), mapOf("riso in bianco" to vero)) }

        repository.recalculate(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true)

        val fattore = repository.currentFactor(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true)
        assertEquals(1.20, fattore!!, 1e-9)
    }

    @Test
    fun `too few observations for the segment leave no factor saved`() = runBlocking {
        val analysisDao = BiasFakeV2AnalysisRecordDao()
        val biasDao = BiasFakeBiasCorrectionDao()
        val repository = BiasCorrectionRepository(analysisDao, biasDao, json)

        recordWithGroundTruth(analysisDao, listOf(estimate("riso in bianco", 100.0)), mapOf("riso in bianco" to 120.0))

        repository.recalculate(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true)

        assertNull(repository.currentFactor(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true))
    }

    @Test
    fun `observations from a different strategy, mode or model never mix into the segment`() = runBlocking {
        val analysisDao = BiasFakeV2AnalysisRecordDao()
        val biasDao = BiasFakeBiasCorrectionDao()
        val repository = BiasCorrectionRepository(analysisDao, biasDao, json)

        // Stesso alimento ma strategia diversa: non deve contare per DISTESA_SUL_PIATTO.
        repeat(5) {
            recordWithGroundTruth(
                analysisDao,
                listOf(estimate("pollo", 100.0, strategia = EstimationStrategy.BLOCCO_MISURABILE)),
                mapOf("pollo" to 120.0),
            )
        }
        // Modalità ristorante: deve comunque contare per il segmento, perché la modalità non separa più i dati (§8).
        runBlocking {
            analysisDao.insert(
                V2AnalysisRecordEntity(
                    analyzedAtEpochMillis = System.currentTimeMillis(),
                    sessionMode = SessionMode.RISTORANTE.name,
                    aiModelId = modelId,
                    schemaVersion = 1,
                    sceneGraphJson = "{}",
                    weightEstimatesJson = json.encodeToString(listOf(estimate("riso in bianco", 100.0))),
                    verificationReportJson = "{}",
                    nutritionReportJson = "{}",
                    groundTruthPesiJson = json.encodeToString(mapOf("riso in bianco" to 120.0)),
                    groundTruthLivello = "gold",
                ),
            )
        }

        // Una sola osservazione (quella ristorante sopra): non bastano 5 per ricalcolare, quindi resta null.
        repository.recalculate(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true)

        assertNull(repository.currentFactor(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true))
        assertNull(repository.currentFactor(EstimationStrategy.BLOCCO_MISURABILE, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true))
    }

    @Test
    fun `without consent, recalculate is a no-op and currentFactor never returns a stored value`() = runBlocking {
        val analysisDao = BiasFakeV2AnalysisRecordDao()
        val biasDao = BiasFakeBiasCorrectionDao()
        val repository = BiasCorrectionRepository(analysisDao, biasDao, json)

        val veri = listOf(120.0, 110.0, 130.0, 115.0, 125.0)
        veri.forEach { vero -> recordWithGroundTruth(analysisDao, listOf(estimate("riso in bianco", 100.0)), mapOf("riso in bianco" to vero)) }

        repository.recalculate(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = false)

        assertTrue(biasDao.saved.isEmpty())
        assertNull(repository.currentFactor(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = false))
    }

    @Test
    fun `usage stats expose raw errors and estimated correction impact`() = runBlocking {
        val analysisDao = BiasFakeV2AnalysisRecordDao()
        val biasDao = BiasFakeBiasCorrectionDao()
        val repository = BiasCorrectionRepository(analysisDao, biasDao, json)

        listOf(120.0, 120.0, 120.0, 120.0, 120.0).forEach { vero ->
            recordWithGroundTruth(analysisDao, listOf(estimate("riso in bianco", 100.0)), mapOf("riso in bianco" to vero))
        }
        repository.recalculate(EstimationStrategy.DISTESA_SUL_PIATTO, modelId, FoodCategory.RISO_E_CEREALI, consentGiven = true)

        val row = repository.getAllCorrectionsWithUsageStats().single()
        val stats = row.usageStats!!

        assertEquals(5, stats.observationCount)
        assertEquals(100.0, stats.meanEstimatedWeightG, 1e-9)
        assertEquals(120.0, stats.meanGroundTruthWeightG, 1e-9)
        assertEquals(120.0, stats.meanCorrectedWeightG, 1e-9)
        assertEquals(20.0, stats.meanAppliedCorrectionG, 1e-9)
        assertEquals(-20.0, stats.meanErrorG, 1e-9)
        assertEquals(20.0, stats.meanAbsoluteErrorG, 1e-9)
        assertEquals(400.0, stats.meanSquaredErrorG2, 1e-9)
        assertEquals(20.0, stats.rootMeanSquaredErrorG, 1e-9)
        assertEquals(0.0, stats.correctedMeanErrorG, 1e-9)
        assertEquals(0.0, stats.correctedMeanAbsoluteErrorG, 1e-9)
        assertEquals(0.0, stats.correctedRootMeanSquaredErrorG, 1e-9)
    }
}
