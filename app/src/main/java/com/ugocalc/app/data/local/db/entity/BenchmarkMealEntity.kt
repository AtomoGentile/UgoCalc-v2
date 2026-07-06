package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Benchmark pesato V1 vs V2 (piano V2 §10, §13 punto 5): una riga per pasto, che collega la stima
 * grezza di V1 (pesi per alimento, prima del matching nutrizionale) e il record di telemetria V2
 * corrispondente ([v2RecordId], verso `v2_analysis_records`) tramite lo stesso [sessionId] generato
 * in `AnalysisSessionStore` al momento dello scatto. Il peso vero è registrato per alimento (non
 * solo il totale del pasto), così la dashboard di confronto può scomporre l'errore per strategia di
 * calcolo, non solo mostrare un numero aggregato.
 *
 * Salvata automaticamente (non su azione utente, a differenza di `analysis_history`): altrimenti un
 * pasto in cui l'utente non preme "Salva" in cronologia sparirebbe anche dal benchmark.
 */
@Entity(tableName = "benchmark_meals")
data class BenchmarkMealEntity(
    @PrimaryKey val sessionId: String,
    val capturedAtEpochMillis: Long,
    val sessionMode: String,
    val aiModelId: String,
    /** Pesi grezzi stimati da V1 per alimento, es. `{"riso in bianco": 165.0}`; null se V1 non ha completato l'analisi. */
    val v1PesiPerAlimentoJson: String?,
    /** CHO/100g e grassi/100g trovati dal matching V1 per alimento, popolati solo se l'utente arriva a "Ricevi i CHO" nella stessa sessione: servono a calcolare il CHO "vero" senza richiamare l'AI di abbinamento nella dashboard. */
    val v1NutrizionePerAlimentoJson: String? = null,
    /** Riferimento al record M8 completo (SceneGraph, WeightEstimate, NutritionReport) in `v2_analysis_records`, valorizzato quando la pipeline V2 in ombra completa sulla stessa sessione. */
    val v2RecordId: Long? = null,
    /** Peso vero in grammi per alimento, registrato dall'utente con la bilancia (o altra fonte dichiarata da [groundTruthLivello]). */
    val groundTruthPesiJson: String? = null,
    val groundTruthLivello: String? = null,
)
