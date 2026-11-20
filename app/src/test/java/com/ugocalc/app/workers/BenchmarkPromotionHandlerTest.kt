package com.ugocalc.app.workers

import com.ugocalc.app.data.repository.EngineStats
import com.ugocalc.app.data.repository.PromotionDecision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class FakeEvaluator(private val decision: PromotionDecision) : PromotionEvaluator {
    var lastRequestedMinMeals: Int? = null
    override suspend fun evaluatePromotion(minMeals: Int): PromotionDecision {
        lastRequestedMinMeals = minMeals
        return decision
    }
}

private class FakeSettingsSetter : V2VisibleModeSetter {
    var lastSet: Boolean? = null
    override suspend fun setV2VisibleModeEnabled(enabled: Boolean) {
        lastSet = enabled
    }
}

class BenchmarkPromotionHandlerTest {

    private fun engineStats(percent: Double?) = EngineStats(weightMaeG = null, weightSampleCount = 0, choWithin10gPercent = percent, choWithin20gPercent = null, choMealCount = 0)

    @Test
    fun handlePromotion_promotes_when_decision_promoteV2_true() = runBlocking {
        val decision = PromotionDecision(promoteV2 = true, mealCount = 5, recommendedMinMeals = 5, v1 = engineStats(50.0), v2 = engineStats(60.0))
        val evaluator = FakeEvaluator(decision)

        val settings = FakeSettingsSetter()
        val handler = BenchmarkPromotionHandler(evaluator, settings)
        val promoted = handler.handlePromotion()
        assertTrue(promoted)
        assertEquals(5, evaluator.lastRequestedMinMeals)
        assertEquals(true, settings.lastSet)
    }

    @Test
    fun handlePromotion_does_not_promote_when_decision_promoteV2_false() = runBlocking {
        val decision = PromotionDecision(promoteV2 = false, mealCount = 5, recommendedMinMeals = 5, v1 = engineStats(60.0), v2 = engineStats(50.0))
        val evaluator = FakeEvaluator(decision)

        val settings = FakeSettingsSetter()
        val handler = BenchmarkPromotionHandler(evaluator, settings)
        val promoted = handler.handlePromotion()
        assertFalse(promoted)
        assertEquals(5, evaluator.lastRequestedMinMeals)
        assertNull(settings.lastSet)
    }
}