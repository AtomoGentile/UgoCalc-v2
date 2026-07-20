package com.ugocalc.app.domain.model

/**
 * Versioni dei moduli interni del motore V2 (piano V2 §15): le righe qui sotto sono rivendicate
 * come "rilasciate X" dal corpo della release, non sono calcolate automaticamente dal codice a
 * runtime. Servono a legare un record M8 (che può contenere qualunque combinazione di versioni
 * candidato in futuro) a un changelog leggibile: anche se la pipeline V2 non è ancora il flusso
 * visibile, l'utente deve poter sapere "questa versione 2.1.0 porta il motore V2 a X".
 *
 * Tutti i campi sono opzionali: una voce di changelog potrebbe non toccare tutti i moduli.
 */
data class ModuleVersions(
    @androidx.annotation.Keep val inferenceEngine: String? = null,
    @androidx.annotation.Keep val perceptionPrompt: String? = null,
    @androidx.annotation.Keep val physicalData: String? = null,
    @androidx.annotation.Keep val biasCalculator: String? = null,
    @androidx.annotation.Keep val ambiguityResolver: String? = null,
    @androidx.annotation.Keep val matchStep: String? = null,
)

/**
 * Una riga del changelog in-app. [moduleVersions] è popolata solo per le release che toccano
 * esplicitamente il motore V2; per le altre (UI, piccole migrazioni) resta `null`.
 */
data class ChangelogEntry(
    val version: String,
    val highlights: List<String>,
    val moduleVersions: ModuleVersions? = null,
)

/**
 * Storico degli aggiornamenti pubblicati (changelog in-app): più recente per prima. Va aggiornato
 * ad ogni versione successiva con novità rilevanti per l'utente, non ogni singolo commit/micro-fix,
 * solo ciò che cambia qualcosa di visibile o utile da sapere. Le correzioni di bug vanno incluse
 * quanto le nuove funzionalità, etichettate esplicitamente come "Bug fix:" o "Corretto:".
 */
