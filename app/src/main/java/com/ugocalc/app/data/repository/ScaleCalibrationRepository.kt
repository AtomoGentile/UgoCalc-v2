package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScaleCalibrationRepository @Inject constructor(
    private val calibrationDao: ScaleCalibrationDao
) {
    fun observeAll(): Flow<List<ScaleCalibrationEntity>> = calibrationDao.getAll()

    suspend fun getByType(type: ScaleAnchorType): List<ScaleCalibrationEntity> =
        calibrationDao.getByType(type.name)

    suspend fun addCalibration(type: ScaleAnchorType, name: String, valueCm: Double, capacityMl: Double? = null) {
        calibrationDao.insert(
            ScaleCalibrationEntity(
                type = type.name,
                name = name,
                valueCm = valueCm,
                capacityMl = capacityMl
            )
        )
    }

    suspend fun deleteCalibration(id: Long) {
        calibrationDao.deleteById(id)
    }
}
