package com.ugocalc.app.domain.inference.m7

import com.ugocalc.app.data.repository.Recipe
import com.ugocalc.app.data.repository.RecipeIngredient
import com.ugocalc.app.domain.inference.m6.NutritionMatchStepAdapter
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalculatorTest {

    private fun estimate(nome: String, pesoG: Double) = WeightEstimate(
        nome = nome,
        strategia = EstimationStrategy.DISTESA_SUL_PIATTO,
        pesoG = pesoG,
        pesoCrudoG = pesoG,
        intervallo = ConfidenceInterval(pesoG * 0.8, pesoG * 1.2),
        catenaCalcolo = CalculationChain(
            formula = "frazione × area × altezza × riempimento × densità",
            valori = emptyMap(),
            fonti = emptyList(),
            sintesi = "$pesoG g — spiegazione di prova",
        ),
        confidenza = ConfidenceScore(90),
    )

    @Test
    fun `computes CHO and fat contribution from grams and per-100g values`() {
        val estimates = listOf(estimate("riso in bianco", 165.0))
        val matches = mapOf("riso in bianco" to NormalizedFoodEntry("riso in bianco", choPer100g = 28.0, grassiPer100g = 0.3))

        val report = NutritionCalculator.calculate(estimates, matches)

        assertEquals(1, report.righe.size)
        val riga = report.righe.first()
        assertEquals(165.0 * 28.0 / 100.0, riga.choG!!, 1e-9)
        assertEquals(165.0 * 0.3 / 100.0, riga.grassiG!!, 1e-9)
        assertTrue(riga.spiegazione.contains("spiegazione di prova"))
        assertEquals(riga.choG, report.choTotaleG, 1e-9)
        assertEquals(riga.grassiG, report.grassiTotaleG, 1e-9)
    }

    @Test
    fun `unmatched food is excluded from totals, never silently zeroed`() {
        val estimates = listOf(estimate("piatto misterioso", 100.0))

        val report = NutritionCalculator.calculate(estimates, emptyMap())

        val riga = report.righe.first()
        assertTrue(riga.escluso)
        assertEquals(null, riga.choG)
        assertEquals(null, riga.grassiG)
        assertEquals(0.0, report.choTotaleG, 1e-9)
    }

    @Test
    fun `mixed matched and unmatched foods only sum the matched ones`() {
        val estimates = listOf(estimate("riso in bianco", 100.0), estimate("alimento sconosciuto", 50.0))
        val matches = mapOf("riso in bianco" to NormalizedFoodEntry("riso in bianco", choPer100g = 20.0, grassiPer100g = 1.0))

        val report = NutritionCalculator.calculate(estimates, matches)

        assertEquals(20.0, report.choTotaleG, 1e-9)
        assertEquals(1.0, report.grassiTotaleG, 1e-9)
        assertEquals(1, report.righe.count { it.escluso })
    }

    @Test
    fun `recipe decomposition produces synthetic composite entry so NutritionCalculator computes CHO and fat`() = runBlocking {
        val model = AiModelCatalog.ALL.first()
        val lasagnaEst = WeightEstimate(
            nome = "lasagna",
            strategia = EstimationStrategy.COMPOSTO_OCCLUSO,
            pesoG = 300.0,
            pesoCrudoG = 300.0,
            intervallo = ConfidenceInterval(240.0, 360.0),
            catenaCalcolo = CalculationChain("blocco", emptyMap(), emptyList(), "300 g lasagna"),
            confidenza = ConfidenceScore(90),
        )
        val recipe = Recipe(
            id = 1L,
            name = "Lasagna classica",
            description = "",
            ingredients = listOf(
                RecipeIngredient(id = 1L, name = "pasta all'uovo", percentage = 40.0),
                RecipeIngredient(id = 2L, name = "ragù di carne", percentage = 40.0),
                RecipeIngredient(id = 3L, name = "besciamella", percentage = 20.0),
            ),
        )
        val tableByIngredient = mapOf(
            "pasta all'uovo" to NormalizedFoodEntry("Pasta all'uovo", choPer100g = 70.0, grassiPer100g = 4.0),
            "ragù di carne" to NormalizedFoodEntry("Ragù alla bolognese", choPer100g = 5.0, grassiPer100g = 10.0),
            "besciamella" to NormalizedFoodEntry("Besciamella", choPer100g = 10.0, grassiPer100g = 12.0),
        )

        val matches = NutritionMatchStepAdapter.matchWith(
            model = model,
            stime = listOf(lasagnaEst),
            sessionMode = SessionMode.CASA,
            findRecipe = { _, _ -> recipe },
            matchFoods = { foods, _, _ ->
                foods.map { rf ->
                    val entry = tableByIngredient[rf.nome]
                    CalculatedFoodLine(
                        recognizedFood = rf,
                        match = entry?.let { FoodMatch(rf.nome, it.nome, false, Confidence.ALTO) },
                        tableEntry = entry,
                    )
                }
            },
        )

        val report = NutritionCalculator.calculate(listOf(lasagnaEst), matches)
        val riga = report.righe.single()
        assertFalse(riga.escluso)
        // Media ponderata: CHO/100g = 0.4*70 + 0.4*5 + 0.2*10 = 32.0 -> su 300g = 96.0g
        // Grassi/100g = 0.4*4 + 0.4*10 + 0.2*12 = 8.0 -> su 300g = 24.0g
        assertEquals(96.0, riga.choG!!, 1e-9)
        assertEquals(24.0, riga.grassiG!!, 1e-9)
        assertEquals(96.0, report.choTotaleG, 1e-9)
        assertEquals(24.0, report.grassiTotaleG, 1e-9)
    }

    @Test
    fun `recipe decomposition with no matched ingredients keeps composite food excluded`() = runBlocking {
        val model = AiModelCatalog.ALL.first()
        val lasagnaEst = WeightEstimate(
            nome = "lasagna",
            strategia = EstimationStrategy.COMPOSTO_OCCLUSO,
            pesoG = 300.0,
            pesoCrudoG = 300.0,
            intervallo = ConfidenceInterval(240.0, 360.0),
            catenaCalcolo = CalculationChain("blocco", emptyMap(), emptyList(), "300 g lasagna"),
            confidenza = ConfidenceScore(90),
        )
        val recipe = Recipe(
            id = 1L,
            name = "Lasagna",
            description = "",
            ingredients = listOf(RecipeIngredient(id = 1L, name = "ingrediente ignoto", percentage = 100.0)),
        )

        val matches = NutritionMatchStepAdapter.matchWith(
            model = model,
            stime = listOf(lasagnaEst),
            sessionMode = SessionMode.CASA,
            findRecipe = { _, _ -> recipe },
            matchFoods = { foods, _, _ ->
                foods.map { rf ->
                    CalculatedFoodLine(
                        recognizedFood = rf,
                        match = FoodMatch(rf.nome, "", true, Confidence.BASSO),
                        tableEntry = null,
                    )
                }
            },
        )

        val report = NutritionCalculator.calculate(listOf(lasagnaEst), matches)
        val riga = report.righe.single()
        assertTrue(riga.escluso)
        assertEquals(null, riga.choG)
    }
}
