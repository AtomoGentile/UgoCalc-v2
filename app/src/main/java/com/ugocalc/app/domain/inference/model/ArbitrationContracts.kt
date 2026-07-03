package com.ugocalc.app.domain.inference.model

import com.ugocalc.app.domain.model.Confidence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contratto dati M4 → M5 → M4 (piano V2 §5, §7). M5 (Arbitrato selettivo) riceve solo gli
 * alimenti [FlaggedItem] segnalati da M4 e le foto originali, e restituisce una revisione mirata
 * per ciascuno: stesso principio di M1 (§2), MAI grammi diretti — solo identificazione e misure
 * grezze riviste, che rientrano in M3 per un nuovo calcolo deterministico.
 *
 * `schema_version` segue la stessa regola di [SceneGraph] (§7 "Versionamento dello schema"): un
 * valore non riconosciuto è un errore gestito, non una deserializzazione parziale.
 */
@Serializable
data class ArbitrationResponse(
    @SerialName("schema_version") val schemaVersion: Int,
    val revisioni: List<RevisedFood> = emptyList(),
)

/**
 * Revisione mirata di UN alimento flaggato da M4. [nome] identifica l'alimento originale nello
 * [SceneGraph] (chiave di corrispondenza); [nomeConfermato] è il nome canonico da usare da qui in
 * avanti se M5 ha sciolto un'ambiguità identificativa (altrimenti resta assente). [misureGrezze]
 * è presente solo se M5 ha potuto fornire misure più precise; se assente, restano quelle di M1.
 */
@Serializable
data class RevisedFood(
    val nome: String,
    @SerialName("nome_confermato") val nomeConfermato: String? = null,
    @SerialName("misure_grezze") val misureGrezze: RawMeasurements? = null,
    val confidenza: Confidence,
    /** Vero se M5 ha sciolto il dubbio che aveva causato il flag; falso se l'ambiguità resta. */
    val risolto: Boolean,
)
