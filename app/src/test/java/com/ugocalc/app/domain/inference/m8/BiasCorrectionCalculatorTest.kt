package com.ugocalc.app.domain.inference.m8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiasCorrectionCalculatorTest {

    @Test
    fun `fewer observations than the minimum sample count are declared insufficient`() {
        val observations = List(BiasCorrectionCalculator.MIN_SAMPLE_COUNT - 1) { BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 120.0) }

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.DatiInsufficienti)
        assertEquals(BiasCorrectionCalculator.MIN_SAMPLE_COUNT - 1, (result as BiasCorrectionResult.DatiInsufficienti).campione)
    }

    @Test
    fun `a consistent underestimation produces the median ratio as the correction factor`() {
        // M3 sottostima sempre: il vero è sistematicamente più alto dello stimato.
        val observations = listOf(
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 120.0), // 1.20
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 110.0), // 1.10
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 130.0), // 1.30
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 115.0), // 1.15
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 125.0), // 1.25
        )

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.Applicabile)
        val applicabile = result as BiasCorrectionResult.Applicabile
        assertEquals(5, applicabile.campione)
        assertEquals(1.20, applicabile.fattore, 1e-9) // mediana di 1.10,1.15,1.20,1.25,1.30
    }

    @Test
    fun `mixed over and under estimation with no clear sign agreement is declared unstable`() {
        val observations = listOf(
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 120.0), // sovrastima del vero (sottostima di M3)
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 80.0), // sottostima del vero (sovrastima di M3)
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 115.0),
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 85.0),
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 105.0),
        )

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.BiasInstabile)
    }

    @Test
    fun `a single outlier meal does not skew the factor thanks to the median`() {
        val observations = listOf(
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 110.0), // 1.10
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 112.0), // 1.12
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 108.0), // 1.08
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 111.0), // 1.11
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 300.0), // pasto anomalo: 3.00
        )

        val result = BiasCorrectionCalculator.calculate(observations) as BiasCorrectionResult.Applicabile

        assertEquals(1.11, result.fattore, 1e-9)
    }

    @Test
    fun `exactly the minimum sample count is enough to be applicable`() {
        val observations = List(BiasCorrectionCalculator.MIN_SAMPLE_COUNT) { BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 120.0) }

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.Applicabile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive estimated weight is rejected explicitly`() {
        BiasObservation(pesoStimatoG = 0.0, pesoVeroG = 120.0)
    }
}
