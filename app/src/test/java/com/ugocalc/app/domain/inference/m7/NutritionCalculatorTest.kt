package com.ugocalc.app.domain.inference.m7

import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import org.junit.Assert.assertEquals
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
}
