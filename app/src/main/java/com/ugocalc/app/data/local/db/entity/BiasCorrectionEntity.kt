package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity

/**
 * M8/§8 – Fattore di correzione appreso per un segmento **esatto** (modalità di sessione, strategia
 * di stima, modello IA usato in M1, categoria alimentare — vedi [com.ugocalc.app.domain.inference.model.FoodCategory]).
 * Chiave composta apposta: non esiste alcuna riga di aggregazione cross-modello o cross-modalità
 * (§8 "Nessuna mescolanza tra modelli, mai — nemmeno come fallback"). La categoria alimentare è
 * stata aggiunta perché la sola strategia mescolava alimenti troppo diversi (es. pasta e riso,
 * entrambi `DISTESA_SUL_PIATTO`): un fattore tarato sulla pasta non deve applicarsi al riso senza
 * dati di riso. Ricalcolata da [com.ugocalc.app.data.repository.BiasCorrectionRepository] a ogni
 * nuova correzione con ground truth salvata in M8, interamente senza intervento dell'owner.
 */
@Entity(tableName = "bias_corrections", primaryKeys = ["sessionMode", "strategy", "aiModelId", "foodCategory"])
data class BiasCorrectionEntity(
    val sessionMode: String,
    val strategy: String,
    val aiModelId: String,
    val foodCategory: String,
    val factor: Double,
    val sampleCount: Int,
    val updatedAtEpochMillis: Long,
)
