package com.ugocalc.app.domain.inference.model

import com.ugocalc.app.domain.model.Confidence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contratto dati M3 → M4 (piano V2 §7). È l'output del Motore fisico per UN alimento: il peso in
 * grammi calcolato deterministicamente, il suo intervallo di confidenza e la catena di calcolo
 * completa che lo rende spiegabile (§2, "ogni numero finale porta con sé la propria catena di
 * calcolo").
 *
 * Questo è il tipo che l'adattatore verso il resto dell'app (guida migrazione §3) trasformerà in
 * `RecognizedFood`, usando [CalculationChain.sintesi] come `commento`.
 */
@Serializable
data class WeightEstimate(
    val nome: String,
    /** Ragionamento visivo dell'IA ricevuto da M1 (§ richiesta utente). */
    val ragionamento: String = "",
    val strategia: EstimationStrategy,
    @SerialName("peso_g") val pesoG: Double,
    @SerialName("intervallo") val intervallo: ConfidenceInterval,
    @SerialName("catena_calcolo") val catenaCalcolo: CalculationChain,
    val confidenza: Confidence,
    /** Vero se il calcolo ha dovuto ripiegare su un valore di default (densità, peso unitario, capacità) perché non trovato in tabella (§ richiesta utente). */
    val usoDefaultFisico: Boolean = false,
)

/** Intervallo di confidenza sul peso, in grammi. Sempre mostrato, mai nascosto (§9). */
@Serializable
data class ConfidenceInterval(
    @SerialName("min_g") val minG: Double,
    @SerialName("max_g") val maxG: Double,
) {
    init {
        require(minG <= maxG) { "Intervallo non valido: minG ($minG) > maxG ($maxG)" }
    }

    /** Semiampiezza relativa al centro, utile come metrica diagnostica di incertezza. */
    val ampiezzaRelativa: Double
        get() = if (pesoCentrale == 0.0) 0.0 else (maxG - minG) / 2.0 / pesoCentrale

    private val pesoCentrale: Double get() = (minG + maxG) / 2.0
}

/**
 * Catena di calcolo completa di una stima (§2, §7 M3→M4): la formula applicata, i valori usati con
 * il loro nome, le fonti di ciascun fattore e una sintesi leggibile.
 */
@Serializable
data class CalculationChain(
    /** Formula simbolica applicata, es. "π·(d/2)²·h·riempimento·densità". */
    val formula: String,
    /** Valori numerici usati, per nome (es. "diametro_cm" → 27.0, "densita" → 0.85). */
    val valori: Map<String, Double>,
    /** Provenienza di ciascun fattore non geometrico (tabella densità, peso unitario, ancora…). */
    val fonti: List<CalculationSource>,
    /**
     * Sintesi leggibile della catena, destinata all'utente (§3 guida migrazione). Esempio:
     * "165 g — cerchio Ø27 cm, 40% piatto, densità riso cotto".
     */
    @SerialName("sintesi") val sintesi: String,
)

/** Provenienza di un singolo fattore usato nel calcolo, per la spiegabilità. */
@Serializable
data class CalculationSource(
    /** Nome del fattore, es. "densità" o "peso_unitario". */
    val fattore: String,
    val valore: Double,
    /** Da dove viene il valore, es. "tabella densità: riso cotto" o "ancora: Ø piatto calibrato". */
    val fonte: String,
)
