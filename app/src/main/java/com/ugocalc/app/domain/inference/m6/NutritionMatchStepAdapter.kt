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
    suspend fun match(
        model: AiModel,
        stime: List<WeightEstimate>,
        sessionMode: SessionMode,
        sessionId: String? = null
    ): Map<String, NormalizedFoodEntry> = matchWith(
        model = model,
        stime = stime,
        sessionMode = sessionMode,
        sessionId = sessionId,
        findRecipe = { name, mode -> recipeRepository.findByNameFuzzy(name, mode) },
        matchFoods = { foods, m, sid -> matchFoodsAcrossTables(foods, m, sid) },
    )

    companion object {
        private val recipeMinConfidence = Confidence.ALTO // only automatic decomposition when confidence is ALTO

        internal suspend fun matchWith(
        model: AiModel,
        stime: List<WeightEstimate>,
        sessionMode: SessionMode,
        sessionId: String? = null,
        findRecipe: suspend (String, SessionMode) -> com.ugocalc.app.data.repository.Recipe?,
        matchFoods: suspend (List<RecognizedFood>, AiModel, String?) -> List<com.ugocalc.app.domain.model.CalculatedFoodLine>,
    ): Map<String, NormalizedFoodEntry> {
        val expandedRecognized = mutableListOf<RecognizedFood>()
        val decomposedRecipes = mutableListOf<DecomposedRecipe>()

        for (est in stime) {
            if (est.strategia == EstimationStrategy.COMPOSTO_OCCLUSO && Confidence.fromScore(est.confidenza) == recipeMinConfidence) {
                // Try to find a recipe matching the recognized name in the current session mode
                val recipe = try {
                    findRecipe(est.nome.trim(), sessionMode)
                } catch (_: Exception) {
                    null
                }

                if (recipe != null && recipe.ingredients.isNotEmpty()) {
                    // Normalize percentages in case they don't sum to 100
                    val total = recipe.ingredients.sumOf { it.percentage }.takeIf { it > 0.0 } ?: 100.0
                    val ingredientItems = recipe.ingredients.map { ing ->
                        val pct = ing.percentage / total
                        // Usa pesoCrudoG: la ricetta è definita su ingredienti crudi.
                        val pesoIng = est.pesoCrudoG * pct
                        val recognized = RecognizedFood(
                            nome = ing.name,
                            pesoStimatoG = pesoIng,
                            commento = "${est.catenaCalcolo.sintesi} — decomposizione ricetta '${recipe.name}' (${(pct * 100).toInt()}%)",
                            confidenza = Confidence.fromScore(est.confidenza),
                            confidenzaPeso = est.confidenza,
                            decomposition = com.ugocalc.app.domain.model.DecompositionInfo(
                                recipeId = recipe.id,
                                recipeName = recipe.name,
                                percentageOfRecipe = pct,
                            ),
                        )
                        expandedRecognized.add(recognized)
                        recognized to pct
                    }
                    decomposedRecipes.add(
                        DecomposedRecipe(
                            foodName = est.nome,
                            recipeName = recipe.name,
                            ingredients = ingredientItems,
                        )
                    )
                    // Proceed to next estimate
                    continue
                }
                // else fallthrough to default single recognized food
            }

            // Default behavior: single recognized food corresponding to the weight estimate
            expandedRecognized.add(
                RecognizedFood(
                    nome = est.nome,
                    pesoStimatoG = est.pesoCrudoG,
                    commento = est.catenaCalcolo.sintesi,
                    confidenza = Confidence.fromScore(est.confidenza),
                    confidenzaPeso = est.confidenza,
                )
            )
        }

        val lines = matchFoods(expandedRecognized, model, sessionId)
        val result = mutableMapOf<String, NormalizedFoodEntry>()
        for (line in lines) {
            val entry = line.tableEntry
            if (!line.isExcluded && entry != null) {
                result[line.recognizedFood.nome] = entry
            }
        }

        // Per ogni alimento decomposto da ricetta, aggrega CHO e grassi per 100g ponderati sulle
        // percentuali degli ingredienti trovati e inserisci l'entry sintetica sotto chiave `est.nome`
        // così NutritionCalculator (M7) calcola il piatto composto anziché marcarlo escluso.
        for (decomposed in decomposedRecipes) {
            val matchedIngredients = decomposed.ingredients.mapNotNull { (rf, pct) ->
                val line = lines.firstOrNull { it.recognizedFood === rf }
                    ?: lines.firstOrNull { it.recognizedFood.nome == rf.nome }
                val entry = line?.tableEntry
                if (line != null && !line.isExcluded && entry != null) {
                    pct to entry
                } else {
                    null
                }
            }
            if (matchedIngredients.isNotEmpty()) {
                val choPer100g = matchedIngredients.sumOf { (pct, entry) -> pct * entry.choPer100g }
                val grassiPer100g = matchedIngredients.sumOf { (pct, entry) -> pct * entry.grassiPer100g }
                result[decomposed.foodName] = NormalizedFoodEntry(
                    nome = "${decomposed.recipeName} (${matchedIngredients.joinToString(" + ") { it.second.nome }})",
                    choPer100g = choPer100g,
                    grassiPer100g = grassiPer100g,
                )
            }
        }

        return result
    }

        private data class DecomposedRecipe(
            val foodName: String,
            val recipeName: String,
            val ingredients: List<Pair<RecognizedFood, Double>>,
        )
    }
}
