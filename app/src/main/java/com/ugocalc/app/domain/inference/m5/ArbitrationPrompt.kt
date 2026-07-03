package com.ugocalc.app.domain.inference.m5

import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.SceneGraph

/**
 * Prompt di M5 – Arbitrato selettivo (piano V2 §4, §5). A differenza di M1, che percepisce l'intera
 * scena, questo prompt porta al modello SOLO gli alimenti flaggati da M4 e il motivo del flag: è la
 * "rianalisi mirata" richiesta dal piano, al costo minimo possibile. MAI grammi diretti (§2): anche
 * qui il modello propone identificazione e misure grezze riviste, non pesi.
 */
object ArbitrationPrompt {

    fun build(scene: SceneGraph, flaggati: List<FlaggedItem>): String {
        val foodByName = scene.alimenti.associateBy { it.nome }
        val casi = flaggati.joinToString("\n") { flag ->
            val food = foodByName[flag.nomeAlimento]
            val alternative = food?.alternativeIdentificazione?.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " (alternative già proposte: ", postfix = ")") ?: ""
            "- \"${flag.nomeAlimento}\"$alternative — motivo del dubbio: ${flag.motivo}"
        }
        return """
            Nella tua precedente analisi di questo pasto hai percepito degli alimenti, ma un
            controllo di coerenza a valle ha segnalato dei dubbi su alcuni di essi. Ti richiedo di
            osservare di nuovo SOLO le fotografie originali e rianalizzare ESCLUSIVAMENTE questi
            alimenti, con particolare attenzione al motivo del dubbio indicato:

            $casi

            Per ciascuno di questi alimenti, e SOLO per questi, restituisci una revisione: se riesci
            a sciogliere il dubbio (identificazione più certa, o misura più precisa osservando meglio
            la foto), fornisci il nome confermato e/o le misure grezze corrette e marca "risolto":
            true. Se dopo un'osservazione più attenta il dubbio resta, restituisci comunque la tua
            miglior stima con "risolto": false — non inventare certezza che non hai.

            Le stesse regole di M1 restano valide: NON stimare mai un peso in grammi, quello è
            compito di un motore di calcolo deterministico a valle. Le misure grezze seguono lo
            stesso formato già usato (frazione_piatto, altezza_cm, riempimento, conteggio,
            contenitore, frazione_riempimento, forma, lunghezza_cm, larghezza_cm, diametro_cm).

            Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con ESATTAMENTE questa
            struttura:

            {
              "schema_version": ${SceneGraph.SCHEMA_VERSION},
              "revisioni": [
                {
                  "nome": "string (il nome originale esatto dell'alimento flaggato, per farlo corrispondere)",
                  "nome_confermato": "string (opzionale, solo se l'identificazione cambia)",
                  "misure_grezze": {
                    "conteggio": number (opzionale),
                    "frazione_piatto": number (opzionale),
                    "altezza_cm": number (opzionale),
                    "riempimento": number (opzionale),
                    "contenitore": "string" (opzionale),
                    "frazione_riempimento": number (opzionale),
                    "forma": "string" (opzionale),
                    "lunghezza_cm": number (opzionale),
                    "larghezza_cm": number (opzionale),
                    "diametro_cm": number (opzionale),
                    "nota": "string (opzionale)"
                  } (omettere interamente se le misure non cambiano),
                  "confidenza": "alto" | "medio" | "basso",
                  "risolto": true | false
                }
              ]
            }
        """.trimIndent()
    }
}
