package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.inference.model.SessionMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScaleCalibrationRepository @Inject constructor(
    private val calibrationDao: ScaleCalibrationDao
) {
    fun observeAll(sessionMode: SessionMode): Flow<List<ScaleCalibrationEntity>> = 
        calibrationDao.getAll(sessionMode.name)

    suspend fun getByType(type: ScaleAnchorType, sessionMode: SessionMode): List<ScaleCalibrationEntity> =
        calibrationDao.getByType(type.name, sessionMode.name)

    suspend fun addCalibration(
        sessionMode: SessionMode,
        type: ScaleAnchorType,
        name: String,
        valueCm: Double,
        widthCm: Double? = null,
        heightCm: Double? = null,
        capacityMl: Double? = null,
        imagePath: String? = null,
    ) {
        calibrationDao.insert(
            ScaleCalibrationEntity(
                sessionMode = sessionMode.name,
                type = type.name,
                name = name,
                valueCm = valueCm,
                widthCm = widthCm,
                heightCm = heightCm,
                capacityMl = capacityMl,
                imagePath = imagePath
            )
        )
    }

    suspend fun updateCalibration(
        id: Long,
        sessionMode: SessionMode,
        type: ScaleAnchorType,
        name: String,
        valueCm: Double,
        widthCm: Double? = null,
        heightCm: Double? = null,
        capacityMl: Double? = null,
        imagePath: String? = null,
    ) {
        calibrationDao.insert(
            ScaleCalibrationEntity(
                id = id,
                sessionMode = sessionMode.name,
                type = type.name,
                name = name,
                valueCm = valueCm,
                widthCm = widthCm,
                heightCm = heightCm,
                capacityMl = capacityMl,
                imagePath = imagePath
            )
        )
    }

    suspend fun deleteCalibration(id: Long) {
        val entity = calibrationDao.getById(id)
        if (entity?.imagePath != null) {
            try {
                java.io.File(entity.imagePath).delete()
            } catch (e: Exception) {
                // Ignore failure to delete physical file
            }
        }
        calibrationDao.deleteById(id)
    }
}
