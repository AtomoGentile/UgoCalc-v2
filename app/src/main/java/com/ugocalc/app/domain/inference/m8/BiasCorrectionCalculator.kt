package com.ugocalc.app.domain.inference.m8

/**
 * Calibrazione automatica dei fattori di correzione (piano V2 §8): dato un insieme di coppie
 * (peso stimato da M3, peso vero da ground truth) per un segmento **esatto** (modalità, strategia,
 * modello M1), decide se il bias è abbastanza stabile da meritare un fattore correttivo e lo
 * calcola. Puramente deterministico: nessuna chiamata IA, nessuna decisione dell'owner nel loop —
 * è il "self-improvement" reale di §8, non l'Evolution Controller scartato in Parte 0.
 *
 * Il chiamante è responsabile di passare solo osservazioni già filtrate per l'esatto segmento
 * (§8 "Nessuna mescolanza tra modelli, mai — nemmeno come fallback"): questa classe non sa nulla
 * di modalità/strategia/modello, lavora solo sui numeri.
 */
object BiasCorrectionCalculator {

    /** Numero minimo di osservazioni prima di fidarsi di un bias (§8 "pattern stabili"). */
    const val MIN_SAMPLE_COUNT = 2

    /** Campione a partire dal quale il fattore usa la mediana piena quando il MAD è contenuto. */
    const val FULL_TRUST_SAMPLE_COUNT = 5

    /** Banda morta attorno a 1.0 (±5%): pasti già accurati non contraddicono la direzione del bias. */
    const val NEUTRAL_DEADBAND = 0.05

    /** Scala di riferimento MAD per la pesatura di stabilità su piccoli campioni. */
    const val MAD_SCALE = 0.15

    /** Soglia massima di MAD per applicare il fattore pieno senza smorzamento anche con `n >= 5`. */
    const val FULL_TRUST_MAX_MAD = 0.25

    /** Clamp di sicurezza per evitare fattori estremi sui piccoli campioni. */
    const val MIN_FACTOR = 0.5
    const val MAX_FACTOR = 2.0

    /** Frazione minima di osservazioni concordi sul segno dello scarto (§8 "pattern stabili"). */
    const val MIN_SIGN_AGREEMENT = 0.7

    /**
     * [observations] deve contenere solo dati dello stesso identico segmento. Il fattore, quando
     * applicabile, parte dalla **mediana** dei rapporti peso_vero/peso_stimato (robusta a un
     * singolo pasto anomalo):
     * - le osservazioni nella deadband `1.0 ± 0.05` non contraddicono la direzione prevalente;
     * - per campioni piccoli (`2 <= n < 5`) o alta dispersione (`MAD > 0.25`), il fattore è
     *   smorzato verso `1.0` in proporzione a `(n / 5.0) * stabilityFactor(MAD)` e clampato in `[0.5, 2.0]`.
     */
    fun calculate(observations: List<BiasObservation>): BiasCorrectionResult {
        val n = observations.size
        if (n < MIN_SAMPLE_COUNT) {
            return BiasCorrectionResult.DatiInsufficienti(n)
        }

        val ratios = observations.map { it.pesoVeroG / it.pesoStimatoG }
        val lowerNeutral = 1.0 - NEUTRAL_DEADBAND
        val upperNeutral = 1.0 + NEUTRAL_DEADBAND
        val sovrastime = ratios.count { it > upperNeutral }
        val sottostime = ratios.count { it < lowerNeutral }
        val neutre = n - sovrastime - sottostime
        val concordanza = maxOf(sovrastime + neutre, sottostime + neutre).toDouble() / n

        if (concordanza < MIN_SIGN_AGREEMENT) {
            return BiasCorrectionResult.BiasInstabile(n, concordanza)
        }

        val rawMedian = mediana(ratios)
        val mad = mediana(ratios.map { kotlin.math.abs(it - rawMedian) })
        val stabilityFactor = 1.0 / (1.0 + (mad / MAD_SCALE) * (mad / MAD_SCALE))
        val sampleWeight = (n.toDouble() / FULL_TRUST_SAMPLE_COUNT).coerceIn(0.0, 1.0)
        val weight = if (n >= FULL_TRUST_SAMPLE_COUNT && mad <= FULL_TRUST_MAX_MAD) {
            1.0
        } else {
            sampleWeight * stabilityFactor
        }
        val shrunk = 1.0 + (rawMedian - 1.0) * weight
        val clamped = shrunk.coerceIn(MIN_FACTOR, MAX_FACTOR)

        return BiasCorrectionResult.Applicabile(fattore = clamped, campione = n)
    }

    private fun mediana(valori: List<Double>): Double {
        val ordinati = valori.sorted()
        val meta = ordinati.size / 2
        return if (ordinati.size % 2 == 0) (ordinati[meta - 1] + ordinati[meta]) / 2.0 else ordinati[meta]
    }
}

/** Una singola osservazione: quanto M3 ha stimato contro il peso vero registrato come ground truth (§7). */
data class BiasObservation(val pesoStimatoG: Double, val pesoVeroG: Double) {
    init {
        require(pesoStimatoG > 0.0) { "pesoStimatoG deve essere positivo, ricevuto $pesoStimatoG" }
        require(pesoVeroG > 0.0) { "pesoVeroG deve essere positivo, ricevuto $pesoVeroG" }
    }
}

/** Esito del calcolo (§8): mai un fattore applicato su dati insufficienti o su un bias incoerente. */
@androidx.compose.runtime.Immutable
sealed interface BiasCorrectionResult {
    @androidx.compose.runtime.Immutable
    data class Applicabile(val fattore: Double, val campione: Int) : BiasCorrectionResult
    @androidx.compose.runtime.Immutable
    data class DatiInsufficienti(val campione: Int) : BiasCorrectionResult
    @androidx.compose.runtime.Immutable
    data class BiasInstabile(val campione: Int, val concordanza: Double) : BiasCorrectionResult
}
