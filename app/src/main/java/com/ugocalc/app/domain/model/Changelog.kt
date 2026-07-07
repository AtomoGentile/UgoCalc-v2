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
                "Nuova guida in tempo reale durante lo scatto: un suggeritore d'angolazione indica se il telefono è troppo inclinato rispetto al piatto, per foto più affidabili.",
                "Nuova schermata \"Ground truth\": puoi indicare il peso reale di un piatto già analizzato, per aiutare l'app a correggere il proprio bias di stima.",
                "Il fattore di correzione del bias viene ora ricalcolato automaticamente ogni volta che riapri l'app, così da usare sempre i dati più recenti.",
                "Bug fix: in presenza di forte ambiguità residua nella stima, l'app ora segnala il problema invece di applicare comunque una correzione di bias potenzialmente fuorviante.",
            ),
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.1.0 (app version)",
                perceptionPrompt = "1.0.0 (Schema 1)",
                physicalData = "1.0.0",
                biasCalculator = "1.0.0",
                ambiguityResolver = "1.0.0",
                matchStep = "1.0.0",
            ),
        ),
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
            moduleVersions = ModuleVersions(
                inferenceEngine = "2.0.0",
                perceptionPrompt = "1.0.0",
                physicalData = "3.0.0",
            ),
        ),
    )
}
