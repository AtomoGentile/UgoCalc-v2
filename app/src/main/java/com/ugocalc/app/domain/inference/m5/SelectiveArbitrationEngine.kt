package com.ugocalc.app.domain.inference.m5

import com.ugocalc.app.data.remote.ai.AiCallExecutor
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.domain.inference.model.ArbitrationResponse
import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.RevisedFood
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.model.AiModel
import javax.inject.Inject

/**
 * M5 – Arbitrato selettivo (piano V2 §4, §5). Si attiva SOLO quando M4 ha flaggato almeno un
 * alimento: un'unica chiamata al modello con le foto originali e il solo elenco dei dubbi, per una
 * rianalisi mirata. È la terza chiamata, eventuale, del budget di §3.
 *
 * Invocato al massimo una volta per analisi (§5 M5): il chiamante ([com.ugocalc.app.domain.inference.V2AnalysisPipeline])
 * non richiama mai M5 una seconda volta sullo stesso flag, nemmeno se la revisione lascia
 * l'ambiguità irrisolta — si applica invece la regola di sicurezza di §5 a valle.
 */
class SelectiveArbitrationEngine @Inject constructor(
    private val executor: AiCallExecutor,
) {

    suspend fun arbitrate(
        model: AiModel,
        images: List<ByteArray>,
        imageMimeType: String,
        scene: SceneGraph,
        flaggati: List<FlaggedItem>,
    ): ArbitrationOutcome {
        if (flaggati.isEmpty()) return ArbitrationOutcome.Riuscita(emptyList())

        val imageParts = executor.encodeImages(images, imageMimeType)
        val prompt = ArbitrationPrompt.build(scene, flaggati)
        return try {
            val response = executor.completeJson<ArbitrationResponse>(model, prompt, imageParts)
            if (response.schemaVersion != SceneGraph.SCHEMA_VERSION) {
                ArbitrationOutcome.NonDisponibile(
                    "schema_version non riconosciuta nella risposta di arbitrato: ${response.schemaVersion}",
                )
            } else {
                ArbitrationOutcome.Riuscita(response.revisioni)
            }
        } catch (e: AiCallException) {
            ArbitrationOutcome.NonDisponibile(e.message ?: "errore sconosciuto nella chiamata di arbitrato")
        }
    }
}

/**
 * Esito di M5. [NonDisponibile] non è un errore silenzioso: se l'arbitrato fallisce, la pipeline
 * prosegue con le stime e il report di M4 originali, dichiarando esplicitamente che l'arbitrato non
 * è stato possibile, mai spacciando la stima pre-arbitrato per una stima già rivista.
 */
sealed interface ArbitrationOutcome {
    data class Riuscita(val revisioni: List<RevisedFood>) : ArbitrationOutcome
    data class NonDisponibile(val motivo: String) : ArbitrationOutcome
}
