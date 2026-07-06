package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity

/**
 * M8/§8 – Fattore di correzione appreso per un segmento **esatto** (modalità di sessione, strategia
 * di stima, modello IA usato in M1, categoria alimentare). Chiave composta apposta: non esiste
 * alcuna riga di aggregazione cross-modello o cross-modalità (§8 "Nessuna mescolanza tra modelli,
 * mai — nemmeno come fallback"). Ricalcolata da [com.ugocalc.app.data.repository.BiasCorrectionRepository]
 * a ogni nuova correzione con ground truth salvata in M8, interamente senza intervento dell'owner.
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
