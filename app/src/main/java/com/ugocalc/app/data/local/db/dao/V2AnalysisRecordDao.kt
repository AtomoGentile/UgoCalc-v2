package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity

/** M8 – accesso all'archivio di telemetria V2 (§5, §8). */
@Dao
interface V2AnalysisRecordDao {
    @Insert
    suspend fun insert(record: V2AnalysisRecordEntity): Long

    @Update
    suspend fun update(record: V2AnalysisRecordEntity)

    @Query("SELECT * FROM v2_analysis_records ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getAll(): List<V2AnalysisRecordEntity>

    @Query("SELECT * FROM v2_analysis_records WHERE id = :id")
    suspend fun getById(id: Long): V2AnalysisRecordEntity?

    /** Righe con ground truth: la base dei fattori di correzione appresi per (modalità, strategia, modello) (§8). */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL AND sessionMode = :sessionMode AND aiModelId = :aiModelId ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity>

    /** Righe ancora senza ground truth: da mostrare all'utente per registrare un peso vero (§13 punto 3). */
    @Query("SELECT * FROM v2_analysis_records WHERE groundTruthPesiJson IS NULL ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity>

    /** Il record appena creato per [sessionId], se ancora senza ground truth: per la scorciatoia "Registra peso vero" a fine analisi. */
    @Query("SELECT * FROM v2_analysis_records WHERE sessionId = :sessionId AND groundTruthPesiJson IS NULL ORDER BY analyzedAtEpochMillis DESC LIMIT 1")
    suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity?

    /** Coppie (modalità, modello) per cui esiste almeno un ground truth: base per il ricalcolo periodico di tutti i segmenti (§8, §13). */
    @Query("SELECT DISTINCT sessionMode, aiModelId FROM v2_analysis_records WHERE groundTruthPesiJson IS NOT NULL")
    suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair>

    @Query("DELETE FROM v2_analysis_records")
    suspend fun deleteAll()
}

/** Proiezione minima per [V2AnalysisRecordDao.getSessionModeAiModelPairsWithGroundTruth]. */
data class SessionModeAiModelPair(val sessionMode: String, val aiModelId: String)
