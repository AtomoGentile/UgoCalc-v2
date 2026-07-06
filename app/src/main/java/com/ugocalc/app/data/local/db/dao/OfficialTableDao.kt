package com.ugocalc.app.data.local.db.dao

import androidx.room.*
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficialTableDao {
    @Query("SELECT * FROM official_tables ORDER BY priorityOrder ASC")
    fun observeAll(): Flow<List<OfficialTableEntity>>

    @Query("SELECT COUNT(*) FROM official_tables")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM official_tables ORDER BY priorityOrder ASC")
    suspend fun getAllOrderedByPriority(): List<OfficialTableEntity>

    @Query("SELECT MAX(priorityOrder) FROM official_tables")
    suspend fun maxPriorityOrder(): Int?

    @Query("SELECT * FROM official_tables WHERE id = :id")
    suspend fun getById(id: Long): OfficialTableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OfficialTableEntity): Long

    @Update
    suspend fun update(entity: OfficialTableEntity)

    @Delete
    suspend fun delete(entity: OfficialTableEntity)

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            updatePriority(id, index)
        }
    }

    @Query("UPDATE official_tables SET priorityOrder = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    /** Sostituzione totale in un import di backup (§17): le voci normalizzate seguono a cascata (ON DELETE CASCADE). */
    @Query("DELETE FROM official_tables")
    suspend fun deleteAll()
}
