package com.ugocalc.app.domain.usecase

import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.ModelVisionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Passaggio 1 (§3): esegue la Visione in parallelo per ciascun modello selezionato dall'utente
 * (§2.4 punto 3). Ogni chiamata è isolata: se un modello fallisce, gli altri proseguono e il
 * risultato per quel modello diventa `ModelVisionOutcome.Failure` (§3 "Gestione del fallimento",
 * §2.4 punto 4 "Se un modello fallisce... senza bloccare la visualizzazione degli altri modelli").
 */
class RunVisionForModelsUseCase @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
) {
    suspend operator fun invoke(
        models: List<AiModel>,
        images: List<ByteArray>,
        imageMimeType: String,
        sessionId: String? = null,
        photoTelemetryBlock: String? = null,
        userPromptNote: String? = null,
    ): List<ModelVisionOutcome> = coroutineScope {
        models.map { model ->
            async {
                try {
                    val result = aiOrchestrator.runVisionAnalysis(
                        model = model,
                        images = images,
                        imageMimeType = imageMimeType,
                        sessionId = sessionId,
                        photoTelemetryBlock = photoTelemetryBlock,
                        userPromptNote = userPromptNote,
                    )
                    ModelVisionOutcome.Success(model, result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiCallException) {
                    ModelVisionOutcome.Failure(model, e.message?.takeIf { it.isNotBlank() } ?: "Analisi non riuscita")
                } catch (e: Exception) {
                    val detail = e.message?.takeIf { it.isNotBlank() }
                    ModelVisionOutcome.Failure(
                        model,
                        if (detail != null) "Analisi non riuscita: $detail" else "Analisi non riuscita",
                    )
                }
            }
        }.map { it.await() }
    }
}
