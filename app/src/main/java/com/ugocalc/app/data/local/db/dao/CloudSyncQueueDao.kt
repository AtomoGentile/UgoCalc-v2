package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity

@Dao
interface CloudSyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(event: CloudSyncQueueEntity)

    @Query("SELECT * FROM cloud_sync_queue WHERE syncedAtEpochMillis IS NULL ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<CloudSyncQueueEntity>

    @Update
    suspend fun update(event: CloudSyncQueueEntity)

    @Query("UPDATE cloud_sync_queue SET syncedAtEpochMillis = :syncedAt WHERE eventId = :eventId")
    suspend fun markSynced(eventId: String, syncedAt: Long)

    @Query("DELETE FROM cloud_sync_queue WHERE syncedAtEpochMillis IS NOT NULL AND syncedAtEpochMillis < :beforeEpochMillis")
    suspend fun pruneOldSynced(beforeEpochMillis: Long)

    @Query("DELETE FROM cloud_sync_queue WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)
}
