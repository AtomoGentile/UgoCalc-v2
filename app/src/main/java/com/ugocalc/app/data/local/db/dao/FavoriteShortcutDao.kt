package com.ugocalc.app.data.local.db.dao

import androidx.room.*
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteShortcutDao {
    @Query("SELECT * FROM favorite_shortcuts ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<FavoriteShortcutEntity>>

    /** Lettura una tantum (§ backup/esportazione): [observeAll] è un Flow, non adatto a un singolo export puntuale. */
    @Query("SELECT * FROM favorite_shortcuts ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<FavoriteShortcutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteShortcutEntity): Long

    @Update
    suspend fun update(entity: FavoriteShortcutEntity)

    @Delete
    suspend fun delete(entity: FavoriteShortcutEntity)

    /** Sostituzione totale in un import di backup (§17). */
    @Query("DELETE FROM favorite_shortcuts")
    suspend fun deleteAll()
}
