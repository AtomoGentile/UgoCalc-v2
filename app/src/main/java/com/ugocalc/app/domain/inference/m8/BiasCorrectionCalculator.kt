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
    const val MIN_SAMPLE_COUNT = 5

    /** Frazione minima di osservazioni concordi sul segno dello scarto: disattivata per ora (§ richiesta utente). */
    const val MIN_SIGN_AGREEMENT = 0.0

    /**
     * [observations] deve contenere solo dati dello stesso identico segmento. Il fattore, quando
     * applicabile, è la **mediana** dei rapporti peso_vero/peso_stimato: robusta a un singolo
     * pasto anomalo, a differenza della media.
     */
    fun calculate(observations: List<BiasObservation>): BiasCorrectionResult {
        if (observations.size < MIN_SAMPLE_COUNT) {
            return BiasCorrectionResult.DatiInsufficienti(observations.size)
        }

        val ratios = observations.map { it.pesoVeroG / it.pesoStimatoG }
        val sovrastime = ratios.count { it > 1.0 }
        val sottostime = ratios.count { it < 1.0 }
        val concordanza = maxOf(sovrastime, sottostime).toDouble() / observations.size

        if (concordanza < MIN_SIGN_AGREEMENT) {
            return BiasCorrectionResult.BiasInstabile(observations.size, concordanza)
        }

        return BiasCorrectionResult.Applicabile(fattore = mediana(ratios), campione = observations.size)
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
sealed interface BiasCorrectionResult {
    data class Applicabile(val fattore: Double, val campione: Int) : BiasCorrectionResult
    data class DatiInsufficienti(val campione: Int) : BiasCorrectionResult
    data class BiasInstabile(val campione: Int, val concordanza: Double) : BiasCorrectionResult
}
