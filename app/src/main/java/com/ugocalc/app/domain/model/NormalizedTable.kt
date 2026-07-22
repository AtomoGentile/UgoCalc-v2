package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una riga della tabella normalizzata, output del Passaggio 2 (§4, schema JSON). */
@Serializable
data class NormalizedFoodEntry(
    val nome: String,
    @SerialName("cho_per_100g") val choPer100g: Double,
    @SerialName("grassi_per_100g") val grassiPer100g: Double,
)

/** Riga scartata durante la normalizzazione, da mostrare come avviso in fase di review (§4). */
@Serializable
data class DiscardedRow(
    @SerialName("contenuto_originale") val contenutoOriginale: String,
    val motivo: String,
)

/** Output completo del Passaggio 2 per un file caricato (§4, schema JSON). */
@Serializable
data class NormalizationResult(
    val alimenti: List<NormalizedFoodEntry>,
    val righeScartate: List<DiscardedRow> = emptyList(),
    val noteConversione: String = "",
)

/**
 * Tipi di file accettati per le tabelle ufficiali (§2.2). ONLINE_RESEARCH è un caso speciale: non
 * viene da un file importato dall'utente, ma dalla tabella sintetica "Alimenti trovati online"
 * (§Pezzo E) dove finiscono gli alimenti trovati con la ricerca online e aggiunti al database.
 * MANUAL_ENTRY è un altro caso speciale: voci inserite manualmente dall'utente (§ richiesta utente).
 */
enum class OfficialFileType { PDF, DOC, DOCX, XLSX, ONLINE_RESEARCH, MANUAL_ENTRY }
