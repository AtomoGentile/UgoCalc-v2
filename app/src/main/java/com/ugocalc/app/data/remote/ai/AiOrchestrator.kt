package com.ugocalc.app.data.remote.ai

import com.ugocalc.app.data.remote.ai.dto.MatchingDto
import com.ugocalc.app.data.remote.ai.dto.NormalizationDto
import com.ugocalc.app.data.remote.ai.dto.OnlineFoodResearchDto
import com.ugocalc.app.data.remote.ai.dto.VisionAnalysisDto
import com.ugocalc.app.data.remote.ai.dto.toDomain
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.NormalizationResult
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.VisionAnalysisResult
import com.ugocalc.app.domain.prompts.PromptTemplates
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto unico di accesso ai 3 Passaggi AI della V1 (§3, §4, §5). La parte HTTP-agnostica-dal-prompt
 * (risoluzione client/API key, retry sugli errori transitori, decodifica JSON) è delegata ad
 * [AiCallExecutor], condivisa anche dai moduli IA della V2 (es. M1): qui restano solo il prompt e
 * il DTO specifici di ciascun passaggio.
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val executor: AiCallExecutor,
) {

    suspend fun runVisionAnalysis(model: AiModel, images: List<ByteArray>, imageMimeType: String): VisionAnalysisResult {
        val imageParts = executor.encodeImages(images, imageMimeType)
        return executor.completeJson<VisionAnalysisDto>(model, PromptTemplates.VISION_PROMPT, imageParts).toDomain()
    }

    suspend fun runTableNormalization(model: AiModel, extractedText: String): NormalizationResult =
        executor.completeJson<NormalizationDto>(model, PromptTemplates.normalizationPrompt(extractedText)).toDomain()

    suspend fun runMatching(model: AiModel, listaAlimentiJson: String, tabellaNormalizzataJson: String): MatchingResult =
        executor.completeJson<MatchingDto>(
            model,
            PromptTemplates.matchingPrompt(listaAlimentiJson, tabellaNormalizzataJson),
        ).toDomain()

    /** Ricerca online per un alimento assente dalle tabelle dell'utente (oggi funzionante solo con Gemini, vedi [com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient]). */
    suspend fun runOnlineFoodResearch(model: AiModel, foodName: String): OnlineFoodResearchResult =
        executor.searchOnline<OnlineFoodResearchDto>(model, PromptTemplates.onlineFoodResearchPrompt(foodName)).toDomain()
}