object Changelog {
    val ENTRIES = listOf(
        ChangelogEntry(
            version = "2.4.0",
            highlights = listOf(
                "Nuova Identità Visiva: introdotto il nuovo logo ufficiale di UgoCalc, che riflette meglio l'unione tra tecnologia (calcolatrice) e nutrizione (pasta, riso, grano).",
                "Ottimizzazione Prestazioni BIAS: risolto un problema di stuttering pesante nella sezione Evoluzione del Comportamento. Il caricamento dei dati storici e il calcolo delle statistiche (MAE, RMSE) sono ora fino a 10 volte più veloci grazie al caching intelligente.",
                "Layout Orizzontale (Landscape): la Homepage è stata riprogettata per i dispositivi ruotati. Il pulsante 'Analizza' è ora affiancato alle azioni rapide e ai preferiti, ottimizzando l'uso dello spazio.",
                "Personalizzazione Temi: rinnovati i colori della Modalità Casa con un verde più vibrante. Introdotto il tema 'Ristorante' con toni caldi (oro e bordeaux) per un distacco visivo netto.",
                "Miglioramento Sidebar: il logo e la versione dell'app sono stati spostati in fondo al menu laterale per dare priorità alle voci di navigazione, mantenendo coerenza con le Impostazioni.",
                "Accessibilità Display: aggiunto un margine di sicurezza al pulsante principale in modalità landscape per evitare interferenze con fori della fotocamera (selfie cam) e tacche del display.",
                "Bug fix: corretta la nomenclatura delle risorse grafiche per garantire la compatibilità con i sistemi di build Android più recenti."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.3.1",
                perceptionPrompt = "1.3.0",
                physicalData = "1.1.0",
                biasCalculator = "1.2.1",
                ambiguityResolver = "1.0.0",
                matchStep = "1.1.0",
            ),
        ),
        ChangelogEntry(
            version = "2.3.0",
            highlights = listOf(
                "Miglioramento Routing Strategie: risolto un errore che causava la classificazione errata della pasta condita (es. 'pasta al tonno') come blocco geometrico. Ora la pasta ha sempre la precedenza.",
                "Precisione Volumi (M1): istruita l'IA a non confondere mai il diametro del piatto con quello del cibo per le stime a cilindro, evitando sovrastime macroscopiche.",
                "Sincronizzazione Guardrail: corretto un bug visivo dove l'intervallo di confidenza non si aggiornava quando il peso veniva limitato dai controlli di sicurezza (buon senso).",
                "Gestione Ingredienti Misti: l'IA ora scompone automaticamente piatti mescolati (es. insalata di riso, macedonia) se gli ingredienti hanno profili di carboidrati diversi.",
                "Supporto Multi-pezzo per Blocchi: aggiunta la possibilità di segnalare più pezzi identici (es. fette di pane) mantenendo la precisione della misura geometrica del singolo pezzo.",
                "Allentamento Bias Calculator: rimossi i vincoli restrittivi di concordanza del segnale per permettere un'applicazione più fluida dei fattori di correzione appresi.",
                "Logging Diagnostico: aggiunto il conteggio dei pezzi rilevati direttamente nei log di sistema per un debug più rapido della percezione visiva."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.3.0",
                perceptionPrompt = "1.3.0",
                physicalData = "1.1.0",
                biasCalculator = "1.2.0",
                ambiguityResolver = "1.0.0",
                matchStep = "1.1.0",
            ),
        ),
        ChangelogEntry(
            version = "2.2.0",
            highlights = listOf(
                "Ispezione V2 Shadow: dalla Cronologia o dalla sezione BIAS è ora possibile vedere il dettaglio tecnico e il ragionamento AI anche per le analisi effettuate in background (shadow mode).",
                "Selezione manuale calibrazioni: aggiunta la possibilità di selezionare manualmente quali stoviglie o riferimenti calibrati sono presenti nelle foto, per una precisione di scala millimetrica.",
                "Spiegabilità AI (Ragionamento): l'intelligenza artificiale ora fornisce una spiegazione testuale del suo ragionamento globale sulla scena e per ogni singolo alimento identificato.",
                "Guida Giroscopio ottimizzata: ricalibrata la livella overlay per richiedere angolazioni precise (0°, 90°, 180°, 270°) e giro completo intorno al piatto semplificato (zero relativo alla prima foto).",
                "Stabilità database: aggiornato lo schema interno per supportare i nuovi collegamenti tra V1 e V2 senza perdite di dati."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.2.0",
                perceptionPrompt = "1.2.0",
                physicalData = "1.1.0",
                biasCalculator = "1.1.0",
                ambiguityResolver = "1.0.0",
                matchStep = "1.1.0",
            ),
        ),
        ChangelogEntry(
            version = "2.1.0",
            highlights = listOf(
                "Promozione motore V2: il nuovo motore di inferenza può ora essere impostato come predefinito per le analisi (visible mode).",
                "Gestione Calibrazioni Scala: nuova sezione per salvare le dimensioni di piatti, stoviglie e riferimenti corporei (Impostazioni > Calibrazioni scala).",
                "Apprendimento automatico (Bias Correction): il ciclo è ora chiuso. Inserendo il peso vero del pasto dopo l'analisi, l'app impara a correggere sistematicamente le stime future.",
                "Decomposizione piatti composti: integrazione con il database ricette per scorporare i carboidrati di lasagne, panini e piatti occlusi.",
                "Modalità Casa/Ristorante: switch rapido in Homepage con supporto a temi colore personalizzati per ogni contesto (Impostazioni > Accessibilità).",
                "Dashboard Benchmark: visualizzazione quantitativa del miglioramento della precisione tra V1 e V2 basata sui tuoi pasti reali.",
                "Backup completo: esportazione e ripristino estesi a record V2, calibrazioni e storico di apprendimento."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.1.0",
                perceptionPrompt = "1.1.0",
                physicalData = "1.1.0",
                biasCalculator = "1.1.0",
                ambiguityResolver = "1.0.0",
                matchStep = "1.1.0",
            ),
        ),
        ChangelogEntry(
            version = "2.0.0",
            highlights = listOf(
                "Introduzione del motore di inferenza V2 (modalità shadow): esecuzione in background per telemetria e valutazione qualità.",
                "Valutazione automatica benchmark: nuovo worker per l'aggregazione delle metriche di precisione.",
                "Integrazione Hilt + WorkManager: architettura robusta per i processi in background.",
                "Promozione automatica: logica basata sulla percentuale di pasti con errore CHO contenuto.",
                "Supporto Firebase App Distribution per aggiornamenti rapidi del canale tester."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.0.0",
                perceptionPrompt = "1.0.0",
                physicalData = "1.0.0",
                biasCalculator = "1.0.0",
            ),
        ),
        ChangelogEntry(
            version = "1.0.0",
            highlights = listOf(
                "Versione iniziale di UgoCalc: stima carboidrati tramite visione artificiale singola.",
                "Abbinamento tabelle nutrizionali locali e ricerca online (OpenFoodFacts).",
                "Gestione cronologia, preferiti e backup base."
            )
        ),
    )
}
