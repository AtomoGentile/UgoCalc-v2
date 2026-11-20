package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.sync.CloudSyncEventType
import com.ugocalc.app.sync.CloudSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScaleCalibrationRepository @Inject constructor(
    private val calibrationDao: ScaleCalibrationDao,
    private val cloudSyncManager: CloudSyncManager,
    private val cloudSyncClient: CloudSyncClient,
    private val json: Json,
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
        val entity = ScaleCalibrationEntity(
            sessionMode = sessionMode.name,
            type = type.name,
            name = name,
            valueCm = valueCm,
            widthCm = widthCm,
            heightCm = heightCm,
            capacityMl = capacityMl,
            imagePath = imagePath,
            clientId = UUID.randomUUID().toString(),
        )
        calibrationDao.insert(entity)
        sync(entity)
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
        val existing = calibrationDao.getById(id)
        val entity = ScaleCalibrationEntity(
            id = id,
            sessionMode = sessionMode.name,
            type = type.name,
            name = name,
            valueCm = valueCm,
            widthCm = widthCm,
            heightCm = heightCm,
            capacityMl = capacityMl,
            imagePath = imagePath,
            clientId = existing?.clientId ?: UUID.randomUUID().toString(),
        )
        calibrationDao.insert(entity)
        sync(entity)
    }

    private suspend fun sync(entity: ScaleCalibrationEntity) {
        try {
            cloudSyncManager.enqueue(
                eventType = CloudSyncEventType.CALIBRATION,
                payloadJson = json.encodeToString(entity)
            )
        } catch (_: Exception) {
            // Failure should not prevent local operation
        }
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
        // Note: we don't currently have a "delete" sync event, but the user requested 
        // to link them to the account, which usually means syncing the current state.
    }

    /** Scarica le calibrazioni dell'utente dal backend e fa upsert locale per clientId (idempotente). */
    suspend fun pullAndMerge(): Int {
        val payloads = cloudSyncClient.fetchCalibrationRecords()
        var inserted = 0
        for (payload in payloads) {
            try {
                val remote = json.decodeFromString<ScaleCalibrationEntity>(payload)
                if (remote.clientId == null) continue
                val existing = calibrationDao.getByClientId(remote.clientId)
                if (existing == null) {
                    calibrationDao.insertIfAbsent(remote.copy(id = 0))
                    inserted++
                }
            } catch (_: Exception) {
                // Skip malformed
            }
        }
        return inserted
    }
}
