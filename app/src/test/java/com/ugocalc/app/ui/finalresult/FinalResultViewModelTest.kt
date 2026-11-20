package com.ugocalc.app.ui.finalresult

import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.ui.components.formatMealShareSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalResultViewModelTest {

    private fun sampleLine(name: String = "Pasta", grams: Double = 80.0): CalculatedFoodLine =
        CalculatedFoodLine(
            recognizedFood = RecognizedFood(
                nome = name,
                pesoStimatoG = grams,
                commento = "",
                confidenza = Confidence.ALTO,
            ),
            match = FoodMatch(
                nomeRiconosciuto = name,
                voceTabellaAbbinata = name,
                nessunaCorrispondenza = false,
                confidenzaAbbinamento = Confidence.ALTO,
            ),
            tableEntry = NormalizedFoodEntry(
                nome = name,
                choPer100g = 75.0,
                grassiPer100g = 1.5,
            ),
        )

    @Test
    fun `adjustLineWeight increases weight and recalculates totals`() {
        val initial = listOf(sampleLine(grams = 80.0))
        val updated = adjustLineWeight(initial, lineIndex = 0, deltaGrams = 10f)
        assertNotNull(updated)
        val totals = AnalysisTotals(updated!!)
        assertEquals(90.0, updated[0].recognizedFood.pesoStimatoG, 1e-6)
        assertEquals(67.5, totals.totalChoG, 1e-6)
        assertEquals(1.35, totals.totalGrassiG, 1e-6)
    }

    @Test
    fun `adjustLineWeight clamps safely at minimum 5 grams`() {
        val initial = listOf(sampleLine(grams = 12.0))
        val updated = adjustLineWeight(initial, lineIndex = 0, deltaGrams = -10f)
        assertNotNull(updated)
        assertEquals(5.0, updated!![0].recognizedFood.pesoStimatoG, 1e-6)

        // Already at 5g, decrementing further returns null (no-op)
        assertNull(adjustLineWeight(updated, lineIndex = 0, deltaGrams = -10f))
    }

    @Test
    fun `adjustLineWeight returns null for out of bounds index`() {
        val initial = listOf(sampleLine())
        assertNull(adjustLineWeight(initial, lineIndex = -1, deltaGrams = 10f))
        assertNull(adjustLineWeight(initial, lineIndex = 1, deltaGrams = 10f))
    }

    @Test
    fun `formatMealShareSummary includes title, macros, food list and UgoCalc footer`() {
        val lines = listOf(sampleLine("Pasta", 100.0))
        val totals = AnalysisTotals(lines)
        val text = formatMealShareSummary(
            title = "Pranzo veloce",
            totalChoG = totals.totalChoG,
            totalGrassiG = totals.totalGrassiG,
            lines = lines,
            sessionModeLabel = "Casa",
        )
        assertTrue(text.contains("Pranzo veloce"))
        assertTrue(text.contains("Casa"))
        assertTrue(text.contains("kcal"))
        assertTrue(text.contains("Carboidrati (CHO)"))
        assertTrue(text.contains("Grassi"))
        assertTrue(text.contains("Pasta (100 g)"))
        assertTrue(text.contains("Calcolato con UgoCalc"))
    }
}
