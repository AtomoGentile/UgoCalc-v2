package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.model.ScaleAnchor
import com.ugocalc.app.domain.model.Confidence

/**
 * Ponte tra le ancore di scala percepite da M1 ([ScaleAnchor], nello [com.ugocalc.app.domain.inference.model.SceneGraph])
 * e lo [ScaleContext] richiesto da M3 per le formule "distesa sul piatto" (§5, §8).
 *
 * Convenzione (vedi [com.ugocalc.app.domain.inference.m1.PerceptionPrompt]): quando la scena
 * contiene un alimento a strategia "distesa", M1 riporta comunque il diametro del PIATTO in
 * `dimensione_reale_cm` dell'ancora scelta, anche quando l'ancora di partenza è un riferimento
 * indiretto (corporeo/universale) — è l'unico modo per cui `frazione_piatto` diventi un'area reale
 * a valle. Per questo il risolutore non filtra per `descrizione` (testo libero, inaffidabile da
 * interpretare), ma si fida del `dimensione_reale_cm` dell'ancora di priorità più alta disponibile.
 *
 * Priorità (§8, stesso ordine di [com.ugocalc.app.domain.inference.model.ScaleAnchorType]): stoviglia
 * calibrata > riferimento corporeo > oggetto universale > stima generica. A parità di tipo vince la
 * confidenza più alta. Se nessuna ancora porta una dimensione utilizzabile, si ripiega sul diametro
 * di default con qualità bassa (§9): mai falsa precisione.
 */
object ScaleResolver {

    fun resolve(ancore: List<ScaleAnchor>): ScaleContext {
        val migliore = ancore
            .filter { it.dimensioneRealeCm != null }
            .minWithOrNull(compareBy({ it.tipo.ordinal }, { confidencePriority(it.confidenza) }))
            ?: return ScaleContext()

        return ScaleContext(
            plateDiameterCm = migliore.dimensioneRealeCm!!,
            plateDiameterIsDefault = false,
            quality = migliore.confidenza,
        )
    }

    /** Ordina ALTO prima di MEDIO prima di BASSO (valore più basso = priorità più alta), per il tie-break a parità di tipo. */
    private fun confidencePriority(c: Confidence): Int = when (c) {
        Confidence.ALTO -> 0
        Confidence.MEDIO -> 1
        Confidence.BASSO -> 2
    }
}
