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
    fun `exactly the minimum sample count is enough to be applicable with progressive shrinkage`() {
        val observations = List(BiasCorrectionCalculator.MIN_SAMPLE_COUNT) { BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 120.0) }

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.Applicabile)
        val applicabile = result as BiasCorrectionResult.Applicabile
        assertEquals(2, applicabile.campione)
        // n=2 < 5: shrinkage verso 1.0 con peso 2/5 = 0.4 -> 1.0 + (1.20 - 1.0) * 0.4 = 1.08
        assertEquals(1.08, applicabile.fattore, 1e-9)
    }

    @Test
    fun `small samples shrink progressively toward 1 and clamp within safety bounds`() {
        val three = List(3) { BiasObservation(100.0, 120.0) }
        val four = List(4) { BiasObservation(100.0, 120.0) }
        assertEquals(1.12, (BiasCorrectionCalculator.calculate(three) as BiasCorrectionResult.Applicabile).fattore, 1e-9)
        assertEquals(1.16, (BiasCorrectionCalculator.calculate(four) as BiasCorrectionResult.Applicabile).fattore, 1e-9)

        val extremeHigh = List(5) { BiasObservation(100.0, 400.0) }
        val extremeLow = List(5) { BiasObservation(100.0, 10.0) }
        assertEquals(BiasCorrectionCalculator.MAX_FACTOR, (BiasCorrectionCalculator.calculate(extremeHigh) as BiasCorrectionResult.Applicabile).fattore, 1e-9)
        assertEquals(BiasCorrectionCalculator.MIN_FACTOR, (BiasCorrectionCalculator.calculate(extremeLow) as BiasCorrectionResult.Applicabile).fattore, 1e-9)
    }

    @Test
    fun `accurate meal within neutral deadband does not contradict consistent bias direction`() {
        // 2 sottostime nette (1.25, 1.30) + 1 pasto quasi perfetto (0.99 dentro deadband 0.95..1.05):
        // prima scartato come BiasInstabile (2/3 = 0.667 < 0.70), ora concordanza = 3/3 = 1.0!
        val observations = listOf(
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 125.0),
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 130.0),
            BiasObservation(pesoStimatoG = 100.0, pesoVeroG = 99.0),
        )

        val result = BiasCorrectionCalculator.calculate(observations)

        assertTrue(result is BiasCorrectionResult.Applicabile)
    }

    @Test
    fun `high dispersion MAD strongly damps small-sample jump toward 1`() {
        // Confronto fra 2 osservazioni stabili (1.30, 1.32 -> mad=0.01) e 2 disperse (1.10, 1.80 -> mad=0.35)
        val stable = listOf(
            BiasObservation(100.0, 130.0),
            BiasObservation(100.0, 132.0),
        )
        val dispersed = listOf(
            BiasObservation(100.0, 110.0),
            BiasObservation(100.0, 180.0),
        )
        val stableFactor = (BiasCorrectionCalculator.calculate(stable) as BiasCorrectionResult.Applicabile).fattore
        val dispersedFactor = (BiasCorrectionCalculator.calculate(dispersed) as BiasCorrectionResult.Applicabile).fattore

        // Su 2 campioni stabili (mediana 1.31): quasi pieno shrinkage 0.4 * ~0.996 -> ~1.123
        assertTrue(stableFactor > 1.12)
        // Su 2 campioni dispersi (mediana 1.45, mad=0.35): stabilityFactor ~0.155 -> salto fortemente smorzato (< 1.04)
        assertTrue(dispersedFactor < 1.04)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive estimated weight is rejected explicitly`() {
        BiasObservation(pesoStimatoG = 0.0, pesoVeroG = 120.0)
    }
}
