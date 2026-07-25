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
import com.ugocalc.app.sync.CloudSyncEventType
import com.ugocalc.app.sync.CloudSyncManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Metriche aggregate per un motore (V1 o V2), calcolate sui pasti con peso vero registrato (§12: metriche già definite, non nuove). */
data class EngineStats(
    val weightMaeG: Double?,
    val weightSampleCount: Int,
    val choWithin10gPercent: Double?,
    val choWithin20gPercent: Double?,
    val choMealCount: Int,
)

/**
 * Esito della valutazione automatica V1 vs V2 (§13 punto 1, §20: "deve basarsi sul benchmark
 * pesato, nessuna logica automatica sceglie V1 o V2 al posto dell'owner" — questa è quella logica).
 */
data class PromotionDecision(
    val promoteV2: Boolean,
    val mealCount: Int,
    val recommendedMinMeals: Int,
    val v1: EngineStats,
    val v2: EngineStats,
)

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
    private val cloudSyncManager: CloudSyncManager,
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
        val updated = meal.copy(
            groundTruthPesiJson = json.encodeToString(pesiG),
            groundTruthLivello = livello.jsonValue,
        )
        benchmarkDao.update(updated)
        // Propaga il ground truth anche al record V2 linkato, così BiasCorrectionRepository
        // può calcolare i fattori di correzione senza che l'utente registri il peso due volte.
        meal.v2RecordId?.let { v2Id ->
            v2Dao.getById(v2Id)?.let { v2Record ->
                v2Dao.update(v2Record.copy(
                    groundTruthPesiJson = json.encodeToString(pesiG),
                    groundTruthLivello = livello.jsonValue,
                ))
            }
        }
        cloudSyncManager.enqueue(
            eventType = CloudSyncEventType.BENCHMARK,
            payloadJson = json.encodeToString(updated),
        )
    }

    /** Pasti con peso vero registrato, con le stime V1/V2 già decodificate: la base della dashboard di confronto (§10, §13 punto 5). */
    suspend fun listMealsWithGroundTruth(): List<BenchmarkMeal> =
        benchmarkDao.getAllWithGroundTruth().map { entity -> toBenchmarkMeal(entity) }

    /**
     * Decisione automatica V1 vs V2 (§13 punto 1, §20): promuove la V2 a motore visibile solo se,
     * su almeno [minMeals] pasti raccolti, la sua percentuale di pasti con errore CHO entro 10g è
     * migliore o pari a quella della V1 — il CHO è l'unità che conta davvero per l'utente, più
     * significativa del solo errore di peso per alimento. Sotto la soglia minima, o se manca la
     * metrica per uno dei due motori, resta su V1 per prudenza (dati insufficienti per decidere).
     *
     * DEBUG: Se [forceV2] è true (Impostazioni > BIAS), la promozione è forzata ignorando le metriche.
     */
    suspend fun evaluatePromotion(minMeals: Int = 30, forceV2: Boolean = false): PromotionDecision {
        if (forceV2) {
            val meals = listMealsWithGroundTruth()
            val v1 = computeEngineStats(meals, v1 = true)
            val v2 = computeEngineStats(meals, v1 = false)
            return PromotionDecision(true, meals.size, minMeals, v1, v2)
        }
        val meals = listMealsWithGroundTruth()
        val v1 = computeEngineStats(meals, v1 = true)
        val v2 = computeEngineStats(meals, v1 = false)
        val v1Percent = v1.choWithin10gPercent
        val v2Percent = v2.choWithin10gPercent
        val promoteV2 = meals.size >= minMeals && v1Percent != null && v2Percent != null && v2Percent >= v1Percent
        return PromotionDecision(promoteV2, meals.size, minMeals, v1, v2)
    }

    private fun computeEngineStats(meals: List<BenchmarkMeal>, v1: Boolean): EngineStats {
        val weightErrors = mutableListOf<Double>()
        var within10 = 0
        var within20 = 0
        var mealsWithCho = 0
        for (meal in meals) {
            // Per V2, il ground truth è a crudo: usa pesoCrudoG per il confronto corretto.
            val stimePerNome = if (v1) meal.v1PesiPerAlimento else meal.v2Stime.associate { it.nome to it.pesoCrudoG }
            for ((nome, veroG) in meal.groundTruthPesi) {
                stimePerNome[nome]?.let { stimaG -> weightErrors += abs(stimaG - veroG) }
            }
            // Coefficienti V1 come baseline condivisa per entrambi i motori: così la variabile
            // nel confronto è solo il peso stimato, non i coefficienti CHO (che per V2 possono
            // mancare se nutritionReportJson non è stato salvato, causando choMealCount = 0).
            val coeffPerNome = meal.v1NutrizionePerAlimento.mapValues { it.value.choPer100g }
            if (coeffPerNome.isEmpty()) continue
            val choVero = meal.groundTruthPesi.entries.sumOf { (nome, veroG) -> (coeffPerNome[nome] ?: 0.0) * veroG / 100.0 }
            val choStimato = stimePerNome.entries.sumOf { (nome, stimaG) -> (coeffPerNome[nome] ?: 0.0) * stimaG / 100.0 }
            mealsWithCho++
            // Errore relativo rispetto al CHO vero: 10% e 20% del valore reale.
            val erroreRelativo = if (choVero > 0.0) abs(choStimato - choVero) / choVero else 0.0
            if (erroreRelativo <= 0.10) within10++
            if (erroreRelativo <= 0.20) within20++
        }
        return EngineStats(
            weightMaeG = weightErrors.takeIf { it.isNotEmpty() }?.average(),
            weightSampleCount = weightErrors.size,
            choWithin10gPercent = mealsWithCho.takeIf { it > 0 }?.let { within10 * 100.0 / it },
            choWithin20gPercent = mealsWithCho.takeIf { it > 0 }?.let { within20 * 100.0 / it },
            choMealCount = mealsWithCho,
        )
    }

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
