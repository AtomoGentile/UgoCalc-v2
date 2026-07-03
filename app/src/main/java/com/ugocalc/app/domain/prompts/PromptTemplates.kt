package com.ugocalc.app.domain.prompts

/**
 * Prompt testuali dei 3 Passaggi AI, riportati così come definiti nel documento
 * "UgoCalc — Metodo di Ragionamento" (§3, §4, §5). Non modificare la struttura JSON richiesta
 * senza aggiornare in parallelo i DTO di parsing in `data.remote.ai`.
 */
object PromptTemplates {

    val VISION_PROMPT = """
        Sei un assistente esperto in analisi visiva di piatti alimentari.

        Ti vengono fornite una o più fotografie dello STESSO piatto, scattate da
        angolazioni o distanze diverse per aiutarti a valutare meglio porzioni e
        alimenti parzialmente nascosti in una singola foto. Considerale insieme come
        un'unica scena da analizzare: non trattarle come piatti distinti né
        duplicare gli alimenti già contati. Se una foto rivela dettagli non visibili
        nelle altre (es. un ingrediente coperto in un'inquadratura ma visibile in
        un'altra), usala per correggere o confermare le stime.

        Esegui questi passaggi:

        1. IDENTIFICAZIONE: elenca tutti gli alimenti visibili nel piatto, inclusi
        condimenti, salse o componenti parzialmente nascosti. Usa nomi specifici e
        standardizzati quando possibile (es. "pasta penne" invece di "pasta", "petto
        di pollo" invece di "carne").

        2. STIMA PORZIONI: per ciascun alimento identificato, stima il peso
        approssimativo in grammi, basandoti su riferimenti visivi nell'immagine
        (dimensione del piatto, posate, confronto tra gli ingredienti).

        3. COMMENTO: per ciascun alimento, scrivi una breve nota visiva che spieghi
        su cosa si basa la stima (es. "porzione media, confrontata con il diametro
        del piatto" oppure "parzialmente coperta dal sugo, stima per difetto").

        4. CONFIDENZA PER ALIMENTO: assegna un livello (alto/medio/basso) seguendo
        questi criteri. La confidenza NON riguarda solo quanto l'alimento è visibile
        nella foto, ma quanto sei DAVVERO sicuro che identificazione E peso stimato
        siano entrambi corretti — un alimento perfettamente visibile e ben illuminato
        può comunque avere un peso incerto (angolazione insolita, piatto non
        standard, forma irregolare difficile da tradurre in grammi) o
        un'identificazione ambigua (potrebbe essere uno tra due alimenti simili): in
        questi casi NON assegnare "alto" solo perché l'immagine è nitida.
           - ALTO: sei sicuro sia dell'identificazione sia della stima del peso —
        alimento chiaramente visibile, non sovrapposto, riferimento di scala chiaro
        (piatto o posate di dimensione nota) che rende la stima affidabile, forma
        regolare o comunque facile da tradurre in grammi, nessuna alternativa di
        identificazione plausibile.
           - MEDIO: identificazione ragionevolmente sicura ma peso stimato con un
        margine di incertezza (forma irregolare, riferimento di scala poco chiaro,
        porzione parzialmente coperta), oppure esiste un'alternativa di
        identificazione plausibile ma meno probabile di quella scelta.
           - BASSO: alimento in gran parte nascosto o sovrapposto ad altri
        ingredienti, forma non chiaramente distinguibile, fortemente miscelato in una
        preparazione composita (es. ripieno, sugo denso), immagine sfocata/poco
        illuminata in quel punto, identificazione incerta tra più alternative
        plausibili, o nessun riferimento di scala utilizzabile per la stima del peso.

        Nel dubbio tra due livelli, scegli sempre il più basso dei due: un "medio"
        dichiarato onestamente è più utile di un "alto" ottimistico su una stima che
        potrebbe rivelarsi sbagliata.

        5. CONFIDENZA GLOBALE: calcola il livello complessivo come segue:
           - ALTO: tutti gli alimenti hanno confidenza alta, oppure massimo un
        alimento secondario (non principale) ha confidenza media.
           - MEDIO: almeno un alimento principale ha confidenza media, nessuno ha
        confidenza bassa.
           - BASSO: almeno un alimento (principale o secondario) ha confidenza bassa.

        6. FATTORI DI INCERTEZZA: elenca in modo esplicito cosa ha ridotto la
        confidenza (es. "salsa non identificabile con certezza", "porzione di riso
        parzialmente coperta da pollo").

        Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con questa
        struttura:

        {
          "alimenti": [
            {
              "nome": "string",
              "peso_stimato_g": number,
              "commento": "string",
              "confidenza": "alto" | "medio" | "basso"
            }
          ],
          "confidenza_globale": "alto" | "medio" | "basso",
          "fattori_incertezza": ["string"],
          "avvertenze": "string"
        }
    """.trimIndent()

