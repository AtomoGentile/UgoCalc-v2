package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Un singolo abbinamento, output del Passaggio 3 (§5, schema JSON). */
@Serializable
data class FoodMatch(
    @SerialName("nome_riconosciuto") val nomeRiconosciuto: String,
    @SerialName("voce_tabella_abbinata") val voceTabellaAbbinata: String,
    @SerialName("nessuna_corrispondenza") val nessunaCorrispondenza: Boolean,
    @SerialName("confidenza_abbinamento") val confidenzaAbbinamento: Confidence,
)

@Serializable
data class MatchingResult(
    val abbinamenti: List<FoodMatch>,
)

/**
 * Riga finale calcolata per la schermata risultati (§2.4 punto 6): unisce peso stimato (Passaggio 1),
 * abbinamento (Passaggio 3) e valori per 100g (Passaggio 2) per ottenere il contributo CHO/grassi.
 * Se [match] è null o [match.nessunaCorrispondenza] è true, l'alimento è escluso dal totale (§5,
 * "Gestione delle non corrispondenze") e va segnalato chiaramente in UI.
 */
@Serializable
data class CalculatedFoodLine(
    val recognizedFood: RecognizedFood,
    val match: FoodMatch?,
    val tableEntry: NormalizedFoodEntry?,
) {
    val isExcluded: Boolean get() = match == null || match.nessunaCorrispondenza || tableEntry == null

    val choContributionG: Double
        get() = if (isExcluded) 0.0 else tableEntry!!.choPer100g * recognizedFood.pesoStimatoG / 100.0

    val grassiContributionG: Double
        get() = if (isExcluded) 0.0 else tableEntry!!.grassiPer100g * recognizedFood.pesoStimatoG / 100.0

    val proteineContributionG: Double
        get() = if (isExcluded) 0.0 else tableEntry!!.proteinePer100g * recognizedFood.pesoStimatoG / 100.0
}

data class AnalysisTotals(
    val lines: List<CalculatedFoodLine>,
) {
    val totalChoG: Double get() = lines.sumOf { it.choContributionG }
    val totalGrassiG: Double get() = lines.sumOf { it.grassiContributionG }
    val totalProteineG: Double get() = lines.sumOf { it.proteineContributionG }
    val estimatedKcal: Double get() = totalChoG * 4.0 + totalGrassiG * 9.0 + totalProteineG * 4.0
    val fpuCount: Double get() = (totalGrassiG * 9.0 + totalProteineG * 4.0) / 100.0
    val excludedCount: Int get() = lines.count { it.isExcluded }

    /**
     * Percentuali CHO/grassi/proteine calcolate sulla somma dei tre macronutrienti in grammi
     * (CHO + grassi + proteine = 100%), utili per la ripartizione visiva in UI.
     */
    val choPercentOfMacros: Double
        get() {
            val total = totalChoG + totalGrassiG + totalProteineG
            return if (total > 0.0) totalChoG / total * 100.0 else 0.0
        }

    val grassiPercentOfMacros: Double
        get() {
            val total = totalChoG + totalGrassiG + totalProteineG
            return if (total > 0.0) totalGrassiG / total * 100.0 else 0.0
        }

    val proteinePercentOfMacros: Double
        get() {
            val total = totalChoG + totalGrassiG + totalProteineG
            return if (total > 0.0) (100.0 - choPercentOfMacros - grassiPercentOfMacros).coerceIn(0.0, 100.0) else 0.0
        }
}
