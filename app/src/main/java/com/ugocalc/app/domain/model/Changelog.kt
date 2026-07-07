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
    val ENTRIES = emptyList<ChangelogEntry>()
}