    val NORMALIZATION_PROMPT_HEADER = """
        Sei un assistente esperto in normalizzazione di dati nutrizionali.

        Riceverai il contenuto testuale estratto da un file di valori nutrizionali
        ufficiali (tabella alimenti). Il formato della tabella originale può variare:
        intestazioni diverse, colonne in ordine differente, unità di misura non
        uniformi (es. valori per 100g, per porzione, in kcal invece che in grammi di
        macronutriente).

        Esegui questi passaggi:

        1. INTERPRETAZIONE: identifica quali colonne corrispondono a nome alimento,
        contenuto di carboidrati e contenuto di grassi. Se i valori non sono espressi
        per 100g, convertili a tale base quando possibile; se non è possibile la
        conversione, segnalalo.

        2. NORMALIZZAZIONE NOMI: standardizza il nome di ciascun alimento mantenendo
        informazioni utili al riconoscimento (es. "Pasta di semola, secca, cruda"
        invece di abbreviazioni o codici interni della tabella). Se il nome nella
        tabella originale è in una lingua diversa dall'italiano, TRADUCILO in
        italiano (mantenendo il significato nutrizionale/culinario esatto, non una
        traduzione letterale che perda senso) prima di normalizzarlo: l'abbinamento
        del Passaggio 3 confronta nomi in italiano con quelli riconosciuti nella
        foto, quindi un nome lasciato in lingua originale non verrebbe mai abbinato.

        3. VALIDAZIONE: per ciascuna riga, verifica che i valori numerici siano
        plausibili (es. CHO o grassi non superiori a 100g per 100g di prodotto).
        Segnala le righe scartate o incerte con il motivo.

        4. RIGHE NON INTERPRETABILI: se alcune righe della tabella non sono
        interpretabili con sufficiente certezza (dati mancanti, formato ambiguo,
        valori incoerenti), NON includerle nell'output e segnalale separatamente.

        Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con questa
        struttura:

        {
          "alimenti": [
            {
              "nome": "string",
              "cho_per_100g": number,
              "grassi_per_100g": number
            }
          ],
          "righe_scartate": [
            {
              "contenuto_originale": "string",
              "motivo": "string"
            }
          ],
          "note_conversione": "string"
        }

        Contenuto estratto dal file da normalizzare:
    """.trimIndent()

    fun normalizationPrompt(extractedTableText: String): String =
        "$NORMALIZATION_PROMPT_HEADER\n\n$extractedTableText"

    private val MATCHING_PROMPT_TEMPLATE = """
        Sei un assistente esperto in abbinamento di alimenti a voci di tabelle
        nutrizionali.

        Riceverai:
        1. Una lista di alimenti riconosciuti in una foto, con il relativo peso
        stimato in grammi.
        2. Una tabella di voci nutrizionali ufficiali disponibili, ciascuna con un
        nome standardizzato.

        Per CIASCUN alimento riconosciuto, in modo INDIPENDENTE dagli altri (non
        confrontare o influenzare un abbinamento sulla base degli altri alimenti
        della lista):
        1. Cerca nella tabella la voce che corrisponde più plausibilmente
        all'alimento, considerando sinonimi, variazioni di preparazione e
        nomenclatura (es. "pasta penne" può corrispondere a "Pasta di semola, secca,
        cruda").
        2. Se esiste una corrispondenza ragionevole, restituiscila.
        3. Se NESSUNA voce della tabella corrisponde con ragionevole certezza,
        restituisci "nessuna_corrispondenza": true e NON forzare un abbinamento
        approssimativo.
        4. Assegna un livello di confidenza al singolo abbinamento (alto/medio/basso)
        in base a quanto il nome riconosciuto coincide semanticamente con la voce di
        tabella scelta:
           - ALTO: corrispondenza diretta o quasi letterale, nessun'altra voce della
        tabella è una candidata plausibile.
           - MEDIO: corrispondenza per sinonimo o variazione di preparazione
        ragionevole, oppure esiste un'altra voce meno probabile ma comunque
        plausibile.
           - BASSO: corrispondenza indovinata per somiglianza generica, con più voci
        della tabella ugualmente plausibili tra cui scegliere.
        Nel dubbio tra due livelli, scegli sempre il più basso: un abbinamento
        segnalato come incerto è più utile di uno etichettato "alto" per errore.

        Lista alimenti riconosciuti:
        {{LISTA_ALIMENTI_JSON}}

        Tabella voci disponibili:
        {{TABELLA_NORMALIZZATA_JSON}}

        Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con questa
        struttura:

        {
          "abbinamenti": [
            {
              "nome_riconosciuto": "string",
              "voce_tabella_abbinata": "string",
              "nessuna_corrispondenza": boolean,
              "confidenza_abbinamento": "alto" | "medio" | "basso"
            }
          ]
        }
    """.trimIndent()

