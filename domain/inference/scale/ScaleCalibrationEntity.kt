package com.ugocalc.domain.inference.scale

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Persistent calibration for a scale anchor type.
 * type: identifier of the anchor (e.g., STOVIGLIA_CALIBRATA, CORPOREO, UNIVERSEL)
 * value: calibrated size in cm or proportion used by ScaleResolver.
 */
@Entity(tableName = "scale_calibrations")
data class ScaleCalibrationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis()
)
