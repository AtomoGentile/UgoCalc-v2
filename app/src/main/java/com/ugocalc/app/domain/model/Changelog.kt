package com.ugocalc.app.domain.model

data class ChangelogEntry(val version: String, val highlights: List<String>)

/**
 * Storico degli aggiornamenti pubblicati (changelog in-app): più recente per prima. Vuoto perché
 * la 1.0.0 è la prima versione rilasciata — va aggiornato ad ogni versione successiva con novità
 * rilevanti per l'utente, non ogni singolo commit/micro-fix, solo ciò che cambia qualcosa di
 * visibile o utile da sapere. Le correzioni di bug vanno incluse quanto le nuove funzionalità,
 * etichettate esplicitamente come "Bug fix:" o "Corretto:".
 */
object Changelog {
    val ENTRIES = listOf<ChangelogEntry>()
}
