package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_shortcuts",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyEntryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("historyEntryId")]
)
data class FavoriteShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val historyEntryId: Long?,
    val label: String,
    val sortOrder: Int,
    /**
     * Combinazione standard "alimento + peso" (§ richiesta utente), indipendente da una voce di
     * cronologia: se valorizzati, [historyEntryId] resta null e questi tre campi bastano da soli a
     * calcolare CHO/grassi per il peso scelto, senza dipendere da una riga di tabella che potrebbe
     * essere modificata o cancellata in futuro.
     */
    val comboGrams: Double? = null,
    val comboChoPer100g: Double? = null,
    val comboGrassiPer100g: Double? = null,
)
