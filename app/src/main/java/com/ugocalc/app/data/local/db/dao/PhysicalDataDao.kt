package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.PhysicalDataEntity
import com.ugocalc.app.data.local.db.entity.PhysicalDataType

@Dao
interface PhysicalDataDao {
    @Query("SELECT * FROM physical_data")
    suspend fun getAll(): List<PhysicalDataEntity>

    @Query("SELECT * FROM physical_data WHERE type = :type")
    suspend fun getByType(type: PhysicalDataType): List<PhysicalDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PhysicalDataEntity): Long

    @Query("DELETE FROM physical_data WHERE id = :id")
    suspend fun deleteById(id: Long)
}
