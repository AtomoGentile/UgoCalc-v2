package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.m0.CameraMetadata
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
 * Se l'utente ha calibrazioni persistenti (§13 punto 4), queste vengono iniettate nel prompt per
 * permettere a M1 di riconoscerle e restituire la dimensione reale corretta.
 *
 * Non modificare la struttura JSON richiesta senza aggiornare in parallelo [SceneGraph] e senza
 * incrementare [SceneGraph.SCHEMA_VERSION] nello stesso commit (§7 "Versionamento dello schema").
 */
object PerceptionPrompt {

    fun build(
        sessionMode: SessionMode,
        angleDescriptions: List<String?> = emptyList(),
        cameraMetadatas: List<CameraMetadata?> = emptyList(),
        calibrations: List<ScaleCalibrationEntity> = emptyList(),
        pastoImageCount: Int = 0,
    ): String = """
        Sei un assistente esperto in analisi visiva di piatti alimentari, incaricato di una
        PERCEZIONE UNIFICATA della scena: ancoraggio di scala, inventario degli alimenti e misure
        grezze nello stesso passaggio. NON stimare mai un peso in grammi: quello è compito esclusivo
        di un motore di calcolo deterministico a valle, che userà le misure che tu fornisci. Un
        modello come te non è affidabile nell'aritmetica né nella coerenza numerica tra più stime:
        il tuo compito è percepire e descrivere, non calcolare.

        Ti vengono fornite ${if (pastoImageCount > 0) "$pastoImageCount foto" else "una o più foto"} dello STESSO pasto, scattate seguendo SEMPRE questo
        protocollo fisso (non casuale, puoi contarci): la PRIMA foto è ripresa dall'alto, con il
        telefono pressoché parallelo al piatto (inclinazione ~90°) — usala come riferimento
        principale per la forma del piatto e per "frazione_piatto", perché da questa angolazione la
        superficie non è distorta da prospettiva. Le foto SUCCESSIVE (se presenti, fino a 4) sono
        scattate a ~45° di inclinazione, ciascuna ruotata di circa 90° intorno al piatto rispetto
        alla precedente: usale soprattutto per "altezza_cm", "riempimento" e la forma dei BLOCCHI
        misurabili, perché da un'angolazione più bassa il profilo laterale del cibo è visibile,
        cosa che la foto dall'alto non mostra. Il giro di 90° tra una foto e l'altra serve anche a
        ridurre le occlusioni: se un alimento è nascosto in una vista, cerca prima nelle foto
        successive (vista da un lato diverso del piatto) prima di dichiararlo non misurabile.
        Considerale insieme come un'unica scena da analizzare: non trattarle come pasti distinti né
        duplicare gli alimenti già contati.
        ${angleReadingsBlock(angleDescriptions, cameraMetadatas)}
        ${calibrationsBlock(calibrations, pastoImageCount)}

        IMMAGINI DI RIFERIMENTO: Oltre alle foto del pasto, ti sono state fornite delle immagini di
        riferimento per le calibrazioni dell'utente elencate sopra (se presenti). Usale per fare
        matching visivo: se un oggetto nella scena del pasto ha lo stesso aspetto (forma, colore,
        pattern) di una di queste immagini di calibrazione, puoi essere certo che si tratti della
        stessa stoviglia o dello stesso riferimento corporeo, e devi applicare la dimensione reale
        associata con confidenza "alto".

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

        4. LIMITI DI BUON SENSO (INTUIZIONE): Per ciascun alimento, fornisci un range di peso
        plausibile (min/max in grammi) basandoti solo sulla tua intuizione statistica di modello
        (quello che faresti in una stima "a occhio"). Questi valori NON verranno usati per il
        calcolo finale, ma solo come "guardrail" per identificare errori macroscopici nelle misure
        geometriche fornite al punto 3. Sii onesto: se un peperoncino non può pesare 100g, il suo
        "peso_max_plausibile_g" deve essere ragionevolmente basso (es. 15g).

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
              "limiti_buon_senso": {
                "peso_min_plausibile_g": number,
                "peso_max_plausibile_g": number
              },
              "confidenza": "alto" | "medio" | "basso"
            }
          ],
          "sovrapposizioni": [ { "sopra": "string", "sotto": "string" } ]
        }
    """.trimIndent()

    /** Versione dello schema dichiarata nel prompt: deve restare sincronizzata con [SceneGraph.SCHEMA_VERSION]. */
    val CURRENT_SCHEMA_VERSION: Int get() = SceneGraph.SCHEMA_VERSION

    /**
     * Angolazione REALMENTE misurata dai sensori per ciascuna foto (§13 punto 7), se disponibile:
     * non solo il protocollo generico dichiarato sopra, ma il valore letto durante quello scatto
     * specifico. Include anche i metadati hardware della camera (focale 35mm eq, sensore) per
     * aiutare l'IA a interpretare le proporzioni e le distanze.
     */
    private fun angleReadingsBlock(
        angleDescriptions: List<String?>,
        cameraMetadatas: List<CameraMetadata?>,
    ): String {
        if (angleDescriptions.none { it != null } && cameraMetadatas.none { it != null }) return ""
        val righe = angleDescriptions.indices.map { index ->
            val angle = angleDescriptions.getOrNull(index) ?: "angolazione non misurata"
            val meta = cameraMetadatas.getOrNull(index)
            val hardware = if (meta != null) {
                val focal = meta.focalLength35mmEq?.let { "${it.toInt()}mm eq." } ?: "focale non nota"
                val sensor = if (meta.sensorWidthMm != null && meta.sensorHeightMm != null)
                    " (sensore ${"%.1f".format(meta.sensorWidthMm)}x${"%.1f".format(meta.sensorHeightMm)}mm)"
                else ""
                " [Hardware: $focal$sensor]"
            } else ""
            "Foto ${index + 1}: $angle$hardware"
        }
        return "\nAngolazione e metadati hardware misurati per ciascuna foto:\n" +
            righe.joinToString("\n")
    }

    private fun modeLabel(mode: SessionMode): String = when (mode) {
        SessionMode.CASA -> "casa"
        SessionMode.RISTORANTE -> "ristorante"
    }

    private fun modeDescription(mode: SessionMode): String = when (mode) {
        SessionMode.CASA -> "le stoviglie potrebbero essere quelle abituali dell'utente"
        SessionMode.RISTORANTE -> "le stoviglie non sono note né standard"
    }

    /**
     * Calibrazioni persistenti dell'utente (§13 punto 4): se M1 riconosce uno di questi oggetti,
     * deve usare il nome esatto in "descrizione" e la dimensione reale fornita qui.
     */
    private fun calibrationsBlock(calibrations: List<ScaleCalibrationEntity>, pastoImageCount: Int): String {
        if (calibrations.isEmpty()) return ""
        val righe = calibrations.mapIndexed { index, cal ->
            val imageRef = if (cal.imagePath != null) " (vedi Immagine di riferimento ${pastoImageCount + index + 1})" else ""
            "- '${cal.name}' (${cal.type})$imageRef: dimensione reale ${cal.valueCm} cm" + (cal.capacityMl?.let { ml -> ", capacità $ml ml" } ?: "")
        }
        return "\nL'utente ha già calibrato i seguenti oggetti di riferimento. Se ne riconosci uno, usa il suo nome esatto nel campo 'descrizione' e riporta il valore di centimetri indicato:\n" +
            righe.joinToString("\n")
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
