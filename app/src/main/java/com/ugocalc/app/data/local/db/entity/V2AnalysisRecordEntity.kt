package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * M8 – Telemetria e archivio di apprendimento (piano V2 §5, §7, §8). Una riga per ogni analisi V2
 * completata: copia integrale del percorso intermedio (SceneGraph, stime, verifica, nutrizione)
 * come JSON, più le versioni (prompt/modello/engine) e la modalità di sessione. È la base sia del
 * benchmark (§10) sia dei fattori di correzione appresi per (modalità, strategia, modello) (§8):
 * nessuna riga aggrega mai dati tra modelli diversi, per non applicare il bias di un modello ai
 * dati di un altro (§8 "Nessuna mescolanza tra modelli, mai").
 *
 * [groundTruthPesoG]/[groundTruthLivello] restano assenti finché l'utente non fornisce una
 * correzione manuale o un peso reale: sono il segnale da cui i fattori di correzione si calcolano,
 * mai popolati automaticamente.
 */
@Entity(tableName = "v2_analysis_records")
data class V2AnalysisRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyzedAtEpochMillis: Long,
    val sessionMode: String,
    val aiModelId: String,
    val schemaVersion: Int,
    val sceneGraphJson: String,
    val weightEstimatesJson: String,
    val verificationReportJson: String,
    val nutritionReportJson: String,
    val arbitratoMotivoMancato: String? = null,
    /** Id di sessione condiviso con `benchmark_meals` (§10, §13 punto 5): collega questo record V2 alla stima V1 fatta sulle stesse foto. Nullo per i record salvati prima dell'introduzione del benchmark, o se la pipeline gira fuori da una sessione tracciata. */
    val sessionId: String? = null,
    /** Peso reale in grammi per alimento, es. `{"riso in bianco": 170.0}`; nullo finché non corretto. */
    val groundTruthPesiJson: String? = null,
    /** Livello di affidabilità del ground truth: "gold" (bilancia), "silver" (etichetta), "bronze" (stima utente). */
    val groundTruthLivello: String? = null,
)
