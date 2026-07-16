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
