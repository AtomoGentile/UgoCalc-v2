package com.ugocalc.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultTest {

    private fun recognized(nome: String, grammi: Double) =
        RecognizedFood(nome = nome, pesoStimatoG = grammi, commento = "", confidenza = Confidence.ALTO)

    private fun match(nome: String, nessunaCorrispondenza: Boolean = false) =
        FoodMatch(
            nomeRiconosciuto = nome,
            voceTabellaAbbinata = nome,
            nessunaCorrispondenza = nessunaCorrispondenza,
            confidenzaAbbinamento = Confidence.ALTO,
        )

    private fun tableEntry(cho: Double, grassi: Double) =
        NormalizedFoodEntry(nome = "voce", choPer100g = cho, grassiPer100g = grassi)

    @Test
    fun `a matched line contributes proportionally to its weight`() {
        val line = CalculatedFoodLine(
            recognizedFood = recognized("pasta", 200.0),
            match = match("pasta"),
            tableEntry = tableEntry(cho = 25.0, grassi = 1.0),
        )
        assertFalse(line.isExcluded)
        assertEquals(50.0, line.choContributionG, 0.001)
        assertEquals(2.0, line.grassiContributionG, 0.001)
    }

    @Test
    fun `a line with no match is excluded and contributes nothing`() {
        val line = CalculatedFoodLine(
            recognizedFood = recognized("mistero", 100.0),
            match = null,
            tableEntry = null,
        )
        assertTrue(line.isExcluded)
        assertEquals(0.0, line.choContributionG, 0.001)
        assertEquals(0.0, line.grassiContributionG, 0.001)
    }

    @Test
    fun `nessuna_corrispondenza excludes the line even if a table entry is present`() {
        val line = CalculatedFoodLine(
            recognizedFood = recognized("boh", 100.0),
            match = match("boh", nessunaCorrispondenza = true),
            tableEntry = tableEntry(cho = 10.0, grassi = 10.0),
        )
        assertTrue(line.isExcluded)
        assertEquals(0.0, line.choContributionG, 0.001)
    }

    @Test
    fun `totals sum only non-excluded lines`() {
        val matched = CalculatedFoodLine(recognized("pasta", 100.0), match("pasta"), tableEntry(cho = 20.0, grassi = 2.0))
        val excluded = CalculatedFoodLine(recognized("boh", 100.0), null, null)
        val totals = AnalysisTotals(listOf(matched, excluded))

        assertEquals(20.0, totals.totalChoG, 0.001)
        assertEquals(2.0, totals.totalGrassiG, 0.001)
        assertEquals(1, totals.excludedCount)
    }
}
