package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.BenchmarkMealDao
import com.ugocalc.app.data.local.db.dao.SessionModeAiModelPair
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake in-memoria del DAO benchmark (§13 punto 5): sufficiente a testare il repository senza un vero database. */
private class FakeBenchmarkMealDao : BenchmarkMealDao {
    private val rows = mutableMapOf<String, BenchmarkMealEntity>()

    override suspend fun insert(meal: BenchmarkMealEntity) { rows[meal.sessionId] = meal }
    override suspend fun update(meal: BenchmarkMealEntity) { rows[meal.sessionId] = meal }
    override suspend fun getById(sessionId: String): BenchmarkMealEntity? = rows[sessionId]
    override suspend fun getAll(): List<BenchmarkMealEntity> = rows.values.sortedByDescending { it.capturedAtEpochMillis }
    override suspend fun getAllWithoutGroundTruth(): List<BenchmarkMealEntity> = rows.values.filter { it.groundTruthPesiJson == null }
    override suspend fun getAllWithGroundTruth(): List<BenchmarkMealEntity> = rows.values.filter { it.groundTruthPesiJson != null }
}

/** Fake in-memoria del DAO di telemetria V2, con l'intera interfaccia (a differenza del fake più datato in V2TelemetryRepositoryTest). */
private class FakeV2Dao : V2AnalysisRecordDao {
    private val rows = mutableMapOf<Long, V2AnalysisRecordEntity>()
    private var nextId = 1L

    override suspend fun insert(record: V2AnalysisRecordEntity): Long {
        val id = nextId++
        rows[id] = record.copy(id = id)
        return id
    }
    override suspend fun update(record: V2AnalysisRecordEntity) { rows[record.id] = record }
    override fun getAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.sortedByDescending { it.analyzedAtEpochMillis })
    override suspend fun getAll(): List<V2AnalysisRecordEntity> = rows.values.sortedByDescending { it.analyzedAtEpochMillis }
    override suspend fun getById(id: Long): V2AnalysisRecordEntity? = rows[id]
    override suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity? = rows.values.firstOrNull { it.sessionId == sessionId }
    override suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity? = rows.values.firstOrNull { it.sessionId == sessionId && it.groundTruthPesiJson == null }
    override suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity> =
        rows.values.filter { it.groundTruthPesiJson != null && it.sessionMode == sessionMode && it.aiModelId == aiModelId }
    override fun getWithGroundTruthAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.filter { it.groundTruthPesiJson != null })
    override suspend fun getWithGroundTruthAll(): List<V2AnalysisRecordEntity> = rows.values.filter { it.groundTruthPesiJson != null }
    override suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity> = rows.values.filter { it.groundTruthPesiJson == null }
    override suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair> =
        rows.values.filter { it.groundTruthPesiJson != null }.map { SessionModeAiModelPair(it.sessionMode, it.aiModelId) }.distinct()
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun deleteById(id: Long) { rows.remove(id) }
}

class BenchmarkRepositoryTest {

    private val model = AiModelCatalog.ALL.first()
    private val json = Json { ignoreUnknownKeys = true }

    private fun repository(benchmarkDao: FakeBenchmarkMealDao = FakeBenchmarkMealDao(), v2Dao: FakeV2Dao = FakeV2Dao()) =
        Triple(BenchmarkRepository(benchmarkDao, v2Dao, json), benchmarkDao, v2Dao)

    @Test
    fun `recordV1Estimate creates a new row with the raw weights per food`() = runBlocking {
        val (repo, dao, _) = repository()

        repo.recordV1Estimate("s1", 1000L, SessionMode.CASA, model, mapOf("riso" to 165.0))

        val saved = dao.getById("s1")!!
        assertEquals("CASA", saved.sessionMode)
        assertTrue(saved.v1PesiPerAlimentoJson!!.contains("165.0"))
        assertNull(saved.v2RecordId)
    }

    @Test
    fun `linkV2Record attaches the V2 telemetry id without touching V1 data`() = runBlocking {
        val (repo, dao, _) = repository()
        repo.recordV1Estimate("s1", 1000L, SessionMode.CASA, model, mapOf("riso" to 165.0))

        repo.linkV2Record("s1", 42L)

        val saved = dao.getById("s1")!!
        assertEquals(42L, saved.v2RecordId)
        assertTrue(saved.v1PesiPerAlimentoJson!!.contains("165.0"))
    }

