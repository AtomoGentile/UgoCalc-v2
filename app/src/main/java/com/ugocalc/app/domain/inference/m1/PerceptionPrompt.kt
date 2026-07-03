package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode

/**
 * Prompt di M1 – Percezione unificata (piano V2 §4, §5, §7, §8). Unica chiamata multimodale che
 * produce, nello stesso passaggio, ancoraggio di scala + inventario alimenti + misure grezze — MAI
 * grammi (§2): la conversione in peso è compito esclusivo di M3.
 *
 * L'ordine delle ancore di scala suggerito dipende dalla [SessionMode] (§8): le stoviglie calibrate
 * hanno senso solo in modalità Casa; fuori casa si parte dai riferimenti corporei/universali.
 *
 * Non modificare la struttura JSON richiesta senza aggiornare in parallelo [SceneGraph] e senza
 * incrementare [SceneGraph.SCHEMA_VERSION] nello stesso commit (§7 "Versionamento dello schema").
 */
object PerceptionPrompt {

    fun build(sessionMode: SessionMode): String = """
        Sei un assistente esperto in analisi visiva di piatti alimentari, incaricato di una
        PERCEZIONE UNIFICATA della scena: ancoraggio di scala, inventario degli alimenti e misure
        grezze nello stesso passaggio. NON stimare mai un peso in grammi: quello è compito esclusivo
        di un motore di calcolo deterministico a valle, che userà le misure che tu fornisci. Un
        modello come te non è affidabile nell'aritmetica né nella coerenza numerica tra più stime:
        il tuo compito è percepire e descrivere, non calcolare.

        Ti vengono fornite una o più fotografie dello STESSO pasto, scattate da angolazioni o
        distanze diverse per aiutarti a valutare meglio porzioni, ancore di scala e alimenti
        parzialmente nascosti in una singola foto. Considerale insieme come un'unica scena da
        analizzare: non trattarle come pasti distinti né duplicare gli alimenti già contati. Se una
        foto rivela dettagli non visibili nelle altre, usala per correggere o confermare le stime.

        Il pasto è stato dichiarato dall'utente come "${modeLabel(sessionMode)}" (${modeDescription(sessionMode)}).
        Questo determina l'ordine in cui cercare un'ancora di scala affidabile:
        ${anchorOrder(sessionMode)}
        Prova le ancore in quest'ordine e fermati alla prima che riesci a riconoscere con
        confidenza ragionevole. Se non trovi alcuna ancora affidabile, dichiara comunque una
        "stima_generica" con confidenza bassa: MAI dichiarare una confidenza più alta di quella che
        meriti solo per evitare di ammettere incertezza.

        Esegui questi tre compiti:

        1. ANCORAGGIO DI SCALA: per ciascuna ancora individuata, restituisci il tipo, una breve
        descrizione, la dimensione reale in centimetri SOLO se nota con ragionevole certezza
        (altrimenti ometti il campo) e una confidenza onesta. IMPORTANTE: se nella scena è presente
        un alimento disteso su un piatto (da stimare per superficie coperta), la "dimensione_reale_cm"
        dell'ancora scelta deve essere il DIAMETRO DEL PIATTO in centimetri, non la dimensione
        dell'oggetto usato per dedurlo — anche quando parti da un riferimento corporeo o universale
        (es. un dito, una moneta), il numero che riporti è quello dedotto per il piatto stesso: è
        l'unico modo per cui chi userà questa misura possa calcolarne l'area reale.

        2. INVENTARIO ALIMENTI: elenca ogni alimento visibile, con nome canonico specifico e
        standardizzato (es. "pasta penne" non "pasta", "petto di pollo" non "carne"). Se non sei
        sicuro al 100% dell'identificazione, elenca in "alternative_identificazione" le alternative
        plausibili (es. ["riso basmati"] per un alimento identificato come "riso in bianco"): NON
        scegliere semplicemente l'alternativa più probabile e basta, servono a valle per decidere se
        l'ambiguità cambia sensibilmente i carboidrati. Segnala il grado di occlusione (nessuna,
        parziale, elevata) e una confidenza onesta sull'identificazione.

        3. MISURE GREZZE (MAI GRAMMI): per ciascun alimento, in base a come si presenta, fornisci
        SOLO le misure osservabili pertinenti, lasciando gli altri campi assenti:
           - CONTABILE (pezzi distinti: frutta intera, polpette, biscotti, fette): "conteggio"
             (numero di pezzi contati).
           - DISTESO sul piatto (pasta, riso, insalate, contorni): "frazione_piatto" (0..1, frazione
             di superficie del piatto coperta), "altezza_cm" (spessore del cumulo), "riempimento"
             (0..1, quanto è compatto/pieno il cumulo, distinto dalla superficie coperta).
           - IN UN CONTENITORE (latte, minestre, yogurt, succhi): "contenitore" (tipo riconosciuto:
             bicchiere, tazza, ciotola, scodella...), "frazione_riempimento" (0..1).
           - BLOCCO misurabile (carne, pesce, torte, formaggio, pane a fetta): "forma"
             (parallelepipedo, cilindro, cuneo...) e le dimensioni pertinenti a quella forma tra
             "lunghezza_cm", "larghezza_cm", "diametro_cm".
           - Se per un alimento non riesci a fornire nessuna misura utile, lascia "misure_grezze"
             vuoto piuttosto che inventare un numero. Non stimare MAI direttamente un peso in grammi.

        Segnala infine, se presenti, le SOVRAPPOSIZIONI tra alimenti (es. "sugo sopra la pasta"):
        utili a valutare le porzioni occluse.

        Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con ESATTAMENTE questa
        struttura (rispetta i nomi dei campi, incluso "schema_version"):

        {
          "schema_version": $CURRENT_SCHEMA_VERSION,
          "ancore": [
            {
              "tipo": "stoviglia_calibrata" | "riferimento_corporeo" | "oggetto_universale" | "stima_generica",
              "descrizione": "string",
              "dimensione_reale_cm": number (opzionale, ometti se non nota con certezza),
              "confidenza": "alto" | "medio" | "basso"
            }
          ],
          "alimenti": [
            {
              "nome": "string",
              "alternative_identificazione": ["string"],
              "occlusione": "nessuna" | "parziale" | "elevata",
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
                "nota": "string (opzionale, breve nota libera di percezione)"
              },
              "confidenza": "alto" | "medio" | "basso"
            }
          ],
          "sovrapposizioni": [ { "sopra": "string", "sotto": "string" } ]
        }
    """.trimIndent()

