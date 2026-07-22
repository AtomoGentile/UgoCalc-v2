package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity

import kotlinx.coroutines.flow.Flow

/** M8/§8 – accesso ai fattori di correzione appresi, uno per ciascun segmento (modalità, strategia, modello, categoria alimentare). */
@Dao
interface BiasCorrectionDao {
    @Query("SELECT * FROM bias_corrections WHERE sessionMode = :sessionMode AND strategy = :strategy AND aiModelId = :aiModelId AND foodCategory = :foodCategory")
    suspend fun get(sessionMode: String, strategy: String, aiModelId: String, foodCategory: String): BiasCorrectionEntity?

    /** Tutti i fattori appresi (§ backup/esportazione). */
    @Query("SELECT * FROM bias_corrections")
    fun getAllFlow(): Flow<List<BiasCorrectionEntity>>

    @Query("SELECT * FROM bias_corrections")
    suspend fun getAll(): List<BiasCorrectionEntity>

    /** Sostituzione totale in un import di backup (§17). */
    @Query("DELETE FROM bias_corrections")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BiasCorrectionEntity)
}
