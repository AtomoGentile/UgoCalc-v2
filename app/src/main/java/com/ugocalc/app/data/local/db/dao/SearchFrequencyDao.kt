package com.ugocalc.app.data.local.db.dao

import androidx.room.*
import com.ugocalc.app.data.local.db.entity.SearchFrequencyEntity

@Dao
interface SearchFrequencyDao {
    @Query("SELECT * FROM search_frequency WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: Long): SearchFrequencyEntity?

    /** Righe fissate per prime, poi per numero di ricerche, poi per ultima ricerca più recente. */
    @Query("SELECT * FROM search_frequency ORDER BY pinned DESC, searchCount DESC, lastSearchedAtEpochMillis DESC LIMIT :limit")
    suspend fun getMostSearched(limit: Int): List<SearchFrequencyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchFrequencyEntity)

    /** "Cancella ultime ricerche" (§ richiesta utente): tiene le voci fissate con lo spillo. */
    @Query("DELETE FROM search_frequency WHERE pinned = 0")
    suspend fun clearUnpinned()
}
