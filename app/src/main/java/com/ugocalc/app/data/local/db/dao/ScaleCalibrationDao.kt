package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaleCalibrationDao {
    @Query("SELECT * FROM scale_calibrations WHERE sessionMode = :sessionMode")
    fun getAll(sessionMode: String): Flow<List<ScaleCalibrationEntity>>

    @Query("SELECT * FROM scale_calibrations")
    suspend fun getAllOnce(): List<ScaleCalibrationEntity>

    @Query("SELECT * FROM scale_calibrations WHERE type = :type AND sessionMode = :sessionMode")
    suspend fun getByType(type: String, sessionMode: String): List<ScaleCalibrationEntity>

    @Query("SELECT * FROM scale_calibrations WHERE id = :id")
    suspend fun getById(id: Long): ScaleCalibrationEntity?

    @Query("SELECT * FROM scale_calibrations WHERE clientId = :clientId")
    suspend fun getByClientId(clientId: String): ScaleCalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: ScaleCalibrationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(calibration: ScaleCalibrationEntity): Long

    @Query("DELETE FROM scale_calibrations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scale_calibrations")
    suspend fun deleteAll()
}
