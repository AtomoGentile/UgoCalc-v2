package com.ugocalc.app.data.local.db.dao

import androidx.room.*
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisHistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY analyzedAtEpochMillis DESC")
    fun observeAll(): Flow<List<AnalysisHistoryEntity>>

    @Query("SELECT * FROM analysis_history WHERE sessionMode = :mode ORDER BY analyzedAtEpochMillis DESC")
    fun observeByMode(mode: String): Flow<List<AnalysisHistoryEntity>>

    /** Lettura una tantum (§ backup/esportazione): [observeAll] è un Flow, non adatto a un singolo export puntuale. */
    @Query("SELECT * FROM analysis_history ORDER BY analyzedAtEpochMillis DESC")
    suspend fun getAllOnce(): List<AnalysisHistoryEntity>

    @Query("SELECT * FROM analysis_history WHERE id = :id")
    suspend fun getById(id: Long): AnalysisHistoryEntity?

    @Query("SELECT * FROM analysis_history WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): AnalysisHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AnalysisHistoryEntity): Long

    @Query("DELETE FROM analysis_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analysis_history")
    suspend fun clearAll()
}
