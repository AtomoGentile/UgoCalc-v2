package com.ugocalc.app.domain.inference

import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.ScaleCalibrationRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.inference.m1.UnifiedPerceptionEngine
import com.ugocalc.app.domain.inference.m5.SelectiveArbitrationEngine
import com.ugocalc.app.domain.inference.m6.NutritionMatchStepAdapter
import kotlinx.coroutines.flow.first
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
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val scaleCalibrationRepository: ScaleCalibrationRepository,
    private val settingsRepository: SettingsRepository,
    private val tableRepository: TableRepository,
    private val recipeRepository: com.ugocalc.app.data.repository.RecipeRepository
) {
    /**
     * [sessionId] collega il record M8 salvato da questa esecuzione al pasto tracciato per il
     * benchmark V1/V2 (§10, §13 punto 5), se il chiamante ne ha già generato uno; `null` se la
     * pipeline gira fuori da una sessione tracciata (es. test, chiamate dirette).
     */
    fun create(sessionId: String? = null): V2AnalysisPipeline = V2AnalysisPipeline(
        perceive = perceptionEngine::perceive,
        arbitrate = arbitrationEngine::arbitrate,
        matchNutrition = nutritionMatchStepAdapter::match,
        recordTelemetry = { outcome, model, sessionMode -> telemetryRepository.record(outcome, model, sessionMode, sessionId) },
        biasFactor = { sessionMode, strategy, aiModelId, foodCategory ->
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            biasCorrectionRepository.currentFactor(sessionMode, strategy, aiModelId, foodCategory, consentGiven)
        },
        getCalibrations = { scaleCalibrationRepository.observeAll().first() },
        resolveAmbiguity = { candidati ->
            // Ricerca locale, deterministica (§13 punto 2): nessuna chiamata al modello. Per ogni
            // candidato prende il CHO/100g della prima voce trovata nelle tabelle dell'utente.
            candidati.mapNotNull { nome ->
                tableRepository.searchEntries(nome).firstOrNull()?.let { nome to it.entry.choPer100g }
            }.toMap()
        },
        recipeRepository = recipeRepository
    )
}
