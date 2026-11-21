package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "normalized_food_entries",
    foreignKeys = [
        ForeignKey(
            entity = OfficialTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tableId")]
)
data class NormalizedFoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val nome: String,
    val choPer100g: Double,
    val grassiPer100g: Double,
    val rowOrder: Int,
    val isManuallyEdited: Boolean = false,
    val proteinePer100g: Double = 0.0,
    val indiceGlicemico: Int? = null,
)
