package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PhysicsEngineTest {

    private val engine = PhysicsEngine()

    private val goodScale = ScaleContext(plateDiameterCm = 27.0, plateDiameterIsDefault = false, quality = Confidence.ALTO)

    private fun food(
        nome: String,
        misure: RawMeasurements = RawMeasurements(),
        occlusione: OcclusionDegree = OcclusionDegree.NESSUNA,
        confidenza: Confidence = Confidence.ALTO,
    ) = PerceivedFood(nome = nome, misureGrezze = misure, occlusione = occlusione, confidenza = confidenza)

    // --- Casi esatti calcolati a mano ---------------------------------------------------------

    @Test
    fun `countable weight is count times unit weight`() {
        val est = engine.estimate(
            food("mele", RawMeasurements(conteggio = 3)),
            EstimationStrategy.CONTABILE,
            goodScale,
        )
        assertEquals(450.0, est.pesoG, 1e-9) // 3 × 150 g
        assertEquals(EstimationStrategy.CONTABILE, est.strategia)
        assertEquals(3.0, est.catenaCalcolo.valori["conteggio"]!!, 1e-9)
        assertEquals(150.0, est.catenaCalcolo.valori["peso_unitario_g"]!!, 1e-9)
    }

    @Test
    fun `spread weight follows the plate-fraction formula`() {
        val est = engine.estimate(
            food("riso", RawMeasurements(frazionePiatto = 0.4, altezzaCm = 1.5, riempimento = 0.8)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            goodScale,
        )
        val area = PI * 13.5 * 13.5
        val atteso = 0.4 * area * 1.5 * 0.8 * 0.85 // densità riso cotto = 0.85
        assertEquals(atteso, est.pesoG, 1e-6)
    }

    @Test
    fun `container weight is capacity times fill times density`() {
        val est = engine.estimate(
            food("yogurt", RawMeasurements(contenitore = "ciotola", frazioneRiempimento = 0.6)),
            EstimationStrategy.A_CONTENITORE,
            goodScale,
        )
        assertEquals(350.0 * 0.6 * 1.03, est.pesoG, 1e-6) // ciotola 350 ml, yogurt 1.03
    }

    @Test
    fun `block parallelepiped weight is volume times density`() {
        val est = engine.estimate(
            food("formaggio", RawMeasurements(lunghezzaCm = 5.0, larghezzaCm = 4.0, altezzaCm = 2.0)),
            EstimationStrategy.BLOCCO_MISURABILE,
            goodScale,
        )
        assertEquals(40.0 * 1.05, est.pesoG, 1e-6) // 40 cm³ × densità formaggio 1.05
    }

    @Test
    fun `block cylinder uses circular base area`() {
        val est = engine.estimate(
            food("torta", RawMeasurements(forma = "cilindro", diametroCm = 8.0, altezzaCm = 3.0)),
            EstimationStrategy.BLOCCO_MISURABILE,
            goodScale,
        )
        val volume = PI * 4.0 * 4.0 * 3.0
        assertEquals(volume * 0.45, est.pesoG, 1e-6) // densità torta 0.45
    }

    @Test
    fun `negligible strategy yields a small fixed weight with low confidence`() {
        val est = engine.estimate(food("olio"), EstimationStrategy.TRASCURABILE, goodScale)
        assertEquals(5.0, est.pesoG, 1e-9)
        assertEquals(Confidence.BASSO, est.confidenza)
    }

    // --- Proprietà -----------------------------------------------------------------------------

    @Test
    fun `the confidence interval always brackets the point estimate`() {
        val est = engine.estimate(
            food("riso", RawMeasurements(frazionePiatto = 0.4, altezzaCm = 1.5, riempimento = 0.8)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            goodScale,
        )
        assertTrue(est.intervallo.minG <= est.pesoG)
        assertTrue(est.pesoG <= est.intervallo.maxG)
        assertTrue(est.intervallo.minG >= 0.0)
    }

    @Test
    fun `heavier occlusion widens the relative interval`() {
        val misure = RawMeasurements(frazionePiatto = 0.4, altezzaCm = 1.5, riempimento = 0.8)
        val nessuna = engine.estimate(
            food("riso", misure, occlusione = OcclusionDegree.NESSUNA),
            EstimationStrategy.DISTESA_SUL_PIATTO, goodScale,
        )
        val elevata = engine.estimate(
            food("riso", misure, occlusione = OcclusionDegree.ELEVATA),
            EstimationStrategy.DISTESA_SUL_PIATTO, goodScale,
        )
        assertEquals(nessuna.pesoG, elevata.pesoG, 1e-9) // l'occlusione non cambia il punto, solo l'incertezza
        assertTrue(elevata.intervallo.ampiezzaRelativa > nessuna.intervallo.ampiezzaRelativa)
    }

    @Test
    fun `falling back to a default density widens the relative interval`() {
        val misure = RawMeasurements(lunghezzaCm = 5.0, larghezzaCm = 4.0, altezzaCm = 2.0)
        val noto = engine.estimate(food("formaggio", misure), EstimationStrategy.BLOCCO_MISURABILE, goodScale)
        val ignoto = engine.estimate(food("sostanza aliena", misure), EstimationStrategy.BLOCCO_MISURABILE, goodScale)
        assertTrue(ignoto.intervallo.ampiezzaRelativa > noto.intervallo.ampiezzaRelativa)
    }

    @Test
    fun `a clean countable estimate is high confidence, a fallback one is not`() {
        val pulito = engine.estimate(food("mele", RawMeasurements(conteggio = 3)), EstimationStrategy.CONTABILE, goodScale)
        assertEquals(Confidence.ALTO, pulito.confidenza)

        val senzaConteggio = engine.estimate(food("mele", RawMeasurements()), EstimationStrategy.CONTABILE, goodScale)
        assertTrue(senzaConteggio.confidenza != Confidence.ALTO) // conteggio mancante → incertezza maggiore
    }
}
