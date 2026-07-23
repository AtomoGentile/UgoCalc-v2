package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity

import kotlinx.coroutines.flow.Flow

/** M8 – accesso all'archivio di telemetria V2 (§5, §8). */
@Dao
interface V2AnalysisRecordDao {
    @Insert
    suspend fun insert(record: V2AnalysisRecordEntity): Long

    @Update
    suspend fun update(record: V2AnalysisRecordEntity)

    @Query("SELECT * FROM v2_analysis_records ORDER BY analyzedAtEpochMillis DESC")
    fun getAllFlow(): Flow<List<V2AnalysisRecordEntity>>

    @Query("SELECT * FROM v2_analysis_records ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getAll(): List<V2AnalysisRecordEntity>

    @Query("SELECT * FROM v2_analysis_records WHERE id = :id")
    suspend fun getById(id: Long): V2AnalysisRecordEntity?

    @Query("SELECT * FROM v2_analysis_records WHERE sessionId = :sessionId ORDER BY analyzedAtEpochMillis DESC LIMIT 1")
    suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity?

    /** Righe con ground truth: la base dei fattori di correzione appresi per (modalità, strategia, modello) (§8). */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL AND sessionMode = :sessionMode AND aiModelId = :aiModelId ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity>

    /** Tutte le righe con ground truth (§ segnalazione utente "stuttering fortissimo"): evita N query per ricalcolare tutte le statistiche della vista BIAS. */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL")
    fun getWithGroundTruthAllFlow(): Flow<List<V2AnalysisRecordEntity>>

    /** Tutte le righe con ground truth (§ segnalazione utente "stuttering fortissimo"): evita N query per ricalcolare tutte le statistiche della vista BIAS. */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL")
    suspend fun getWithGroundTruthAll(): List<V2AnalysisRecordEntity>

    /** Righe ancora senza ground truth: da mostrare all'utente per registrare un peso vero (§13 punto 3). I record falliti (failureReason NOT NULL) sono esclusi perché non hanno stime da correggere. */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NULL AND failureReason IS NULL ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity>

    /** Il record appena creato per [sessionId], se ancora senza ground truth: per la scorciatoia "Registra peso vero" a fine analisi. I record falliti sono esclusi. */
    @Query("SELECT * FROM v2_analysis_records WHERE sessionId = :sessionId AND groundTruthPesiJson IS NULL AND failureReason IS NULL ORDER BY analyzedAtEpochMillis DESC LIMIT 1")
    suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity?

    /** Coppie (modalità, modello) per cui esiste almeno un ground truth: base per il ricalcolo periodico di tutti i segmenti (§8, §13). */
    @Query("SELECT DISTINCT sessionMode, aiModelId FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL")
    suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair>

    /** Modelli per cui esiste almeno un ground truth, indipendentemente dalla modalità: base per il ricalcolo bias unificato (§8). */
    @Query("SELECT DISTINCT aiModelId FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL")
    suspend fun getAiModelIdsWithGroundTruth(): List<String>

    /** Righe con ground truth per un dato modello, senza filtro sulla modalità: per il ricalcolo bias unificato casa+ristorante (§8). */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL AND aiModelId = :aiModelId ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getWithGroundTruthByModel(aiModelId: String): List<V2AnalysisRecordEntity>

    /** Upsert per clientId: ignora il record se già presente (idempotente per pull cross-device). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(record: V2AnalysisRecordEntity): Long

    @Query("SELECT * FROM v2_analysis_records WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): V2AnalysisRecordEntity?

    @Query("DELETE FROM v2_analysis_records")
    suspend fun deleteAll()

    @Query("DELETE FROM v2_analysis_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Proiezione minima per [V2AnalysisRecordDao.getSessionModeAiModelPairsWithGroundTruth]. */
data class SessionModeAiModelPair(val sessionMode: String, val aiModelId: String)
