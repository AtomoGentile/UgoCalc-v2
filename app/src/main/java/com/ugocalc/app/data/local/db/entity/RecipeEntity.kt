package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ricetta per alimento composto (piano V2 §6, §13 punto 6): l'utente definisce una lista di
 * ingredienti con le proporzioni percentuali. Quando il motore V2 incontra un alimento con
 * strategia COMPOSTO_OCCLUSO e trova una ricetta per quel nome, il peso totale geometrico viene
 * ripartito tra gli ingredienti, producendo una WeightEstimate separata per ognuno.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
)
