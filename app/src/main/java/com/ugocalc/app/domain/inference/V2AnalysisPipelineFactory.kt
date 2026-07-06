package com.ugocalc.app.domain.inference

import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.m1.UnifiedPerceptionEngine
import com.ugocalc.app.domain.inference.m5.SelectiveArbitrationEngine
import com.ugocalc.app.domain.inference.m6.NutritionMatchStepAdapter
import javax.inject.Inject

/**
 * Assembla la pipeline V2 con le implementazioni reali di M1/M5/M6/M8 (§4): il punto in cui i
 * moduli IA, il motore nutrizionale esistente (guida migrazione §3) e l'archivio di telemetria
 * (§5 M8) vengono legati ai passi deterministici di [V2AnalysisPipeline] (M0/M2/M3/M4, già di
 * default nel costruttore). Puro cablaggio, senza logica propria: la copertura di test è sui
 * singoli moduli e sulla pipeline con i fake ([V2AnalysisPipelineTest]), non su questa factory.
 */
class V2AnalysisPipelineFactory @Inject constructor(
    private val perceptionEngine: UnifiedPerceptionEngine,
    private val arbitrationEngine: SelectiveArbitrationEngine,
    private val nutritionMatchStepAdapter: NutritionMatchStepAdapter,
    private val telemetryRepository: V2TelemetryRepository,
) {
    fun create(): V2AnalysisPipeline = V2AnalysisPipeline(
        perceive = perceptionEngine::perceive,
        arbitrate = arbitrationEngine::arbitrate,
        matchNutrition = nutritionMatchStepAdapter::match,
        recordTelemetry = telemetryRepository::record,
    )
}
