package com.ugocalc.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.domain.inference.scale.ScaleCalibrationEntity

@Dao
interface ScaleCalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: ScaleCalibrationEntity)

    @Query("SELECT * FROM scale_calibrations WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByType(type: String): ScaleCalibrationEntity?

    @Query("SELECT * FROM scale_calibrations")
    suspend fun getAll(): List<ScaleCalibrationEntity>
}
