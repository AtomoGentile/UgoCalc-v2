package com.ugocalc.app.domain.inference.m7

import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.serialization.Serializable

/**
 * M7 – Calcolo carboidrati e grassi + spiegazione (piano V2 §5, §7). Deterministico: prende i grammi
 * calcolati da M3/M5 e i valori per 100 g abbinati da M6, e produce il contributo CHO/grassi di ogni
 * alimento più il report di spiegazione leggibile dalla catena di calcolo di M3 (§5 M7, esempio:
 * "165 g perché: piatto Ø26 cm calibrato × 45% coperto × ...").
 *
 * Se un alimento non ha un abbinamento nutrizionale (nessuna corrispondenza in M6), è escluso dal
 * totale e segnalato come tale — mai stimato a zero in silenzio (stessa regola della V1, §5
 * "Gestione delle non corrispondenze").
 */
object NutritionCalculator {

    fun calculate(estimates: List<WeightEstimate>, matches: Map<String, NormalizedFoodEntry>): NutritionReport {
        val righe = estimates.map { estimate ->
            val entry = matches[estimate.nome]
            // Usa pesoCrudoG per i calcoli CHO/grassi: le tabelle CREA/INRAN sono su base cruda.
            val pesoPerCalcolo = estimate.pesoCrudoG
            FoodNutritionLine(
                nome = estimate.nome,
                pesoG = estimate.pesoCrudoG,
                choG = entry?.let { it.choPer100g * pesoPerCalcolo / 100.0 },
                grassiG = entry?.let { it.grassiPer100g * pesoPerCalcolo / 100.0 },
                spiegazione = estimate.catenaCalcolo.sintesi,
                escluso = entry == null,
            )
        }
        return NutritionReport(
            righe = righe,
            choTotaleG = righe.sumOf { it.choG ?: 0.0 },
            grassiTotaleG = righe.sumOf { it.grassiG ?: 0.0 },
        )
    }
}

/** Contributo CHO/grassi di UN alimento più la sua spiegazione (§5 M7, §7 catena di calcolo). */
@Serializable
data class FoodNutritionLine(
    val nome: String,
    val pesoG: Double,
    val choG: Double?,
    val grassiG: Double?,
    val spiegazione: String,
    /** Vero se l'alimento non ha un abbinamento nutrizionale: escluso dal totale, mai stimato a zero in silenzio. */
    val escluso: Boolean,
)

/** Output finale di M7: righe per alimento e totali di sessione (§5 M7). */
@Serializable
data class NutritionReport(
    val righe: List<FoodNutritionLine>,
    val choTotaleG: Double,
    val grassiTotaleG: Double,
)
