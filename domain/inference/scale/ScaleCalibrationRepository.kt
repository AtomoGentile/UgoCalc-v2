package com.ugocalc.domain.inference.scale

import com.ugocalc.data.dao.ScaleCalibrationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScaleCalibrationRepository(private val dao: ScaleCalibrationDao) {
    suspend fun saveCalibration(type: String, value: Double) = withContext(Dispatchers.IO) {
        dao.insert(ScaleCalibrationEntity(type = type, value = value))
    }

    suspend fun getActiveCalibration(type: String): ScaleCalibrationEntity? = withContext(Dispatchers.IO) {
        dao.getLatestByType(type)
    }
}
