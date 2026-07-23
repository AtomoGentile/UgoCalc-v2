package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Livelli di confidenza usati in tutti e tre i Passaggi AI (Visione, Matching).
 * I valori serializzati corrispondono esattamente a quelli richiesti nei prompt
 * ("alto" | "medio" | "basso"), per poter deserializzare la risposta del modello
 * direttamente in questo enum.
 */
@Serializable
enum class Confidence {
    @SerialName("alto") ALTO,
    @SerialName("medio") MEDIO,
    @SerialName("basso") BASSO;

    companion object {
        /** Soglia di default per il "livello di sicurezza" mostrato in UI (§2.2). */
        const val DEFAULT_THRESHOLD_PERCENT = 70

        fun toPercentEstimate(confidence: Confidence): Int = when (confidence) {
            ALTO -> 90
            MEDIO -> 65
            BASSO -> 35
        }

        fun fromScore(score: ConfidenceScore): Confidence = when {
            score.percent >= 85 -> ALTO
            score.percent >= 60 -> MEDIO
            else -> BASSO
        }
    }
}
