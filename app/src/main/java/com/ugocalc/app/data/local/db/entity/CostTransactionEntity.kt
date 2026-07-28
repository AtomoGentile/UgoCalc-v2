package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracciamento dei costi per ogni transazione API (M2 – Vision API, M3/M4/M5 – V2 inference,
 * M6 – Matching, M7 – Nutrition) — base per il riepilogo delle spese (dashboard).
 */
@Entity(tableName = "cost_transactions")
data class CostTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionAtEpochMillis: Long,
    val stage: String, // "vision", "inference_v1", "inference_v2", "matching", "nutrition"
    val modelId: String,
    val costUsd: Double,
    val description: String, // es. "Vision API - 1 image processed", "Claude 3.5 Sonnet - 2 calls"
    val sessionId: String? = null, // link opzionale alla sessione di analisi
)
