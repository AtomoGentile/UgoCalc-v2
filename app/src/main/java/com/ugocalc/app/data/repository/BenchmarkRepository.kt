package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.BenchmarkMealDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.inference.m7.NutritionReport
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.CalculatedFoodLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Coefficienti nutrizionali per 100g trovati dal matching V1, per un alimento. */
@Serializable
data class V1NutritionCoefficient(val choPer100g: Double, val grassiPer100g: Double)

/** Un pasto del benchmark con le stime già decodificate, pronto per il confronto V1 vs V2. */
data class BenchmarkMeal(
    val entity: BenchmarkMealEntity,
    val v1PesiPerAlimento: Map<String, Double>,
    val v1NutrizionePerAlimento: Map<String, V1NutritionCoefficient>,
    val v2Record: V2AnalysisRecordEntity?,
    val v2Stime: List<WeightEstimate>,
    val v2Nutrizione: NutritionReport?,
    val groundTruthPesi: Map<String, Double>,
)

/**
 * Benchmark pesato V1 vs V2 (piano V2 §10, §13 punto 5): raccoglie, per lo stesso [sessionId]
 * generato in `AnalysisSessionStore`, la stima grezza di V1 e il record di telemetria V2
 * corrispondente, poi il peso vero registrato dall'utente. Non chiama mai l'AI di matching per
 * calcolare il CHO "vero": riusa i coefficienti CHO/100g già trovati da V1
 * ([recordV1NutritionCoefficients]) sul peso vero invece del peso stimato, restando così nel
 * budget di chiamate (§4).
 */
@Singleton
class BenchmarkRepository @Inject constructor(
    private val benchmarkDao: BenchmarkMealDao,
    private val v2Dao: V2AnalysisRecordDao,
    private val json: Json,
) {
    /** Salva la stima grezza V1 (pesi per alimento) appena disponibile, prima del matching nutrizionale. */
    suspend fun recordV1Estimate(
        sessionId: String,
        capturedAtEpochMillis: Long,
        sessionMode: SessionMode,
        aiModel: AiModel,
        pesiPerAlimento: Map<String, Double>,
    ) {
        val existing = benchmarkDao.getById(sessionId)
        benchmarkDao.insert(
            (existing ?: BenchmarkMealEntity(
                sessionId = sessionId,
                capturedAtEpochMillis = capturedAtEpochMillis,
                sessionMode = sessionMode.name,
                aiModelId = aiModel.id,
                v1PesiPerAlimentoJson = null,
            )).copy(v1PesiPerAlimentoJson = json.encodeToString(pesiPerAlimento)),
        )
    }

    /**
     * Salva i coefficienti CHO/100g e grassi/100g trovati dal matching V1 (§13 punto 5): best-effort,
     * disponibile solo se l'utente arriva a "Ricevi i CHO" nella stessa sessione. Nessun effetto se
     * il pasto non è stato tracciato (flag disattivato al momento dello scatto).
     */
    suspend fun recordV1NutritionCoefficients(sessionId: String, lines: List<CalculatedFoodLine>) {
        val existing = benchmarkDao.getById(sessionId) ?: return
        val coefficienti = lines.mapNotNull { line ->
            val entry = line.tableEntry ?: return@mapNotNull null
            line.recognizedFood.nome to V1NutritionCoefficient(entry.choPer100g, entry.grassiPer100g)
        }.toMap()
        if (coefficienti.isEmpty()) return
        benchmarkDao.update(existing.copy(v1NutrizionePerAlimentoJson = json.encodeToString(coefficienti)))
    }

    /** Collega il record M8 appena salvato da V2 al pasto tracciato per lo stesso [sessionId]. Nessun effetto se il pasto non esiste (flag disattivato o V1 non ancora salvato). */
    suspend fun linkV2Record(sessionId: String, v2RecordId: Long) {
        val existing = benchmarkDao.getById(sessionId) ?: return
        benchmarkDao.update(existing.copy(v2RecordId = v2RecordId))
    }

    suspend fun listPendingGroundTruth(): List<BenchmarkMealEntity> = benchmarkDao.getAllWithoutGroundTruth()

    /** Per la scorciatoia "Registra peso vero" a fine analisi (§13 punto 5): true se questa sessione è tracciata dal benchmark e non ha ancora un peso vero. */
    suspend fun hasPendingGroundTruth(sessionId: String): Boolean {
        val meal = benchmarkDao.getById(sessionId) ?: return false
        return meal.groundTruthPesiJson == null
    }

    /** Unione dei nomi alimento noti a V1 e V2 per [entity] (§13 punto 5): i due motori possono identificare lo stesso piatto con nomi leggermente diversi, mostrarli entrambi lascia all'utente la scelta di quali pesare. */
    suspend fun foodNamesFor(entity: BenchmarkMealEntity): List<String> {
        val v1Nomi = entity.v1PesiPerAlimentoJson?.let { json.decodeFromString<Map<String, Double>>(it).keys }.orEmpty()
        val v2Nomi = entity.v2RecordId?.let { v2Dao.getById(it) }?.weightEstimatesJson
            ?.let { json.decodeFromString<List<WeightEstimate>>(it).map { stima -> stima.nome } }.orEmpty()
        return (v1Nomi + v2Nomi).distinct()
    }

    suspend fun recordGroundTruth(sessionId: String, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        val meal = benchmarkDao.getById(sessionId) ?: return
        benchmarkDao.update(
            meal.copy(
                groundTruthPesiJson = json.encodeToString(pesiG),
                groundTruthLivello = livello.jsonValue,
            ),
        )
    }

    /** Pasti con peso vero registrato, con le stime V1/V2 già decodificate: la base della dashboard di confronto (§10, §13 punto 5). */
    suspend fun listMealsWithGroundTruth(): List<BenchmarkMeal> =
        benchmarkDao.getAllWithGroundTruth().map { entity -> toBenchmarkMeal(entity) }

    private suspend fun toBenchmarkMeal(entity: BenchmarkMealEntity): BenchmarkMeal {
        val v2Record = entity.v2RecordId?.let { v2Dao.getById(it) }
        return BenchmarkMeal(
            entity = entity,
            v1PesiPerAlimento = entity.v1PesiPerAlimentoJson?.let { json.decodeFromString<Map<String, Double>>(it) }.orEmpty(),
            v1NutrizionePerAlimento = entity.v1NutrizionePerAlimentoJson?.let { json.decodeFromString<Map<String, V1NutritionCoefficient>>(it) }.orEmpty(),
            v2Record = v2Record,
            v2Stime = v2Record?.weightEstimatesJson?.let { json.decodeFromString<List<WeightEstimate>>(it) }.orEmpty(),
            v2Nutrizione = v2Record?.nutritionReportJson?.let { json.decodeFromString<NutritionReport>(it) },
            groundTruthPesi = entity.groundTruthPesiJson?.let { json.decodeFromString<Map<String, Double>>(it) }.orEmpty(),
        )
    }
}
