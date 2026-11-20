package com.ugocalc.app.data.local.db.dao

import androidx.room.*
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NormalizedFoodEntryDao {
    @Query("SELECT * FROM normalized_food_entries WHERE tableId = :tableId ORDER BY rowOrder ASC")
    fun observeForTable(tableId: Long): Flow<List<NormalizedFoodEntryEntity>>

    @Query("SELECT * FROM normalized_food_entries WHERE tableId = :tableId ORDER BY rowOrder ASC")
    suspend fun getForTable(tableId: Long): List<NormalizedFoodEntryEntity>

    @Query("DELETE FROM normalized_food_entries WHERE tableId = :tableId")
    suspend fun deleteAllForTable(tableId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NormalizedFoodEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NormalizedFoodEntryEntity): Long

    @Update
    suspend fun update(entity: NormalizedFoodEntryEntity)

    @Delete
    suspend fun delete(entity: NormalizedFoodEntryEntity)

    @Query("SELECT MAX(rowOrder) FROM normalized_food_entries WHERE tableId = :tableId")
    suspend fun maxRowOrder(tableId: Long): Int?

    @Query("SELECT * FROM normalized_food_entries WHERE id = :id")
    suspend fun getById(id: Long): NormalizedFoodEntryEntity?

    /** Ricerca manuale (§ ricerca tra i cibi del database): su tutte le tabelle, non solo una. */
    @Query("SELECT * FROM normalized_food_entries WHERE LOWER(nome) LIKE '%' || LOWER(:query) || '%' ORDER BY nome ASC LIMIT 50")
    suspend fun searchByName(query: String): List<NormalizedFoodEntryEntity>
}
