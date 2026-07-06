package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaleCalibrationDao {
    @Query("SELECT * FROM scale_calibrations")
    fun getAll(): Flow<List<ScaleCalibrationEntity>>

    @Query("SELECT * FROM scale_calibrations WHERE type = :type")
    suspend fun getByType(type: String): List<ScaleCalibrationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: ScaleCalibrationEntity): Long

    @Query("DELETE FROM scale_calibrations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scale_calibrations")
    suspend fun deleteAll()
}
