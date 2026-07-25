package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodTitle: String,
    val analyzedAtEpochMillis: Long,
    val totalChoG: Double,
    val totalGrassiG: Double,
    val modelDisplayNamesJson: String,
    val detailsJson: String,
    /** Id di sessione condiviso con i record V2 e il benchmark (§ richiesta utente). */
    val sessionId: String? = null,
    /** Modalità in cui è avvenuta l'analisi; DEFAULT 'CASA' per i record precedenti alla migration 20→21. */
    val sessionMode: String = "CASA",
)