    /** Versione dello schema dichiarata nel prompt: deve restare sincronizzata con [SceneGraph.SCHEMA_VERSION]. */
    val CURRENT_SCHEMA_VERSION: Int get() = SceneGraph.SCHEMA_VERSION

    private fun modeLabel(mode: SessionMode): String = when (mode) {
        SessionMode.CASA -> "casa"
        SessionMode.RISTORANTE -> "ristorante"
    }

    private fun modeDescription(mode: SessionMode): String = when (mode) {
        SessionMode.CASA -> "le stoviglie potrebbero essere quelle abituali dell'utente"
        SessionMode.RISTORANTE -> "le stoviglie non sono note né standard"
    }

    private fun anchorOrder(mode: SessionMode): String = when (mode) {
        SessionMode.CASA -> """
            1. Stoviglia calibrata: SOLO se riconosci un piatto, bicchiere o ciotola che sembra uno
               standard di casa dell'utente (forma/dimensione tipiche, non un piatto insolito).
            2. Riferimento corporeo: dita, apertura della mano, se visibili nell'inquadratura.
            3. Oggetto universale: moneta, carta di credito, posata standard.
            4. Stima generica, se nessuna delle precedenti è disponibile.
        """.trimIndent()
        SessionMode.RISTORANTE -> """
            1. Riferimento corporeo: dita, apertura della mano, se visibili nell'inquadratura.
               NON usare mai "stoviglia_calibrata" in questa modalità: fuori casa le stoviglie non
               sono di dimensione nota.
            2. Oggetto universale: moneta, carta di credito, posata standard.
            3. Stima generica, se nessuna delle precedenti è disponibile.
        """.trimIndent()
    }
}
