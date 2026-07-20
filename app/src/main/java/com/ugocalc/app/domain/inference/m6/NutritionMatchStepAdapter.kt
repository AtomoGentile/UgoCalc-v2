package com.ugocalc.app.domain.inference.m6

import com.ugocalc.app.data.repository.RecipeRepository
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import javax.inject.Inject

/**
 * M6 – Nutrition Engine (piano V2 §5, §14): resta quello della V1 ([MatchFoodsAcrossTablesUseCase]),
 * nessuna riscrittura. Questo è l'unico punto di innesto tra la pipeline V2 e il motore
 * nutrizionale esistente (guida migrazione §3) — converte le [WeightEstimate] di M3/M4/M5 in
 * [RecognizedFood].
 */
class NutritionMatchStepAdapter @Inject constructor(
    private val matchFoodsAcrossTables: MatchFoodsAcrossTablesUseCase,
    private val recipeRepository: RecipeRepository,
) {
    private val RECIPE_MIN_CONFIDENCE = Confidence.ALTO // only automatic decomposition when confidence is ALTO

    suspend fun match(model: AiModel, stime: List<WeightEstimate>, sessionMode: SessionMode): Map<String, NormalizedFoodEntry> {
        val expandedRecognized = mutableListOf<RecognizedFood>()

        for (est in stime) {
            if (est.strategia == EstimationStrategy.COMPOSTO_OCCLUSO && est.confidenza == RECIPE_MIN_CONFIDENCE) {
                // Try to find a recipe matching the recognized name in the current session mode
                val recipe = try {
                    recipeRepository.findByNameFuzzy(est.nome.trim(), sessionMode)
                } catch (e: Exception) {
                    null
                }

                if (recipe != null && recipe.ingredients.isNotEmpty()) {
                    // Normalize percentages in case they don't sum to 100
                    val total = recipe.ingredients.sumOf { it.percentage }.takeIf { it > 0.0 } ?: 100.0
                    recipe.ingredients.forEach { ing ->
                        val pct = if (total > 0.0) ing.percentage / total else 0.0
                        val pesoIng = est.pesoG * pct
                        expandedRecognized.add(
                            RecognizedFood(
                                nome = ing.name,
                                pesoStimatoG = pesoIng,
                                commento = "${est.catenaCalcolo.sintesi} — decomposizione ricetta '${recipe.name}' (${(pct * 100).toInt()}%)",
                                confidenza = est.confidenza,
                                decomposition = com.ugocalc.app.domain.model.DecompositionInfo(
                                    recipeId = recipe.id,
                                    recipeName = recipe.name,
                                    percentageOfRecipe = pct,
                                ),
                            )
                        )
                    }
                    // Proceed to next estimate
                    continue
                }
                // else fallthrough to default single recognized food
            }

            // Default behavior: single recognized food corresponding to the weight estimate
            expandedRecognized.add(
                RecognizedFood(
                    nome = est.nome,
                    pesoStimatoG = est.pesoG,
                    commento = est.catenaCalcolo.sintesi,
                    confidenza = est.confidenza,
                )
            )
        }

        val lines = matchFoodsAcrossTables(expandedRecognized, model)
        return lines.mapNotNull { line ->
            val entry = line.tableEntry
            if (line.isExcluded || entry == null) null else line.recognizedFood.nome to entry
        }.toMap()
    }
}
