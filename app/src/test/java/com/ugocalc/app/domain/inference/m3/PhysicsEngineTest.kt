package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
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
        // riso ha densità BULK (CREA): riempimento non si applica (già incluso nella densità apparente)
        val atteso = 0.4 * area * 1.5 * 0.82
        assertEquals(atteso, est.pesoG, 1e-6)
    }

    @Test
    fun `conical plate volume follows truncated cone formula`() {
        val conicScale = ScaleContext(
            plateDiameterCm = 20.5,
            plateDiameterIsDefault = false,
            quality = Confidence.ALTO,
            plateShapeType = "conico",
            plateBottomDiameterCm = 10.0
        )
        val est = engine.estimate(
            food("pasta maccheroni", RawMeasurements(frazionePiatto = 1.0, altezzaCm = 3.5, riempimento = 0.85)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            conicScale,
        )
        // Tronco di cono: V = (h/3) × π × (R² + R×r + r²)
        // R = 10.25 (top), r = 5.0 (bottom), h = 3.5
        val R = 10.25
        val r = 5.0
        val h = 3.5
        val volumeTronco = (h / 3.0) * PI * (R * R + R * r + r * r)
        val atteso = volumeTronco * 0.46 // densità bulk pasta bucata
        assertEquals(atteso, est.pesoG, 1e-6)
    }

    @Test
    fun `conical plate volume scales with frazionePiatto`() {
        val conicScale = ScaleContext(
            plateDiameterCm = 20.5,
            plateDiameterIsDefault = false,
            quality = Confidence.ALTO,
            plateShapeType = "conico",
            plateBottomDiameterCm = 10.0,
        )
        val full = engine.estimate(
            food("pasta maccheroni", RawMeasurements(frazionePiatto = 1.0, altezzaCm = 3.5)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            conicScale,
        )
        val half = engine.estimate(
            food("pasta maccheroni", RawMeasurements(frazionePiatto = 0.5, altezzaCm = 3.5)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            conicScale,
        )
        assertEquals(full.pesoG * 0.5, half.pesoG, 1e-6)
    }

    @Test
    fun `conical plate interpolates effective top radius when plateHeightCm is known`() {
        val conicScaleWithHeight = ScaleContext(
            plateDiameterCm = 22.0, // R = 11.0
            plateDiameterIsDefault = false,
            quality = Confidence.ALTO,
            plateShapeType = "conico",
            plateBottomDiameterCm = 10.0, // r = 5.0
            plateHeightCm = 6.0,
        )
        val h = 3.0 // metà altezza -> rTopEff = 5.0 + (11.0 - 5.0) * 0.5 = 8.0
        val frazione = 0.8
        val est = engine.estimate(
            food("riso", RawMeasurements(frazionePiatto = frazione, altezzaCm = h)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            conicScaleWithHeight,
        )
        val rTopEff = 8.0
        val rBottom = 5.0
        val volume = frazione * (h / 3.0) * PI * (rTopEff * rTopEff + rTopEff * rBottom + rBottom * rBottom)
        assertEquals(volume * 0.82, est.pesoG, 1e-6)
    }

    @Test
    fun `spread applies mound profile factor for piatto, cupola, cono and keeps null backwards-compatible`() {
        val base = RawMeasurements(frazionePiatto = 0.4, altezzaCm = 2.0)
        val nullProf = engine.estimate(food("riso", base), EstimationStrategy.DISTESA_SUL_PIATTO, goodScale)
        val piatto = engine.estimate(food("riso", base.copy(profiloCumulo = "piatto")), EstimationStrategy.DISTESA_SUL_PIATTO, goodScale)
        val cupola = engine.estimate(food("riso", base.copy(profiloCumulo = "cupola")), EstimationStrategy.DISTESA_SUL_PIATTO, goodScale)
        val cono = engine.estimate(food("riso", base.copy(profiloCumulo = "cono")), EstimationStrategy.DISTESA_SUL_PIATTO, goodScale)

        assertEquals(nullProf.pesoG, piatto.pesoG, 1e-6)
        assertEquals(nullProf.pesoG * 0.65, cupola.pesoG, 1e-6)
        assertEquals(nullProf.pesoG * 0.45, cono.pesoG, 1e-6)
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
    fun `container uses capacitaContenitoreMl or calibrated capacity before falling back to PhysicalData`() {
        val fromM1 = engine.estimate(
            food("latte", RawMeasurements(contenitore = "bicchiere", frazioneRiempimento = 0.5, capacitaContenitoreMl = 330.0)),
            EstimationStrategy.A_CONTENITORE,
            goodScale,
        )
        assertEquals(330.0 * 0.5 * 1.03, fromM1.pesoG, 1e-6)

        val scaleWithAnchorCap = goodScale.copy(calibratedCapacityMl = 420.0)
        val fromScaleAnchor = engine.estimate(
            food("latte", RawMeasurements(contenitore = "tazza", frazioneRiempimento = 0.5)),
            EstimationStrategy.A_CONTENITORE,
            scaleWithAnchorCap,
        )
        assertEquals(420.0 * 0.5 * 1.03, fromScaleAnchor.pesoG, 1e-6)

        val scaleWithNamedMap = goodScale.copy(calibratedCapacitiesByName = mapOf("tazza blu" to 290.0))
        val fromNamedMap = engine.estimate(
            food("latte", RawMeasurements(contenitore = "Tazza blu", frazioneRiempimento = 0.5)),
            EstimationStrategy.A_CONTENITORE,
            scaleWithNamedMap,
        )
        assertEquals(290.0 * 0.5 * 1.03, fromNamedMap.pesoG, 1e-6)
    }

    @Test
    fun `block parallelepiped weight is volume times density`() {
        val est = engine.estimate(
            food("formaggio", RawMeasurements(lunghezzaCm = 5.0, larghezzaCm = 4.0, altezzaCm = 2.0)),
            EstimationStrategy.BLOCCO_MISURABILE,
            goodScale,
        )
        assertEquals(40.0 * 1.06, est.pesoG, 1e-6) // 40 cm³ × densità formaggio 1.06
    }

    @Test
    fun `block cylinder uses circular base area`() {
        val est = engine.estimate(
            food("torta", RawMeasurements(forma = "cilindro", diametroCm = 8.0, altezzaCm = 3.0)),
            EstimationStrategy.BLOCCO_MISURABILE,
            goodScale,
        )
        val volume = PI * 4.0 * 4.0 * 3.0
        assertEquals(volume * 0.48, est.pesoG, 1e-6) // densità torta 0.48
    }

    @Test
    fun `negligible strategy yields a small fixed weight with low confidence`() {
        val est = engine.estimate(food("olio"), EstimationStrategy.TRASCURABILE, goodScale)
        assertEquals(5.0, est.pesoG, 1e-9)
        assertEquals(Confidence.BASSO, Confidence.fromScore(est.confidenza))
    }

    // --- Proprietà -----------------------------------------------------------------------------

    @Test
    fun `the confidence interval always brackets the point estimate`() {
        val est = engine.estimate(
            food("riso", RawMeasurements(frazionePiatto = 0.4, altezzaCm = 1.5, riempimento = 0.8)),
            EstimationStrategy.DISTESA_SUL_PIATTO,
            goodScale,
        )
        assertTrue(est.intervallo.minG <= est.pesoCrudoG)
        assertTrue(est.pesoCrudoG <= est.intervallo.maxG)
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
        assertTrue(pulito.confidenza.percent >= 85) // ALTO è 85-100%

        val senzaConteggio = engine.estimate(food("mele", RawMeasurements()), EstimationStrategy.CONTABILE, goodScale)
        assertTrue(senzaConteggio.confidenza.percent < pulito.confidenza.percent) // conteggio mancante → confidenza minore
    }
}