    @Test
    fun `linkV2Record on a missing session is a no-op`() = runBlocking {
        val (repo, dao, _) = repository()

        repo.linkV2Record("missing", 42L)

        assertNull(dao.getById("missing"))
    }

    @Test
    fun `recordV1NutritionCoefficients stores only lines with a matched table entry`() = runBlocking {
        val (repo, dao, _) = repository()
        repo.recordV1Estimate("s1", 1000L, SessionMode.CASA, model, mapOf("riso" to 165.0, "acqua" to 200.0))

        val entry = NormalizedFoodEntry(nome = "riso", choPer100g = 78.0, grassiPer100g = 0.5)
        val lines = listOf(
            CalculatedFoodLine(
                recognizedFood = RecognizedFood("riso", 165.0, "", confidenza = Confidence.ALTO),
                match = FoodMatch("riso", "riso", nessunaCorrispondenza = false, confidenzaAbbinamento = Confidence.ALTO),
                tableEntry = entry,
            ),
            CalculatedFoodLine(
                recognizedFood = RecognizedFood("acqua", 200.0, "", confidenza = Confidence.ALTO),
                match = null,
                tableEntry = null,
            ),
        )

        repo.recordV1NutritionCoefficients("s1", lines)

        val saved = dao.getById("s1")!!
        assertTrue(saved.v1NutrizionePerAlimentoJson!!.contains("78.0"))
        assertTrue(!saved.v1NutrizionePerAlimentoJson!!.contains("acqua"))
    }

    @Test
    fun `listMealsWithGroundTruth joins the linked V2 record`() = runBlocking {
        val (repo, benchmarkDao, v2Dao) = repository()
        val v2Id = v2Dao.insert(
            V2AnalysisRecordEntity(
                analyzedAtEpochMillis = 1000L,
                sessionMode = "CASA",
                aiModelId = model.id,
                schemaVersion = 1,
                sceneGraphJson = "{}",
                weightEstimatesJson = "[]",
                verificationReportJson = "{}",
                nutritionReportJson = """{"righe":[],"choTotaleG":0.0,"grassiTotaleG":0.0}""",
            ),
        )
        benchmarkDao.insert(
            BenchmarkMealEntity(
                sessionId = "s1",
                capturedAtEpochMillis = 1000L,
                sessionMode = "CASA",
                aiModelId = model.id,
                v1PesiPerAlimentoJson = """{"riso":165.0}""",
                v2RecordId = v2Id,
                groundTruthPesiJson = """{"riso":170.0}""",
                groundTruthLivello = "gold",
            ),
        )

        val meals = repo.listMealsWithGroundTruth()

        assertEquals(1, meals.size)
        assertEquals(170.0, meals.first().groundTruthPesi["riso"])
        assertEquals(v2Id, meals.first().v2Record?.id)
    }

    @Test
    fun `foodNamesFor unions V1 and V2 food names`() = runBlocking {
        val (repo, _, v2Dao) = repository()
        val v2Id = v2Dao.insert(
            V2AnalysisRecordEntity(
                analyzedAtEpochMillis = 1000L,
                sessionMode = "CASA",
                aiModelId = model.id,
                schemaVersion = 1,
                sceneGraphJson = "{}",
                weightEstimatesJson = """[{"nome":"riso basmati","strategia":"distesa","peso_g":160.0,"intervallo":{"min_g":130.0,"max_g":190.0},"catena_calcolo":{"formula":"f","valori":{},"fonti":[],"sintesi":"s"},"confidenza":"alto"}]""",
                verificationReportJson = "{}",
                nutritionReportJson = """{"righe":[],"choTotaleG":0.0,"grassiTotaleG":0.0}""",
            ),
        )
        val entity = BenchmarkMealEntity(
            sessionId = "s1",
            capturedAtEpochMillis = 1000L,
            sessionMode = "CASA",
            aiModelId = model.id,
            v1PesiPerAlimentoJson = """{"riso in bianco":165.0}""",
            v2RecordId = v2Id,
        )

        val nomi = repo.foodNamesFor(entity)

        assertEquals(setOf("riso in bianco", "riso basmati"), nomi.toSet())
    }
}