    fun matchingPrompt(listaAlimentiJson: String, tabellaNormalizzataJson: String): String =
        MATCHING_PROMPT_TEMPLATE
            .replace("{{LISTA_ALIMENTI_JSON}}", listaAlimentiJson)
            .replace("{{TABELLA_NORMALIZZATA_JSON}}", tabellaNormalizzataJson)

    /**
     * Passaggio "Ricerca online" (aggiuntivo, non nel documento originale): usato solo quando un
     * alimento non trova corrispondenza in nessuna tabella dell'utente. A differenza degli altri
     * 3 passaggi, qui il modello deve cercare sul web (grounding), non limitarsi a interpretare i
     * dati già forniti nel prompt.
     */
    fun onlineFoodResearchPrompt(foodName: String): String = """
        Sei un assistente esperto in valori nutrizionali. Devi cercare informazioni AGGIORNATE
        sul web per l'alimento "$foodName", che non è presente nelle tabelle nutrizionali
        dell'utente.

        Cerca su PIÙ fonti, non fermarti alla prima trovata. Cerca PRIMA su fonti
        ufficiali/istituzionali (es. banche dati nutrizionali governative come CREA/INRAN per
        l'Italia o USDA FoodData Central, EFSA, l'etichetta nutrizionale ufficiale del produttore
        se è un prodotto di marca). SOLO se non trovi nulla su fonti ufficiali, cerca su fonti
        generiche ma affidabili (siti di nutrizione riconosciuti). Se non trovi alcuna fonte
        attendibile, NON inventare valori.

        Trova, per 100g di prodotto:
        - contenuto di carboidrati (CHO) in grammi
        - contenuto di grassi in grammi

        Classifica il risultato con un livello di affidabilità, seguendo ESATTAMENTE questo
        criterio:
        - "sicura": almeno una fonte ufficiale/istituzionale aggiornata conferma il dato.
        - "buona": nessuna fonte ufficiale disponibile, ma almeno una fonte non ufficiale
          comunque autorevole (es. sito di nutrizione riconosciuto) conferma il dato.
        - "errore_dati": usa questo livello in DUE casi — (a) non hai trovato nessuna fonte
          utilizzabile, oppure (b) le fonti trovate sono in contrasto tra loro (valori diversi in
          modo significativo) E hanno un livello di affidabilità comparabile (differenza di un
          solo gradino: sicura-vs-buona oppure buona-vs-errore). Un contrasto NON conta se più
          fonti del livello più alto disponibile si confermano a vicenda: in quel caso vince il
          consenso del livello più alto e il risultato resta "sicura" o "buona" di conseguenza,
          ignorando fonti isolate di livello inferiore che dicono altro.

        Se l'affidabilità è "errore_dati", lascia cho_per_100g e grassi_per_100g a null (non
        proporre un numero che potrebbe essere sbagliato) e spiega brevemente il motivo in
        motivo_errore (mancanza di fonti, oppure quali fonti sono in contrasto tra loro).

        Rispondi ESCLUSIVAMENTE in formato JSON, senza testo aggiuntivo, con questa struttura:

        {
          "affidabilita": "sicura" | "buona" | "errore_dati",
          "alimento": "string (nome dell'alimento, in italiano)",
          "cho_per_100g": number o null,
          "grassi_per_100g": number o null,
          "fonte_nome": "string (nome della fonte principale, es. \"USDA FoodData Central\", vuoto se nessuna)",
          "fonte_url": "string (URL della pagina consultata, vuoto se nessuna)",
          "motivo_errore": "string (solo se affidabilita è \"errore_dati\", altrimenti vuoto)"
        }
    """.trimIndent()
}
