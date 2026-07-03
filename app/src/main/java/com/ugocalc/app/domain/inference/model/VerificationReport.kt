package com.ugocalc.app.domain.inference.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contratto dati M4 → M5 (piano V2 §7). Esito della Verifica di coerenza: l'elenco dei vincoli
 * controllati con il loro esito e, se presenti, gli alimenti flaggati con il motivo.
 *
 * M4 è un modulo gratuito e sempre attivo (§5). M5 (Arbitrato selettivo, chiamata AL modello) si
 * attiva **solo** se [flaggati] non è vuoto: nel caso tipico non si attiva affatto (§4, budget di
 * due chiamate).
 */
@Serializable
data class VerificationReport(
    val controlli: List<ConstraintCheck>,
    @SerialName("flaggati") val flaggati: List<FlaggedItem> = emptyList(),
) {
    /** Vero se M5 (arbitrato selettivo) deve essere invocato su almeno un alimento. */
    val richiedeArbitrato: Boolean
        get() = flaggati.isNotEmpty()
}

/** Esito di un singolo vincolo verificato da M4. */
@Serializable
data class ConstraintCheck(
    /** Nome del vincolo, es. "densità superficiale piatto" o "peso entro intervallo tipico". */
    val nome: String,
    val superato: Boolean,
    @SerialName("dettaglio") val dettaglio: String = "",
)

/** Un alimento segnalato da M4 come da rianalizzare, con il motivo e la categoria di anomalia. */
@Serializable
data class FlaggedItem(
    @SerialName("nome_alimento") val nomeAlimento: String,
    val motivo: String,
    val tipo: FlagType,
)

/** Le categorie di anomalia che M4 può segnalare (§5 M4). */
@Serializable
enum class FlagType {
    /** Vincolo fisico violato: grammi/cm² di piatto implausibili, peso fuori dall'intervallo tipico. */
    @SerialName("fisico") VINCOLO_FISICO,

    /** Vincolo di scena: somma delle superfici incoerente, incoerenza tra le foto. */
    @SerialName("scena") VINCOLO_SCENA,

    /** Ambiguità identificativa: un'alternativa cambia sensibilmente i carboidrati. */
    @SerialName("identita") AMBIGUITA_IDENTITA,
}
