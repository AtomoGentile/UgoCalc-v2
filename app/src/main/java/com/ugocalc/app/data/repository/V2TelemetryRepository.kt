package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.inference.PipelineOutcome
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Livelli di ground truth per una correzione (piano V2 §7): Gold = bilancia, Silver = etichetta/fonte affidabile, Bronze = stima dell'utente. */
enum class GroundTruthLevel(val jsonValue: String) {
    GOLD("gold"), SILVER("silver"), BRONZE("bronze")
}

/**
 * M8 – Telemetria e archivio di apprendimento (piano V2 §5, §7, §8). Serializza l'intero percorso
 * intermedio di un'analisi V2 completata (SceneGraph, stime, verifica, nutrizione) e lo salva come
 * riga di [V2AnalysisRecordEntity]: è la base sia del benchmark (§10) sia dei fattori di correzione
 * appresi per (modalità, strategia, modello) (§8), che leggeranno da qui via
 * [V2AnalysisRecordDao.getWithGroundTruth].
 *
 * Il ground truth resta assente finché l'utente non fornisce una correzione manuale o un peso
 * reale (§7, §8): [recordGroundTruth] è l'unico punto che lo popola, mai [record].
 */
@Singleton
class V2TelemetryRepository @Inject constructor(
    private val dao: V2AnalysisRecordDao,
    private val json: Json,
) {
    suspend fun record(
        outcome: PipelineOutcome.Verificato,
        model: AiModel,
        sessionMode: SessionMode,
        sessionId: String? = null,
    ): Long {
        val entity = V2AnalysisRecordEntity(
            analyzedAtEpochMillis = System.currentTimeMillis(),
            sessionMode = sessionMode.name,
            aiModelId = model.id,
            schemaVersion = outcome.sceneGraph.schemaVersion,
            sceneGraphJson = json.encodeToString(outcome.sceneGraph),
            weightEstimatesJson = json.encodeToString(outcome.stime),
            verificationReportJson = json.encodeToString(outcome.verifica),
            nutritionReportJson = json.encodeToString(outcome.nutrizione),
            arbitratoMotivoMancato = outcome.arbitratoMotivoMancato,
            sessionId = sessionId,
        )
        return dao.insert(entity)
    }

    /** Analisi V2 ancora senza ground truth: la lista che l'utente può scegliere di correggere con un peso vero (§13 punto 3). */
    suspend fun listPendingGroundTruth(): List<V2AnalysisRecordEntity> = dao.getAllWithoutGroundTruth()

    fun getAllRecordsFlow(): kotlinx.coroutines.flow.Flow<List<V2AnalysisRecordEntity>> = dao.getAllFlow()

    suspend fun getAllRecords(): List<V2AnalysisRecordEntity> = dao.getAll()

    suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity? = dao.getBySessionId(sessionId)

    suspend fun getById(id: Long): V2AnalysisRecordEntity? = dao.getById(id)

    /** Per la scorciatoia "Registra peso vero" a fine analisi (§13 punto 3): true se questa sessione ha prodotto un record V2 ancora senza ground truth. */
    suspend fun hasPendingGroundTruth(sessionId: String): Boolean = dao.getPendingBySessionId(sessionId) != null

    /** Correzione manuale o peso vero pesato per uno o più alimenti di un'analisi già salvata. Nessun effetto se [recordId] non esiste più (es. cronologia già ripulita). */
    suspend fun recordGroundTruth(recordId: Long, pesiG: Map<String, Double>, livello: GroundTruthLevel) {
        val record = dao.getById(recordId) ?: return
        dao.update(
            record.copy(
                groundTruthPesiJson = json.encodeToString(pesiG),
                groundTruthLivello = livello.jsonValue,
            ),
        )
    }

    suspend fun deleteRecord(id: Long) {
        dao.deleteById(id)
    }
}
