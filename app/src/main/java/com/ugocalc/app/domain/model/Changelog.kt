package com.ugocalc.app.domain.model

data class ChangelogEntry(val version: String, val highlights: List<String>)

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
                "Bug fix: la fotocamera poteva mandare in crash l'app al primo scatto se il permesso non era mai stato concesso da Impostazioni.",
                "Bug fix: uno scatto fatto tenendo il telefono in orizzontale poteva andare perso (l'app veniva ricreata mentre la fotocamera di sistema era in primo piano).",
                "Nella schermata finale, ora è possibile correggere nome e peso di un alimento riconosciuto, o rimuoverlo del tutto.",
                "Nella schermata finale, è possibile aggiungere un alimento scelto direttamente dalle tue tabelle, saltando il riconoscimento automatico.",
                "Nuovo controllo aggiornamenti in Impostazioni > Software e aggiornamenti: verifica l'ultima versione pubblicata su GitHub e permette di scaricarla.",
                "Nuova voce nel menu \"BIAS ed evoluzione del comportamento\": consenso al trattamento dei dati per l'identificazione del bias e trasparenza sui fattori di correzione appresi.",
                "Nuova sezione Impostazioni > Backup e ripristino: esporta o importa cronologia, database e dati di correzione del bias come file separati.",
                "In Homepage, la scelta Casa/Ristorante ora è un'icona in topAppBar (accanto a Cronologia): casetta quando sei in modalità Casa, piatto e posate quando sei in modalità Ristorante, un tocco per alternarle.",
                "Nuova sezione Impostazioni > Accessibilità: colori personalizzati separati per la modalità Casa e per la modalità Ristorante, applicati a tutta l'app in base all'icona attiva in Homepage.",
            ),
        ),
    )
}
