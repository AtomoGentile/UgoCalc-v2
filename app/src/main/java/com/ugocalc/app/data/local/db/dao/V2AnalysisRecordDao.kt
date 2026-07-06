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
}
