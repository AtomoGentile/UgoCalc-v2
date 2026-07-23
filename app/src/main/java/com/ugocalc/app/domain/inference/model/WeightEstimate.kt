package com.ugocalc.app.domain.inference.model

import com.ugocalc.app.domain.model.ConfidenceScore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// Vecchi record nel DB salvavano confidenza come stringa "alto"/"medio"/"basso".
// Nuovi record la salvano come Int (ConfidenceScore inline value class).
private object LegacyConfidenceScoreSerializer : KSerializer<ConfidenceScore> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ConfidenceScore", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ConfidenceScore {
        val jsonDecoder = decoder as? JsonDecoder
        val element = jsonDecoder?.decodeJsonElement() as? JsonPrimitive
        if (element != null) {
            element.intOrNull?.let { return ConfidenceScore(it.coerceIn(0, 100)) }
            return when (element.content) {
                "alto" -> ConfidenceScore(90)
                "medio" -> ConfidenceScore(65)
                else -> ConfidenceScore(35)
            }
        }
        return ConfidenceScore(decoder.decodeInt().coerceIn(0, 100))
    }

    override fun serialize(encoder: Encoder, value: ConfidenceScore) {
        encoder.encodeInt(value.percent)
    }
}

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
    /** Peso cotto/pronto come visibile nella foto (grammi). Base per la geometria e per il guardrail AI. */
    @SerialName("peso_g") val pesoG: Double,
    /**
     * Peso equivalente a crudo (grammi), calcolato dividendo pesoG per la resa di cottura
     * (§ piano crudo/cotto). Questo è il valore confrontabile col ground truth dell'utente e
     * con le tabelle nutrizionali su base cruda (CREA). Se la resa è 1.0 (frutta, pane, formaggio…)
     * coincide con pesoG. Default = pesoG per retrocompatibilità con record DB precedenti al piano
     * crudo/cotto (campo assente in JSON → resa 1.0 assunta).
     */
    @SerialName("peso_crudo_g") val pesoCrudoG: Double = pesoG,
    @SerialName("intervallo") val intervallo: ConfidenceInterval,
    @SerialName("catena_calcolo") val catenaCalcolo: CalculationChain,
    @Serializable(with = LegacyConfidenceScoreSerializer::class) val confidenza: ConfidenceScore,
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
