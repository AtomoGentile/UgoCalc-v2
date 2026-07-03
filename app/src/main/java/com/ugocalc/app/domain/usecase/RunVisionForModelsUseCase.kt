package com.ugocalc.app.domain.usecase

import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.ModelVisionOutcome
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
    ): List<ModelVisionOutcome> = coroutineScope {
        models.map { model ->
            async {
                try {
                    val result = aiOrchestrator.runVisionAnalysis(model, images, imageMimeType)
                    ModelVisionOutcome.Success(model, result)
                } catch (e: AiCallException) {
                    ModelVisionOutcome.Failure(model, e.message ?: "Analisi non riuscita")
                } catch (e: Exception) {
                    ModelVisionOutcome.Failure(model, "Analisi non riuscita")
                }
            }
        }.map { it.await() }
    }
}
