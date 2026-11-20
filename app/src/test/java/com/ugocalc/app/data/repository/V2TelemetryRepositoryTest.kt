package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.BenchmarkMealDao
import com.ugocalc.app.data.local.db.dao.SessionModeAiModelPair
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.data.remote.aws.SyncResult
import com.ugocalc.app.domain.inference.PipelineOutcome
import com.ugocalc.app.domain.inference.m7.NutritionCalculator
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.VerificationReport
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.sync.CloudSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake in-memoria del DAO Room (§8 M8): sufficiente a testare la serializzazione del repository senza un vero database. */
private class TelemetryFakeV2AnalysisRecordDao : V2AnalysisRecordDao {
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

    override fun getAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.sortedByDescending { it.analyzedAtEpochMillis })

    override suspend fun getAll(): List<V2AnalysisRecordEntity> = rows.values.sortedByDescending { it.analyzedAtEpochMillis }

    override suspend fun getById(id: Long): V2AnalysisRecordEntity? = rows[id]

    override suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.sessionId == sessionId }

    override suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson != null && it.sessionMode == sessionMode && it.aiModelId == aiModelId }

    override fun getWithGroundTruthAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.filter { it.groundTruthPesiJson != null })

    override suspend fun getWithGroundTruthAll(): List<V2AnalysisRecordEntity> = rows.values.filter { it.groundTruthPesiJson != null }

    override suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson == null }

    override suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.sessionId == sessionId && it.groundTruthPesiJson == null }

    override suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair> =
        rows.values.filter { it.groundTruthPesiJson != null }
            .map { SessionModeAiModelPair(it.sessionMode, it.aiModelId) }
            .distinct()

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun getAiModelIdsWithGroundTruth(): List<String> =
        rows.values.filter { it.groundTruthPesiJson != null }.map { it.aiModelId }.distinct()

    override suspend fun getWithGroundTruthByModel(aiModelId: String): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson != null && it.aiModelId == aiModelId }

    override suspend fun insertIfAbsent(record: V2AnalysisRecordEntity): Long {
        val existing = rows.values.firstOrNull { it.clientId == record.clientId }
        return if (existing != null) -1 else insert(record)
    }

    override suspend fun getByClientId(clientId: String): V2AnalysisRecordEntity? =
        rows.values.firstOrNull { it.clientId == clientId }

    override suspend fun deleteBySessionId(sessionId: String) {
        rows.values.removeAll { it.sessionId == sessionId }
    }
}

private class TelemetryFakeBenchmarkMealDao : BenchmarkMealDao {
    private val rows = mutableMapOf<String, BenchmarkMealEntity>()
    override suspend fun insert(meal: BenchmarkMealEntity) { rows[meal.sessionId] = meal }
    override suspend fun update(meal: BenchmarkMealEntity) { rows[meal.sessionId] = meal }
    override suspend fun getById(sessionId: String): BenchmarkMealEntity? = rows[sessionId]
    override suspend fun getAll(): List<BenchmarkMealEntity> = rows.values.toList()
    override suspend fun getAllWithoutGroundTruth(): List<BenchmarkMealEntity> = rows.values.filter { it.groundTruthPesiJson == null }
    override suspend fun getAllWithGroundTruth(): List<BenchmarkMealEntity> = rows.values.filter { it.groundTruthPesiJson != null }
    override suspend fun deleteById(sessionId: String) { rows.remove(sessionId) }
    override suspend fun deleteAll() { rows.clear() }
}

private class TelemetryFakeCloudSyncManager : CloudSyncManager {
    override suspend fun enqueue(eventType: String, payloadJson: String) {}
}

private class TelemetryFakeCloudSyncClient : CloudSyncClient {
    override suspend fun fetchInferenceRecords(): List<String> = emptyList()
    override suspend fun fetchCalibrationRecords(): List<String> = emptyList()
    override suspend fun sendEvent(endpoint: String, eventId: String, userId: String, payloadJson: String): SyncResult = SyncResult.Success
}

class V2TelemetryRepositoryTest {

    private val model = AiModelCatalog.ALL.first()
    private val json = Json { ignoreUnknownKeys = true }

    private val scena = SceneGraph(schemaVersion = SceneGraph.SCHEMA_VERSION, ancore = emptyList(), alimenti = emptyList())
    private val stima = WeightEstimate(
        nome = "mela",
        strategia = EstimationStrategy.CONTABILE,
        pesoG = 150.0,
        pesoCrudoG = 150.0,
        intervallo = ConfidenceInterval(120.0, 180.0),
        catenaCalcolo = CalculationChain(formula = "conteggio × peso unitario", valori = emptyMap(), fonti = emptyList(), sintesi = "150 g"),
        confidenza = ConfidenceScore(90),
    )
    private val verifica = VerificationReport(controlli = emptyList())
    private val nutrizione = NutritionCalculator.calculate(listOf(stima), emptyMap())
    private val outcome = PipelineOutcome.Verificato(scena, listOf(stima), verifica, nutrizione)

    private fun repository(dao: V2AnalysisRecordDao = TelemetryFakeV2AnalysisRecordDao()) =
        V2TelemetryRepository(dao, TelemetryFakeBenchmarkMealDao(), json, TelemetryFakeCloudSyncManager(), TelemetryFakeCloudSyncClient())

    @Test
    fun `record serializes the pipeline outcome and leaves ground truth absent`() = runBlocking {
        val dao = TelemetryFakeV2AnalysisRecordDao()
        val repository = repository(dao)

        val id = repository.record(outcome, model, SessionMode.CASA)

        val saved = dao.getById(id)!!
        assertEquals(SessionMode.CASA.name, saved.sessionMode)
        assertEquals(model.id, saved.aiModelId)
        assertEquals(SceneGraph.SCHEMA_VERSION, saved.schemaVersion)
        assertTrue(saved.weightEstimatesJson.contains("mela"))
        assertNull(saved.groundTruthPesiJson)
        assertNull(saved.groundTruthLivello)
    }

    @Test
    fun `recordGroundTruth attaches the correction to the existing row without touching the rest`() = runBlocking {
        val dao = TelemetryFakeV2AnalysisRecordDao()
        val repository = repository(dao)
        val id = repository.record(outcome, model, SessionMode.CASA)

        repository.recordGroundTruth(id, mapOf("mela" to 170.0), GroundTruthLevel.GOLD)

        val saved = dao.getById(id)!!
        assertTrue(saved.groundTruthPesiJson!!.contains("170.0"))
        assertEquals("gold", saved.groundTruthLivello)
        assertEquals(model.id, saved.aiModelId)
    }

    @Test
    fun `recordGroundTruth on a missing record is a no-op`() = runBlocking {
        val dao = TelemetryFakeV2AnalysisRecordDao()
        val repository = repository(dao)

        repository.recordGroundTruth(999L, mapOf("mela" to 170.0), GroundTruthLevel.BRONZE)

        assertTrue(dao.getAll().isEmpty())
    }
}
