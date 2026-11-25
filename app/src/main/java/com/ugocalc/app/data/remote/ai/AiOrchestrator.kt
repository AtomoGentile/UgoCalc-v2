package com.ugocalc.app.data.remote.ai

import com.ugocalc.app.data.remote.ai.dto.MatchingDto
import com.ugocalc.app.data.remote.ai.dto.NormalizationDto
import com.ugocalc.app.data.remote.ai.dto.OnlineFoodResearchDto
import com.ugocalc.app.data.remote.ai.dto.VisionAnalysisDto
import com.ugocalc.app.data.remote.ai.dto.toDomain
import com.ugocalc.app.data.repository.CostManagementRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.ModelPricingCatalog
import com.ugocalc.app.domain.model.NormalizationResult
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.VisionAnalysisResult
import com.ugocalc.app.domain.prompts.PromptTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto unico di accesso ai 3 Passaggi AI della V1 (§3, §4, §5). La parte HTTP-agnostica-dal-prompt
 * (risoluzione client/API key, retry sugli errori transitori, decodifica JSON) è delegata ad
 * [AiCallExecutor], condivisa anche dai moduli IA della V2 (es. M1): qui restano solo il prompt e
 * il DTO specifici di ciascun passaggio.
 *
 * Registra i costi di ogni chiamata per la dashboard (§13 punto 5).
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val executor: AiCallExecutor,
    private val costManagementRepository: CostManagementRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun runVisionAnalysis(
        model: AiModel,
        images: List<ByteArray>,
        imageMimeType: String,
        sessionId: String? = null,
        photoTelemetryBlock: String? = null,
        userPromptNote: String? = null,
    ): VisionAnalysisResult {
        val prompt = PromptTemplates.visionPrompt(photoTelemetryBlock, userPromptNote)
        val imageParts = executor.encodeImages(images, imageMimeType)
        val responseDto = executor.completeJson<VisionAnalysisDto>(model, prompt, imageParts)

        scope.launch {
            recordCost(model, prompt, executor.json.encodeToString(responseDto), images.size, "vision", "Vision API (V1) - ${images.size} foto", sessionId)
        }

        return responseDto.toDomain()
    }

    suspend fun runTableNormalization(
        model: AiModel,
        extractedText: String,
        sessionId: String? = null
    ): NormalizationResult {
        val prompt = PromptTemplates.normalizationPrompt(extractedText)
        val responseDto = executor.completeJson<NormalizationDto>(model, prompt)

        scope.launch {
            recordCost(model, prompt, executor.json.encodeToString(responseDto), 0, "normalization", "Normalizzazione Tabella", sessionId)
        }

        return responseDto.toDomain()
    }

    suspend fun runMatching(
        model: AiModel,
        listaAlimentiJson: String,
        tabellaNormalizzataJson: String,
        sessionId: String? = null
    ): MatchingResult {
        val prompt = PromptTemplates.matchingPrompt(listaAlimentiJson, tabellaNormalizzataJson)
        val responseDto = executor.completeJson<MatchingDto>(model, prompt)

        scope.launch {
            recordCost(model, prompt, executor.json.encodeToString<MatchingDto>(responseDto), 0, "matching", "Matching (M6/V1)", sessionId)
        }

        return responseDto.toDomain()
    }

    /** Ricerca online per un alimento assente dalle tabelle dell'utente (oggi funzionante solo con Gemini, vedi [com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient]). */
    suspend fun runOnlineFoodResearch(
        model: AiModel,
        foodName: String,
        sessionId: String? = null
    ): OnlineFoodResearchResult {
        val prompt = PromptTemplates.onlineFoodResearchPrompt(foodName)
        val responseDto = executor.searchOnline<OnlineFoodResearchDto>(model, prompt)

        scope.launch {
            recordCost(model, prompt, executor.json.encodeToString(responseDto), 0, "online_research", "Ricerca Online: $foodName", sessionId)
        }

        return responseDto.toDomain()
    }

    private suspend fun recordCost(
        model: AiModel,
        prompt: String,
        responseJson: String,
        imageCount: Int,
        stage: String,
        description: String,
        sessionId: String?
    ) {
        try {
            val costUsd = ModelPricingCatalog.estimateCost(model, prompt, responseJson, imageCount)
            costManagementRepository.recordCost(
                stage = stage,
                modelId = model.id,
                costUsd = costUsd,
                description = description,
                sessionId = sessionId
            )
        } catch (e: Exception) {
            // Log silente per non interrompere il flusso principale
        }
    }
}
