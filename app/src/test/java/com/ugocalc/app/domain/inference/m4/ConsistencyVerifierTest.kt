package com.ugocalc.app.domain.inference.m4

import com.ugocalc.app.domain.inference.m3.ScaleContext
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.FlagType
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyVerifierTest {

    private val verifier = ConsistencyVerifier()
    private val scale = ScaleContext(plateDiameterCm = 27.0, plateDiameterIsDefault = false, quality = Confidence.ALTO)

    @Test
    fun `a plausible scene passes every constraint and flags nothing`() {
        val scene = SceneGraph(
            schemaVersion = SceneGraph.SCHEMA_VERSION,
            ancore = emptyList(),
            alimenti = listOf(
                food("riso", RawMeasurements(frazionePiatto = 0.4)),
                food("pollo"),
            ),
        )
        val estimates = listOf(
            estimate("riso", EstimationStrategy.DISTESA_SUL_PIATTO, 200.0),
            estimate("pollo", EstimationStrategy.BLOCCO_MISURABILE, 150.0),
        )
        val report = verifier.verify(scene, estimates, scale)

        assertTrue(report.controlli.all { it.superato })
        assertTrue(report.flaggati.isEmpty())
        assertFalse(report.richiedeArbitrato)
    }

    @Test
    fun `a weight outside the typical range is flagged as a physical violation`() {
        val scene = SceneGraph(schemaVersion = SceneGraph.SCHEMA_VERSION, ancore = emptyList(), alimenti = listOf(food("pollo")))
        val estimates = listOf(estimate("pollo", EstimationStrategy.BLOCCO_MISURABILE, 2500.0))
        val report = verifier.verify(scene, estimates, scale)

        assertTrue(report.richiedeArbitrato)
        val flag = report.flaggati.single { it.nomeAlimento == "pollo" }
        assertEquals(FlagType.VINCOLO_FISICO, flag.tipo)
        assertFalse(report.controlli.single { it.nome == "peso entro intervallo tipico" }.superato)
    }

    @Test
    fun `an implausible plate areal density is flagged`() {
        // 100 g su appena l'1% del piatto → ~17 g/cm², ben oltre il massimo plausibile.
        val scene = SceneGraph(schemaVersion = SceneGraph.SCHEMA_VERSION, ancore = emptyList(), alimenti = listOf(food("riso", RawMeasurements(frazionePiatto = 0.01))))
        val estimates = listOf(estimate("riso", EstimationStrategy.DISTESA_SUL_PIATTO, 100.0))
        val report = verifier.verify(scene, estimates, scale)

        val flag = report.flaggati.single { it.nomeAlimento == "riso" }
        assertEquals(FlagType.VINCOLO_FISICO, flag.tipo)
    }

    @Test
    fun `plate coverage over 100 percent flags a scene inconsistency on the largest item`() {
        val scene = SceneGraph(
            schemaVersion = SceneGraph.SCHEMA_VERSION,
            ancore = emptyList(),
            alimenti = listOf(
                food("insalata", RawMeasurements(frazionePiatto = 0.8)),
                food("riso", RawMeasurements(frazionePiatto = 0.7)),
            ),
        )
        val estimates = listOf(
            estimate("insalata", EstimationStrategy.DISTESA_SUL_PIATTO, 120.0),
            estimate("riso", EstimationStrategy.DISTESA_SUL_PIATTO, 200.0),
        )
        val report = verifier.verify(scene, estimates, scale)

        assertFalse(report.controlli.single { it.nome == "copertura totale del piatto" }.superato)
        val flag = report.flaggati.single { it.tipo == FlagType.VINCOLO_SCENA }
        assertEquals("insalata", flag.nomeAlimento) // il contributore maggiore (0.8)
    }

    @Test
    fun `alternatives with non-high confidence are flagged as identity-ambiguous, high confidence is not`() {
        val scene = SceneGraph(
            schemaVersion = SceneGraph.SCHEMA_VERSION,
            ancore = emptyList(),
            alimenti = listOf(
                food("pane", alternative = listOf("focaccia"), confidenza = Confidence.MEDIO),
                food("mela", alternative = listOf("pesca"), confidenza = Confidence.ALTO),
            ),
        )
        val estimates = listOf(
            estimate("pane", EstimationStrategy.BLOCCO_MISURABILE, 50.0),
            estimate("mela", EstimationStrategy.CONTABILE, 150.0),
        )
        val report = verifier.verify(scene, estimates, scale)

        val ambigui = report.flaggati.filter { it.tipo == FlagType.AMBIGUITA_IDENTITA }
        assertEquals(1, ambigui.size)
        assertEquals("pane", ambigui.single().nomeAlimento)
    }

    // --- helper ------------------------------------------------------------------------------

    private fun food(
        nome: String,
        misure: RawMeasurements = RawMeasurements(),
        alternative: List<String> = emptyList(),
        confidenza: Confidence = Confidence.ALTO,
    ) = PerceivedFood(
        nome = nome,
        alternativeIdentificazione = alternative,
        misureGrezze = misure,
        confidenza = confidenza,
    )

    private fun estimate(nome: String, strategia: EstimationStrategy, pesoG: Double) = WeightEstimate(
        nome = nome,
        strategia = strategia,
        pesoG = pesoG,
        pesoCrudoG = pesoG,
        intervallo = ConfidenceInterval(minG = pesoG * 0.8, maxG = pesoG * 1.2),
        catenaCalcolo = CalculationChain(formula = "", valori = emptyMap(), fonti = emptyList(), sintesi = ""),
        confidenza = ConfidenceScore(65),
    )
}
