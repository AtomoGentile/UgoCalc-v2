package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Livello di affidabilità del dato trovato dalla ricerca online (§Pezzo D, criterio esteso):
 * - SICURA: confermato da almeno una fonte ufficiale/istituzionale aggiornata.
 * - BUONA: nessuna fonte ufficiale, ma confermato da una fonte non ufficiale autorevole.
 * - ERRORE_DATI: nessuna fonte utilizzabile trovata, oppure fonti di affidabilità comparabile
 *   (al massimo un livello di differenza: sicura↔buona o buona↔errore) in contrasto tra loro.
 *   Un contrasto tra una fonte di livello basso e un consenso di fonti di livello più alto NON
 *   conta come conflitto: vince il consenso del livello più alto.
 */
@Serializable
enum class OnlineResearchReliability {
    @SerialName("sicura") SICURA,
    @SerialName("buona") BUONA,
    @SerialName("errore_dati") ERRORE_DATI,
}

/**
 * Output della ricerca online per un alimento assente dalle tabelle dell'utente: un ulteriore
 * passaggio AI, separato da Visione/Normalizzazione/Matching, che interroga il web (fonti
 * ufficiali prima, generiche poi) invece delle tabelle caricate dall'utente.
 */
@Serializable
data class OnlineFoodResearchResult(
    val affidabilita: OnlineResearchReliability,
    val alimento: String,
    @SerialName("cho_per_100g") val choPer100g: Double? = null,
    @SerialName("grassi_per_100g") val grassiPer100g: Double? = null,
    @SerialName("fonte_nome") val fonteNome: String = "",
    @SerialName("fonte_url") val fonteUrl: String = "",
    @SerialName("motivo_errore") val motivoErrore: String = "",
) {
    /** Solo con affidabilità sicura o buona il dato va proposto per l'aggiunta al database. */
    val haDatiUtilizzabili: Boolean get() = affidabilita != OnlineResearchReliability.ERRORE_DATI && choPer100g != null && grassiPer100g != null
}
