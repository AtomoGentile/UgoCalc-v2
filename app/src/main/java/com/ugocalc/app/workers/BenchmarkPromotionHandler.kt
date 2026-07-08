package com.ugocalc.app.workers

import com.ugocalc.app.data.repository.SettingsRepository

/**
 * Handler separato per la logica di promozione V2 in modo che possa essere testato facilmente.
 */
class BenchmarkPromotionHandler(
    private val evaluator: PromotionEvaluator,
    private val settingsSetter: V2VisibleModeSetter,
) {
    /**
     * Esegue la valutazione e, se opportuno, abilita la modalita' visibile V2.
     * Restituisce true se la promozione e' stata applicata.
     */
    suspend fun handlePromotion(minMeals: Int = 30): Boolean {
        val decision = evaluator.evaluatePromotion(minMeals)
        // Logging minimo per ambiente non-Android (testable)
        println("BenchmarkPromotionHandler: V1 choWithin10=${decision.v1.choWithin10gPercent} V2 choWithin10=${decision.v2.choWithin10gPercent} promoteV2=${decision.promoteV2}")
        if (decision.promoteV2) {
            settingsSetter.setV2VisibleModeEnabled(true)
            return true
        }
        return false
    }
}

interface PromotionEvaluator {
    suspend fun evaluatePromotion(minMeals: Int = 30): com.ugocalc.app.data.repository.PromotionDecision
}

interface V2VisibleModeSetter {
    suspend fun setV2VisibleModeEnabled(enabled: Boolean)
}

class BenchmarkRepositoryEvaluator(private val benchmarkRepository: com.ugocalc.app.data.repository.BenchmarkRepository) : PromotionEvaluator {
    override suspend fun evaluatePromotion(minMeals: Int): com.ugocalc.app.data.repository.PromotionDecision =
        benchmarkRepository.evaluatePromotion(minMeals)
}

class SettingsRepositoryAdapter(private val settingsRepository: com.ugocalc.app.data.repository.SettingsRepository) : V2VisibleModeSetter {
    override suspend fun setV2VisibleModeEnabled(enabled: Boolean) {
        settingsRepository.setV2VisibleModeEnabled(enabled)
    }
}
