package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ConfidenceScore(val percent: Int) {
    init {
        require(percent in 0..100) { "ConfidenceScore deve essere tra 0 e 100, ricevuto: $percent" }
    }

    companion object {
        /** Soglia di default per l'allerta — sotto questo livello viene mostrato un avvertimento. */
        const val DEFAULT_ALERT_THRESHOLD_PERCENT = 60

        /** Banda colore per [percent]. */
        fun band(score: ConfidenceScore): ConfidenceBand = when {
            score.percent >= 85 -> ConfidenceBand.GREEN      // Stima solida
            score.percent >= 65 -> ConfidenceBand.AMBER      // Ragionevole
            score.percent >= 40 -> ConfidenceBand.ORANGE     // Incerta
            else -> ConfidenceBand.RED                       // Inaffidabile
        }

        /** Etichetta leggibile della banda colore. */
        fun bandLabel(score: ConfidenceScore): String = when (band(score)) {
            ConfidenceBand.GREEN -> "Solida"
            ConfidenceBand.AMBER -> "Ragionevole"
            ConfidenceBand.ORANGE -> "Incerta"
            ConfidenceBand.RED -> "Inaffidabile"
        }

        /** True se la confidenza è sotto la soglia di allerta. */
        fun needsAlert(score: ConfidenceScore, alertThreshold: Int = DEFAULT_ALERT_THRESHOLD_PERCENT): Boolean =
            score.percent < alertThreshold

        fun fromUncertainty(uncertainty: Double): ConfidenceScore {
            val percent = ((1.0 - uncertainty) * 100).toInt().coerceIn(0, 100)
            return ConfidenceScore(percent)
        }
    }
}

enum class ConfidenceBand {
    GREEN,    // 85–100%
    AMBER,    // 65–84%
    ORANGE,   // 40–64%
    RED       // 0–39%
}
