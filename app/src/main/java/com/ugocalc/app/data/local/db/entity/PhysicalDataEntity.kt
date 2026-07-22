package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PhysicalDataType { DENSITY, UNIT_WEIGHT, CAPACITY }

@Entity(tableName = "physical_data")
data class PhysicalDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PhysicalDataType,
    val value: Double,
    val label: String,
    val keywords: String, // Comma separated for simplicity, or JSON
    val isCustom: Boolean = true
)
