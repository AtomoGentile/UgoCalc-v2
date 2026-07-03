package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyRouterTest {

    private val router = StrategyRouter()

    @Test
    fun `known foods route to their expected strategy with an explicit match`() {
        assertStrategy("spaghetti", EstimationStrategy.DISTESA_SUL_PIATTO)
        assertStrategy("bistecca", EstimationStrategy.BLOCCO_MISURABILE)
        assertStrategy("mela", EstimationStrategy.CONTABILE)
        assertStrategy("minestrone", EstimationStrategy.A_CONTENITORE)
        assertStrategy("lasagne", EstimationStrategy.COMPOSTO_OCCLUSO)
        assertStrategy("olio", EstimationStrategy.TRASCURABILE)
    }

    @Test
    fun `matching ignores case and accents`() {
        assertStrategy("PURÈ", EstimationStrategy.DISTESA_SUL_PIATTO)
        assertStrategy("Caffè", EstimationStrategy.A_CONTENITORE)
        assertStrategy("  Riso  ", EstimationStrategy.DISTESA_SUL_PIATTO)
    }

    @Test
    fun `an unknown food falls back to the default strategy and is not an explicit match`() {
        val result = router.route("pietanza aliena non catalogata")
        assertEquals(StrategyRules.FALLBACK, result.strategia)
        assertFalse(result.abbinamentoEsplicito)
    }

    @Test
    fun `whole-word matching does not let 'mela' capture 'melanzane'`() {
        // "melanzane" contiene "mela" come sottostringa: senza confine di parola sarebbe CONTABILE.
        assertStrategy("melanzane", EstimationStrategy.DISTESA_SUL_PIATTO)
    }

    @Test
    fun `a composite dish wins over a component keyword thanks to rule ordering`() {
        // "pasta al pesto": la regola COMPOSTO (pesto) precede DISTESA (pasta).
        assertStrategy("pasta al pesto", EstimationStrategy.COMPOSTO_OCCLUSO)
    }

    @Test
    fun `routing a scene preserves order and count`() {
        val scene = SceneGraph(
            ancore = emptyList(),
            alimenti = listOf(food("riso"), food("pollo"), food("mela")),
        )
        val assignments = router.route(scene)
        assertEquals(3, assignments.size)
        assertEquals(EstimationStrategy.DISTESA_SUL_PIATTO, assignments[0].strategia)
        assertEquals(EstimationStrategy.BLOCCO_MISURABILE, assignments[1].strategia)
        assertEquals(EstimationStrategy.CONTABILE, assignments[2].strategia)
    }

    private fun assertStrategy(nome: String, atteso: EstimationStrategy) {
        val result = router.route(nome)
        assertEquals("strategia per '$nome'", atteso, result.strategia)
        assertTrue("'$nome' dovrebbe essere un abbinamento esplicito", result.abbinamentoEsplicito)
    }

    private fun food(nome: String) =
        PerceivedFood(nome = nome, misureGrezze = RawMeasurements(), confidenza = Confidence.ALTO)
}
