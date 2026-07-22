package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.PhysicalDataDao
import com.ugocalc.app.data.local.db.entity.PhysicalDataEntity
import com.ugocalc.app.data.local.db.entity.PhysicalDataType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalDataRepository @Inject constructor(
    private val physicalDataDao: PhysicalDataDao
) {
    suspend fun addDensity(nome: String, value: Double) {
        val entity = PhysicalDataEntity(
            type = PhysicalDataType.DENSITY,
            value = value,
            label = nome,
            keywords = nome,
            isCustom = true
        )
        physicalDataDao.insert(entity)
    }

    suspend fun getCustomDensities(): List<PhysicalDataEntity> {
        return physicalDataDao.getByType(PhysicalDataType.DENSITY)
    }
}
