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
            version = "2.0.0",
            highlights = listOf(
                "Introduzione del motore di inferenza V2 (modalità shadow): V2 esegue una pipeline M0→M8 in background per raccogliere telemetria e valutare la qualità delle stime senza sostituire immediatamente V1 nella UI.",
                "Valutazione automatica dei benchmark: aggiunto BenchmarkReportWorker che aggrega i pasti con ground truth e valuta la promozione di V2 basata sulla percentuale di pasti con errore CHO entro 10g.",
                "Promozione automatica: se le metriche soddisfano i criteri, V2 viene promossa a 'visible mode' tramite la nuova impostazione persistente v2VisibleModeEnabled.",
                "Integrazione Hilt + WorkManager: HiltWorkerFactory registrato in Application per permettere l'iniezione nei Worker; WorkManager fornito via un modulo DI.",
                "Logica di promozione estratta in BenchmarkPromotionHandler per facilitare i test unitari e la manutenzione.",
                "Scheduling: il worker di valutazione è schedulato come lavoro periodico (giornaliero) e usa enqueueUniquePeriodicWork per evitare duplicati.",
                "Nuova impostazione persistente: v2VisibleModeEnabled (Settings) per abilitare V2 come motore visibile all'utente.",
                "Test: aggiunti unit test per la logica di promozione (BenchmarkPromotionHandlerTest).",
                "Bug fix e refactor minori: migliorata l'integrazione delle preferenze e isolata la logica di promotion per evitare effetti collaterali in fase di test."
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.0.0",
                perceptionPrompt = "1.0.0",
                physicalData = "1.0.0",
                biasCalculator = "1.0.0",
            ),
        ),
    )
}
